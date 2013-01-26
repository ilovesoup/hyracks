/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.imru.dataflow;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import edu.uci.ics.hyracks.api.application.INCApplicationContext;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.common.io.RunFileWriter;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractOperatorNodePushable;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.ITupleParser;
import edu.uci.ics.hyracks.imru.api.IIMRUJobSpecification;
import edu.uci.ics.hyracks.imru.base.IConfigurationFactory;
import edu.uci.ics.hyracks.imru.data.RunFileContext;
import edu.uci.ics.hyracks.imru.file.HDFSInputSplitProvider;
import edu.uci.ics.hyracks.imru.file.HDFSUtils;
import edu.uci.ics.hyracks.imru.runtime.bootstrap.IMRURuntimeContext;
import edu.uci.ics.hyracks.imru.state.MapTaskState;
import edu.uci.ics.hyracks.imru.util.IterationUtils;

/**
 * Parses input data from files in HDFS and caches it on the local
 * file system. During IMRU iterations, these cached examples are
 * processed by the Map operator.
 * 
 * @author Josh Rosen
 */
public class DataLoadOperatorDescriptor extends AbstractSingleActivityOperatorDescriptor {

    private static final Logger LOG = Logger.getLogger(MapOperatorDescriptor.class.getName());

    private static final long serialVersionUID = 1L;

    private final IIMRUJobSpecification<?> imruSpec;
    private final HDFSInputSplitProvider inputSplitProvider;
    private final IConfigurationFactory confFactory;
    private final String inputPaths;

    /**
     * Create a new MapOperatorDescriptor.
     * 
     * @param spec
     *            The Hyracks job specification for the dataflow
     * @param imruSpec
     *            The IMRU job specification
     * @param inputSplitProvider
     *            The files to read the input records from
     * @param confFactory
     *            A Hadoop configuration, used for HDFS.
     */
    public DataLoadOperatorDescriptor(JobSpecification spec, IIMRUJobSpecification<?> imruSpec,
            HDFSInputSplitProvider inputSplitProvider, String inputPaths, IConfigurationFactory confFactory) {
        super(spec, 0, 0);
        this.inputSplitProvider = inputSplitProvider;
        this.imruSpec = imruSpec;
        this.confFactory = confFactory;
        this.inputPaths = inputPaths;
    }

    private static class DataLoadOperatorNodePushable extends AbstractOperatorNodePushable {

        private final IHyracksTaskContext ctx;
        private final IHyracksTaskContext fileCtx;
        private final IIMRUJobSpecification<?> imruSpec;
        private final IConfigurationFactory confFactory;
        private final HDFSInputSplitProvider inputSplitProvider;
        private final String inputPaths;
        private final int partition;
        private final String name;

        public DataLoadOperatorNodePushable(IHyracksTaskContext ctx, IIMRUJobSpecification<?> imruSpec,
                HDFSInputSplitProvider inputSplitProvider, String inputPaths, IConfigurationFactory confFactory,
                int partition, String name) {
            this.ctx = ctx;
            this.imruSpec = imruSpec;
            this.confFactory = confFactory;
            this.inputPaths = inputPaths;
            this.inputSplitProvider = inputSplitProvider;
            this.partition = partition;
            this.name = name;
            fileCtx = new RunFileContext(ctx, imruSpec.getCachedDataFrameSize());
        }

        @Override
        public void initialize() throws HyracksDataException {
            Configuration conf = confFactory == null ? null : confFactory.createConfiguration();
            FileSystem dfs = null;
            try {
                if (conf != null)
                    dfs = FileSystem.get(conf);
            } catch (IOException e) {
                fail();
                throw new HyracksDataException(e);
            }

            // Load the examples.
            MapTaskState state = (MapTaskState) IterationUtils.getIterationState(ctx, partition);
            if (state != null) {
                LOG.severe("Duplicate loading of input data.");
                INCApplicationContext appContext = ctx.getJobletContext().getApplicationContext();
                IMRURuntimeContext context = (IMRURuntimeContext) appContext.getApplicationObject();
                context.modelAge = 0;
                //                throw new IllegalStateException("Duplicate loading of input data.");
            }
            long start = System.currentTimeMillis();
            if (state == null)
                state = new MapTaskState(ctx.getJobletContext().getJobId(), ctx.getTaskAttemptId().getTaskId());
            FileReference file = ctx.createUnmanagedWorkspaceFile("IMRUInput");
            RunFileWriter runFileWriter = new RunFileWriter(file, ctx.getIOManager());
            state.setRunFileWriter(runFileWriter);
            runFileWriter.open();

            if (inputSplitProvider == null) {
                try {
                    InputStream in = new FileInputStream(inputPaths.split(",")[partition]);
                    ITupleParser dataLoader = imruSpec.getTupleParserFactory().createTupleParser(fileCtx);
                    dataLoader.parse(in, runFileWriter);
                    in.close();
                } catch (IOException e) {
                    fail();
                    throw new HyracksDataException(e);
                }
            } else {
                List<InputSplit> inputSplits = inputSplitProvider.getInputSplits();
                final FileSplit split = (FileSplit) inputSplits.get(partition);
                Path path = split.getPath();
                try {
                    InputStream in = HDFSUtils.open(dfs, conf, path);
                    ITupleParser dataLoader = imruSpec.getTupleParserFactory().createTupleParser(fileCtx);
                    dataLoader.parse(in, runFileWriter);
                    in.close();
                } catch (IOException e) {
                    fail();
                    throw new HyracksDataException(e);
                }
            }
            runFileWriter.close();
            LOG.info("Cached input data file " + runFileWriter.getFileReference().getFile().getAbsolutePath() + " is "
                    + runFileWriter.getFileSize() + " bytes");
            long end = System.currentTimeMillis();
            LOG.info("Parsed input data in " + (end - start) + " milliseconds");
            IterationUtils.setIterationState(ctx, partition, state);
        }

        @Override
        public void setOutputFrameWriter(int index, IFrameWriter writer, RecordDescriptor recordDesc) {
            throw new IllegalArgumentException();
        }

        @Override
        public void deinitialize() throws HyracksDataException {
        }

        @Override
        public int getInputArity() {
            return 0;
        }

        @Override
        public IFrameWriter getInputFrameWriter(int index) {
            throw new IllegalStateException();
        }

        private void fail() throws HyracksDataException {
        }

    }

    @Override
    public IOperatorNodePushable createPushRuntime(IHyracksTaskContext ctx,
            IRecordDescriptorProvider recordDescProvider, int partition, int nPartitions) throws HyracksDataException {
        return new DataLoadOperatorNodePushable(ctx, imruSpec, inputSplitProvider, inputPaths, confFactory, partition,
                "update " + partition + "/" + nPartitions);
    }

}