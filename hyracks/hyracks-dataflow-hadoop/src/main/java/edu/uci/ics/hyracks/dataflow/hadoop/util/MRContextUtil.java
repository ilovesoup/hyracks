/*
 * Copyright 2009-2013 by The Regents of the University of California
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
package edu.uci.ics.hyracks.dataflow.hadoop.util;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.mapred.RawKeyValueIterator;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.StatusReporter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskType;
import org.apache.hadoop.mapreduce.counters.GenericCounter;
import org.apache.hadoop.mapreduce.task.JobContextImpl;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.apache.hadoop.mapreduce.task.MapContextImpl;
import org.apache.hadoop.mapreduce.task.ReduceContextImpl;
import org.apache.hadoop.mapreduce.lib.map.WrappedMapper;
import org.apache.hadoop.mapreduce.lib.reduce.WrappedReducer;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;

/**
 * The wrapper to generate TaskTattemptContext
 */
public class MRContextUtil {
    @SuppressWarnings({ "rawtypes", "unchecked" })
        public Mapper.Context createMapContext(Configuration conf, TaskAttemptID taskid,  RecordReader reader,
            RecordWriter writer, OutputCommitter committer, StatusReporter reporter, InputSplit split) {
        return new WrappedMapper().getMapContext(new MapContextImpl(conf, taskid, reader, writer, committer, reporter, split));
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Reducer.Context createReduceContext(Configuration conf, TaskAttemptID taskid,RawKeyValueIterator input, 
                Counter inputKeyCounter, Counter inputValueCounter, RecordWriter output, OutputCommitter committer, 
                StatusReporter reporter, RawComparator comparator, Class keyClass, Class valueClass) throws HyracksDataException {
        try {
            return new WrappedReducer().getReducerContext(new ReduceContextImpl(conf, taskid, input, inputKeyCounter, inputValueCounter, output, committer, reporter, comparator, keyClass, valueClass));
        } catch (Exception e) {
            throw new HyracksDataException(e);
        }
    }
}