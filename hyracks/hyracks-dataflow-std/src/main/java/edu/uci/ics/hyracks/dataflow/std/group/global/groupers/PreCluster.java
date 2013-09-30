/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.dataflow.std.group.global.groupers;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.std.group.AggregateState;
import edu.uci.ics.hyracks.dataflow.std.group.IAggregatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.group.IAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.group.global.base.GrouperFlushOption;
import edu.uci.ics.hyracks.dataflow.std.group.global.data.StateLessFrameTupleAppender;

public class PreCluster extends AbstractHistogramPushBasedGrouper {

    private final IBinaryComparator[] comparators;

    protected ByteBuffer[] buffers;
    protected ByteBuffer outputBuffer;

    private IAggregatorDescriptor aggregator;
    private AggregateState aggregateState;
    private IAggregatorDescriptor merger;

    private int frameIndexPointer, tupleIndexPointer;

    private ArrayTupleBuilder groupStateBuilder, flushTupleBuilder;
    private FrameTupleAccessor inputFrameAccessor, groupStateFrameAccessor;

    // For debugging
    private long compCounter, inRecCounter, outRecCounter, outFrameCounter, flushedRecCounter;

    public PreCluster(IHyracksTaskContext ctx, int[] keyFields, int[] decorFields, int framesLimit,
            IAggregatorDescriptorFactory aggregatorFactory, IAggregatorDescriptorFactory mergerFactory,
            RecordDescriptor inRecDesc, RecordDescriptor outRecDesc, IBinaryComparatorFactory[] comparatorFactories,
            IFrameWriter outputWriter) throws HyracksDataException {
        super(ctx, keyFields, decorFields, framesLimit, aggregatorFactory, mergerFactory, inRecDesc, outRecDesc, false,
                outputWriter, false);

        this.comparators = new IBinaryComparator[comparatorFactories.length];
        for (int i = 0; i < comparators.length; i++) {
            this.comparators[i] = comparatorFactories[i].createBinaryComparator();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.ics.hyracks.api.comm.IFrameWriter#open()
     */
    @Override
    public void open() throws HyracksDataException {

        int[] storedKeyFields = new int[keyFields.length];
        for (int i = 0; i < keyFields.length; i++) {
            storedKeyFields[i] = i;
        }

        this.aggregator = aggregatorFactory.createAggregator(ctx, inRecDesc, outRecDesc, keyFields, storedKeyFields);
        this.merger = mergerFactory.createAggregator(ctx, outRecDesc, outRecDesc, storedKeyFields, storedKeyFields);
        this.aggregateState = this.aggregator.createAggregateStates();

        this.inputFrameAccessor = new FrameTupleAccessor(frameSize, inRecDesc);
        this.groupStateFrameAccessor = new FrameTupleAccessor(frameSize, outRecDesc);
        this.groupStateBuilder = new ArrayTupleBuilder(outRecDesc.getFieldCount());
        this.flushTupleBuilder = new ArrayTupleBuilder(outRecDesc.getFieldCount());

        this.buffers = new ByteBuffer[framesLimit - 1];
        this.outputBuffer = ctx.allocateFrame();

        // initialize the pointer to the tuple to be compared
        this.frameIndexPointer = -1;
        this.tupleIndexPointer = -1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.ics.hyracks.api.comm.IFrameWriter#nextFrame(java.nio.ByteBuffer)
     */
    @Override
    public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
        inputFrameAccessor.reset(buffer);
        int tupleCount = inputFrameAccessor.getTupleCount();
        for (int i = 0; i < tupleCount; i++) {

            boolean insertNew = false;

            if (this.frameIndexPointer < 0) {
                insertNew = true;
            } else {
                groupStateFrameAccessor.reset(buffers[frameIndexPointer]);
                if (!sameGroup(inputFrameAccessor, i, groupStateFrameAccessor, tupleIndexPointer)) {
                    insertNew = true;
                }
            }

            if (insertNew) {

                // dump the current group state into the result state
                if (frameIndexPointer >= 0) {
                    groupStateFrameAccessor.reset(buffers[frameIndexPointer]);
                    flushTupleBuilder.reset();
                    for (int j = 0; j < keyFields.length + decorFields.length; j++) {
                        flushTupleBuilder.addField(groupStateFrameAccessor, tupleIndexPointer, j);
                    }
                    merger.outputFinalResult(flushTupleBuilder, groupStateFrameAccessor, tupleIndexPointer,
                            aggregateState);

                    StateLessFrameTupleAppender.removeLastTuple(buffers[frameIndexPointer]);
                    if (!StateLessFrameTupleAppender.append(buffers[frameIndexPointer],
                            flushTupleBuilder.getFieldEndOffsets(), flushTupleBuilder.getByteArray(), 0,
                            flushTupleBuilder.getSize())) {
                        frameIndexPointer++;
                        if (frameIndexPointer == buffers.length) {
                            flush(outputWriter, GrouperFlushOption.FLUSH_FOR_RESULT_STATE);
                            frameIndexPointer = 0;
                        }
                        if (buffers[frameIndexPointer] == null) {
                            buffers[frameIndexPointer] = ctx.allocateFrame();
                        }
                        StateLessFrameTupleAppender.reset(buffers[frameIndexPointer]);
                        if (!StateLessFrameTupleAppender.append(buffers[frameIndexPointer],
                                flushTupleBuilder.getFieldEndOffsets(), flushTupleBuilder.getByteArray(), 0,
                                flushTupleBuilder.getSize())) {
                            throw new HyracksDataException("Failed to write a group into the output buffer.");
                        }
                    }
                    tupleIndexPointer = StateLessFrameTupleAppender.getTupleCount(buffers[frameIndexPointer]) - 1;
                } else {
                    frameIndexPointer = 0;
                    tupleIndexPointer = 0;
                    if (buffers[frameIndexPointer] == null) {
                        buffers[frameIndexPointer] = ctx.allocateFrame();
                        StateLessFrameTupleAppender.reset(buffers[frameIndexPointer]);
                    }
                }

                // insert the new group
                groupStateBuilder.reset();
                for (int j = 0; j < keyFields.length; j++) {
                    groupStateBuilder.addField(inputFrameAccessor, i, keyFields[j]);
                }

                for (int j = 0; j < decorFields.length; j++) {
                    groupStateBuilder.addField(inputFrameAccessor, i, decorFields[j]);
                }

                aggregator.init(groupStateBuilder, inputFrameAccessor, i, aggregateState);

                if (!StateLessFrameTupleAppender.append(buffers[frameIndexPointer],
                        groupStateBuilder.getFieldEndOffsets(), groupStateBuilder.getByteArray(), 0,
                        groupStateBuilder.getSize())) {
                    frameIndexPointer++;
                    if (frameIndexPointer == buffers.length) {
                        flush(outputWriter, GrouperFlushOption.FLUSH_FOR_RESULT_STATE);
                        frameIndexPointer = 0;
                    }
                    if (buffers[frameIndexPointer] == null) {
                        buffers[frameIndexPointer] = ctx.allocateFrame();
                    }
                    StateLessFrameTupleAppender.reset(buffers[frameIndexPointer]);
                    if (!StateLessFrameTupleAppender.append(buffers[frameIndexPointer],
                            groupStateBuilder.getFieldEndOffsets(), groupStateBuilder.getByteArray(), 0,
                            groupStateBuilder.getSize())) {
                        throw new HyracksDataException("Failed to write a group into the output buffer.");
                    }
                }
                tupleIndexPointer = StateLessFrameTupleAppender.getTupleCount(buffers[frameIndexPointer]) - 1;
            } else {
                groupStateFrameAccessor.reset(buffers[frameIndexPointer]);
                int groupStateStartOffset = groupStateFrameAccessor.getTupleStartOffset(tupleIndexPointer);
                aggregator.aggregate(inputFrameAccessor, i, buffers[frameIndexPointer].array(), groupStateStartOffset,
                        groupStateFrameAccessor.getTupleEndOffset(tupleIndexPointer) - groupStateStartOffset,
                        aggregateState);
            }
        }
    }

    protected boolean sameGroup(FrameTupleAccessor a1, int t1Idx, FrameTupleAccessor a2, int t2Idx) {
        for (int i = 0; i < comparators.length; i++) {
            int fIdx = keyFields[i];
            int s1 = a1.getTupleStartOffset(t1Idx) + a1.getFieldSlotsLength() + a1.getFieldStartOffset(t1Idx, fIdx);
            int l1 = a1.getFieldLength(t1Idx, fIdx);
            int s2 = a2.getTupleStartOffset(t2Idx) + a2.getFieldSlotsLength() + a2.getFieldStartOffset(t2Idx, i);
            int l2 = a2.getFieldLength(t2Idx, i);
            if (comparators[i].compare(a1.getBuffer().array(), s1, l1, a2.getBuffer().array(), s2, l2) != 0) {
                return false;
            }
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.ics.hyracks.api.comm.IFrameWriter#fail()
     */
    @Override
    public void fail() throws HyracksDataException {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.uci.ics.hyracks.api.comm.IFrameWriter#close()
     */
    @Override
    public void close() throws HyracksDataException {
        ctx.getCounterContext().getCounter("costmodel.io", true).update(ioCounter);
        ctx.getCounterContext().getCounter("costmodel.cpu", true).update(cpuCounter);
        ctx.getCounterContext().getCounter("costmodel.network", true).update(dumpCounter);
        ioCounter = 0;
        cpuCounter = 0;
        dumpCounter = 0;

        ctx.getCounterContext().getCounter(debugID + ".comparisons", true).update(compCounter);
        ctx.getCounterContext().getCounter(debugID + ".inputRecords", true).update(inRecCounter);
        ctx.getCounterContext().getCounter(debugID + ".outputRecords", true).update(outRecCounter);
        ctx.getCounterContext().getCounter(debugID + ".outputFrames", true).update(outFrameCounter);
        ctx.getCounterContext().getCounter(debugID + ".flushedRecords", true).update(flushedRecCounter);

        this.compCounter = 0;
        this.inRecCounter = 0;
        this.outRecCounter = 0;
        this.outFrameCounter = 0;
        this.flushedRecCounter = 0;

        this.buffers = null;
        aggregateState.close();
        this.outputBuffer = null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.ics.hyracks.dataflow.std.group.global.AbstractHistogramPushBasedGrouper#flush(edu.uci.ics.hyracks.api
     * .comm.IFrameWriter, edu.uci.ics.hyracks.dataflow.std.group.global.base.GrouperFlushOption)
     */
    @Override
    protected void flush(IFrameWriter writer, GrouperFlushOption flushOption) throws HyracksDataException {
        for (int i = 0; i <= frameIndexPointer && i < buffers.length; i++) {
            FrameUtils.flushFrame(buffers[i], writer);
        }

    }

    @Override
    public void wrapup() throws HyracksDataException {
        if (frameIndexPointer >= 0) {
            groupStateFrameAccessor.reset(buffers[frameIndexPointer]);
            flushTupleBuilder.reset();
            for (int j = 0; j < keyFields.length + decorFields.length; j++) {
                flushTupleBuilder.addField(groupStateFrameAccessor, tupleIndexPointer, j);
            }
            merger.outputFinalResult(flushTupleBuilder, groupStateFrameAccessor, tupleIndexPointer, aggregateState);

            StateLessFrameTupleAppender.removeLastTuple(buffers[frameIndexPointer]);
            if (!StateLessFrameTupleAppender.append(buffers[frameIndexPointer], flushTupleBuilder.getFieldEndOffsets(),
                    flushTupleBuilder.getByteArray(), 0, flushTupleBuilder.getSize())) {
                frameIndexPointer++;
                if (frameIndexPointer == buffers.length) {
                    flush(outputWriter, GrouperFlushOption.FLUSH_FOR_RESULT_STATE);
                    frameIndexPointer = 0;
                }
                if (buffers[frameIndexPointer] == null) {
                    buffers[frameIndexPointer] = ctx.allocateFrame();
                }
                StateLessFrameTupleAppender.reset(buffers[frameIndexPointer]);
                if (!StateLessFrameTupleAppender.append(buffers[frameIndexPointer],
                        flushTupleBuilder.getFieldEndOffsets(), flushTupleBuilder.getByteArray(), 0,
                        flushTupleBuilder.getSize())) {
                    throw new HyracksDataException("Failed to write a group into the output buffer.");
                }
            }
            tupleIndexPointer = StateLessFrameTupleAppender.getTupleCount(buffers[frameIndexPointer]) - 1;
        }
        flush(outputWriter, GrouperFlushOption.FLUSH_FOR_RESULT_STATE);
    }

}