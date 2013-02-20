package edu.uci.ics.hyracks.imru.trainmerge;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Random;

import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.api.util.JavaSerializationUtils;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryOutputSourceOperatorNodePushable;
import edu.uci.ics.hyracks.imru.api.IMRUContext;
import edu.uci.ics.hyracks.imru.file.IMRUFileSplit;
import edu.uci.ics.hyracks.imru.util.Rt;

/**
 * @author Rui Wang
 */
public class TrainOD<Model extends Serializable> extends
        AbstractSingleActivityOperatorDescriptor {
    protected final IMRUFileSplit[] inputSplits;
    TrainMergeJob<Model> trainMergejob;
    int totalNodes;

    public TrainOD(JobSpecification spec, TrainMergeJob<Model> trainMergejob,
            IMRUFileSplit[] inputSplits,int totalNodes) {
        super(spec, 0, 1);
        recordDescriptors[0] = new RecordDescriptor(
                new ISerializerDeserializer[1]);
        this.inputSplits = inputSplits;
        this.trainMergejob = trainMergejob;
        this. totalNodes= totalNodes;
    }

    @Override
    public IOperatorNodePushable createPushRuntime(
            final IHyracksTaskContext ctx,
            IRecordDescriptorProvider recordDescProvider, final int partition,
            final int nPartitions) throws HyracksDataException {
        return new AbstractUnaryOutputSourceOperatorNodePushable() {
            @Override
            public void initialize() throws HyracksDataException {
                TrainOD.this.nextFrame(ctx, writer, partition, null, null,
                        nPartitions);
            }
        };
    }

    Random random = new Random();

    public void nextFrame(IHyracksTaskContext ctx, IFrameWriter writer,
            int partition, ByteBuffer buffer, LinkedList<ByteBuffer> queue,
            int nPartitions) throws HyracksDataException {
        int frameSize = ctx.getFrameSize();
        if (buffer != null) {
            ByteBuffer frame = ctx.allocateFrame();
            frame.put(buffer.array(), 0, frameSize);
            queue.add(frame);
            int size = buffer.getInt(4);
            int position = buffer.getInt(8);
            //            Rt.p(position + "/" + size);
            if (position + frameSize - 12 < size)
                return;
        }
        writer.open();
        try {
            TrainMergeContext<Model> context = new TrainMergeContext<Model>(
                    ctx, "train", writer);
            String nodeId = context.getNodeId();
            Model model = (Model) context.getModel();
            trainMergejob.train(context, inputSplits[partition], model,
                    totalNodes);
        } catch (IOException e) {
            writer.fail();
            throw new HyracksDataException(e);
        } finally {
            writer.close();
        }
    }
}
