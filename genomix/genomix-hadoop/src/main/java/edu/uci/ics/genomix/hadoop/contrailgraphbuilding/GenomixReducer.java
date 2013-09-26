package edu.uci.ics.genomix.hadoop.contrailgraphbuilding;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Iterator;

import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;

import edu.uci.ics.genomix.type.NodeWritable;
import edu.uci.ics.genomix.type.NodeWritable.EDGETYPE;
import edu.uci.ics.genomix.type.VKmerBytesWritable;

@SuppressWarnings("deprecation")
public class GenomixReducer extends MapReduceBase implements
	Reducer<VKmerBytesWritable, NodeWritable, VKmerBytesWritable, NodeWritable>{
    
    private NodeWritable outputNode;
    private NodeWritable tmpNode;
    private float averageCoverage;
    
    @Override
    public void configure(JobConf job) {
        outputNode = new NodeWritable();
        tmpNode = new NodeWritable();
    }
    
	@Override
	public void reduce(VKmerBytesWritable key, Iterator<NodeWritable> values,
			OutputCollector<VKmerBytesWritable, NodeWritable> output,
			Reporter reporter) throws IOException {
		outputNode.reset();
		averageCoverage = 0;
		
		while (values.hasNext()) {
		    tmpNode.setAsCopy(values.next());
		    for (EDGETYPE e: EnumSet.allOf(EDGETYPE.class)) {
		        outputNode.getEdgeList(e).unionUpdate(tmpNode.getEdgeList(e));
		    }
		    outputNode.getStartReads().unionUpdate(tmpNode.getStartReads());
		    outputNode.getEndReads().unionUpdate(tmpNode.getEndReads());
		    averageCoverage += tmpNode.getAvgCoverage();
		}
		outputNode.setAvgCoverage(averageCoverage);
		output.collect(key,outputNode);
	}

}
