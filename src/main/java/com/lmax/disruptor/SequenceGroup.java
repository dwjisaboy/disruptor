/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.lmax.disruptor.util.Util;

/**
 * {@link Sequence} group that can dynamically have {@link Sequence}s added and removed while being
 * thread safe.
 * <p>
 * The {@link SequenceGroup#get()} and {@link SequenceGroup#set(long)} methods are lock free and can be
 * concurrently be called with the {@link SequenceGroup#add(Sequence)} and {@link SequenceGroup#remove(Sequence)}.
 */
public final class SequenceGroup extends Sequence
{
    private static final AtomicReferenceFieldUpdater<SequenceGroup, Sequence[]> SEQUENCE_UPDATER = 
            AtomicReferenceFieldUpdater.newUpdater(SequenceGroup.class, Sequence[].class, "sequences");
    private volatile Sequence[] sequences = new Sequence[0];

    /**
     * Default Constructor
     */
    public SequenceGroup()
    {
        super(-1);
    }

    /**
     * Get the minimum sequence value for the group.
     *
     * @return the minimum sequence value for the group.
     */
    @Override
    public long get()
    {
        return Util.getMinimumSequence(sequences);
    }

    /**
     * Set all {@link Sequence}s in the group to a given value.
     *
     * @param value to set the group of sequences to.
     */
    @Override
    public void set(final long value)
    {
        final Sequence[] sequences = this.sequences;
        for (int i = 0, size = sequences.length; i < size; i++)
        {
            sequences[i].set(value);
        }
    }

    /**
     * Add a {@link Sequence} into this aggregate.  This should only be used during
     * initialisation.  Use {@see SequenceGroup#addWhileRunning(RingBuffer, Sequence)}
     *
     * @param sequence to be added to the aggregate.
     */
    public void add(final Sequence sequence)
    {
        Sequence[] oldSequences;
        Sequence[] newSequences;
        do
        {
            oldSequences = sequences;
            final int oldSize = oldSequences.length;
            newSequences = new Sequence[oldSize + 1];
            System.arraycopy(oldSequences, 0, newSequences, 0, oldSize);
            newSequences[oldSize] = sequence;
        }
        while (!SEQUENCE_UPDATER.compareAndSet(this, oldSequences, newSequences));
    }

    /**
     * Remove the first occurrence of the {@link Sequence} from this aggregate.
     *
     * @param sequence to be removed from this aggregate.
     * @return true if the sequence was removed otherwise false.
     */
    public boolean remove(final Sequence sequence)
    {
        int numToRemove;
        Sequence[] oldSequences;
        Sequence[] newSequences;
        
        do
        {
            oldSequences = sequences;
            
            numToRemove = 0;
            for (Sequence oldSequence : oldSequences)
            {
                if (oldSequence == sequence)
                {
                    numToRemove++;
                }
            }
            
            if (0 == numToRemove)
            {
                break;
            }
            
            final int oldSize = oldSequences.length;
            newSequences = new Sequence[oldSize - numToRemove];

            for (int i = 0, pos = 0; i < oldSize; i++)
            {
                final Sequence testSequence = oldSequences[i];
                if (sequence != testSequence)
                {
                    newSequences[pos++] = testSequence;
                }
            }
        }
        while (!SEQUENCE_UPDATER.compareAndSet(this, oldSequences, newSequences));

        return numToRemove != 0;
    }

    /**
     * Get the size of the group.
     *
     * @return the size of the group.
     */
    public int size()
    {
        return sequences.length;
    }

    /**
     * Adds a sequence to the sequence group after threads have started to publish to
     * the Disruptor.  It will set the sequences to cursor value of the ringBuffer
     * just after adding them.  This should prevent any nasty rewind/wrapping effects. 
     * 
     * @param ringBuffer The ringBuffer that the owner of this sequence group will
     * be pulling it's events from.
     * @param sequence The sequence to add.
     */
    public void addWhileRunning(RingBuffer<?> ringBuffer, Sequence sequence)
    {
        SequenceGroups.addSequences(this, SEQUENCE_UPDATER, ringBuffer, sequence);
    }
}