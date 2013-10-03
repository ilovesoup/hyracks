package edu.uci.ics.genomix.pregelix.operator.scaffolding;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Writable;

import edu.uci.ics.genomix.pregelix.client.Client;
import edu.uci.ics.genomix.pregelix.io.VertexValueWritable;
import edu.uci.ics.genomix.pregelix.io.common.ArrayListWritable;
import edu.uci.ics.genomix.pregelix.io.common.HashMapWritable;
import edu.uci.ics.genomix.pregelix.io.common.VLongWritable;
import edu.uci.ics.genomix.pregelix.io.message.BFSTraverseMessage;
import edu.uci.ics.genomix.pregelix.io.message.MessageWritable;
import edu.uci.ics.genomix.pregelix.operator.BasicGraphCleanVertex;
import edu.uci.ics.genomix.pregelix.operator.aggregator.StatisticsAggregator;
import edu.uci.ics.genomix.pregelix.type.StatisticsCounter;
import edu.uci.ics.genomix.config.GenomixJobConf;
import edu.uci.ics.genomix.type.ReadHeadSet;
import edu.uci.ics.genomix.type.ReadHeadInfo;
import edu.uci.ics.genomix.type.VKmer;
import edu.uci.ics.pregelix.api.job.PregelixJob;
import edu.uci.ics.pregelix.api.util.BspUtils;
import edu.uci.ics.pregelix.dataflow.util.IterationUtils;

/**
 * Graph clean pattern: Scaffolding
 * @author anbangx
 *
 */
public class ScaffoldingVertex extends 
    BFSTraverseVertex{
    
    public enum READHEADTYPE{
        UNFLIPPED,
        FLIPPED;
        
        
    }
    
	// TODO BFS can seperate into simple BFS to filter and real BFS
    public static class SearchInfo implements Writable{
        private VKmer kmer;
        private boolean flip;
        
        public SearchInfo(VKmer otherKmer, boolean flip){
            this.kmer.setAsCopy(otherKmer);
            this.flip = flip;
        }
        
        public SearchInfo(VKmer otherKmer, READHEADTYPE flip){
            this.kmer.setAsCopy(otherKmer);
            this.flip = flip == READHEADTYPE.FLIPPED ? true : false;
        }
        
        public VKmer getKmer() {
            return kmer;
        }

        public void setKmer(VKmer kmer) {
            this.kmer = kmer;
        }

        public boolean isFlip() {
            return flip;
        }

        public void setFlip(boolean flip) {
            this.flip = flip;
        }

        @Override
        public void write(DataOutput out) throws IOException {
            kmer.write(out);
            out.writeBoolean(flip);
        }

        @Override
        public void readFields(DataInput in) throws IOException {
            kmer.readFields(in);
            flip = in.readBoolean();
        }
    }
    
    // add to driver
    public static int SCAFFOLDING_MIN_TRAVERSAL_LENGTH = 20;
    public static int SCAFFOLDING_MAX_TRAVERSAL_LENGTH = 100;
    public static int SCAFFOLDING_MIN_COVERAGE = 20;
    public static int SCAFFOLDING_MIN_LENGTH = 20;
    
    // TODO VLong to Long
    private HashMapWritable<LongWritable, ArrayListWritable<SearchInfo>> scaffoldingMap = new HashMapWritable<LongWritable, ArrayListWritable<SearchInfo>>();
    
    @Override
    public void initVertex() {
        super.initVertex();
        if(getSuperstep() == 1)
            StatisticsAggregator.preGlobalCounters.clear();
        // TODO move readGlobalAggregator into 2nd iteration
//        else
//            StatisticsAggregator.preGlobalCounters = BasicGraphCleanVertex.readStatisticsCounterResult(getContext().getConfiguration());
        if(getSuperstep() == 1)
            ScaffoldingAggregator.preScaffoldingMap.clear();
        else if(getSuperstep() == 2)
            ScaffoldingAggregator.preScaffoldingMap = readScaffoldingMapResult(getContext().getConfiguration());
        counters.clear();
        scaffoldingMap.clear();
        getVertexValue().getCounters().clear();
        getVertexValue().getScaffoldingMap().clear();
    }
    
    // send map to readId.hashValue() bin
    public void addReadsToScaffoldingMap(ReadHeadSet readIds, READHEADTYPE isFlip){ // TODO ENUM for flip
    	// searchInfo can be a struct
        SearchInfo searchInfo;
        ArrayListWritable<SearchInfo> searchInfoList;
        
        //TODO rename PositionWritable ReadIdInfo?
        for(ReadHeadInfo pos : readIds){ 
        	long readId = pos.getReadId();
            if(scaffoldingMap.containsKey(readId)){
                searchInfoList = scaffoldingMap.get(readId);
            } else{
                searchInfoList = new ArrayListWritable<SearchInfo>();
                scaffoldingMap.put(new LongWritable(readId), searchInfoList);
            }
            searchInfo = new SearchInfo(getVertexId(), isFlip);
            searchInfoList.add(searchInfo);
        }
    }
    
    public boolean isInRange(int traversalLength){
        return traversalLength < SCAFFOLDING_MAX_TRAVERSAL_LENGTH && traversalLength > SCAFFOLDING_MIN_TRAVERSAL_LENGTH;
    }
    
    /**
     * step 1:
     */
    public void generateScaffoldingMap(){
    	// add a fake vertex 
        addFakeVertex("A");
        // grouped by 5'/~5' readId in aggregator
        VertexValueWritable vertex = getVertexValue();
        if(vertex.getAverageCoverage() >= SCAFFOLDING_MIN_COVERAGE 
                && vertex.getInternalKmer().getLength() < SCAFFOLDING_MIN_LENGTH){ 
        	addReadsToScaffoldingMap(vertex.getStartReads(), READHEADTYPE.UNFLIPPED);
        	addReadsToScaffoldingMap(vertex.getEndReads(), READHEADTYPE.FLIPPED);
//			addStartReadsToScaffoldingMap();
//			addEndReadsToScaffoldingMap();
        	vertex.setScaffoldingMap(scaffoldingMap);
        }
        voteToHalt();
    }
    
    /**
     * step 2:
     */
    public void processScaffoldingMap(){
    	// fake vertex process scaffoldingMap 
        ArrayListWritable<SearchInfo> searchInfoList;
        for(Entry<LongWritable, ArrayListWritable<SearchInfo>> entry : ScaffoldingAggregator.preScaffoldingMap.entrySet()){
        	searchInfoList = entry.getValue();
            if(searchInfoList.size() > 2)
                throw new IllegalStateException("The size of SearchInfoList should be not bigger than 2, but here its size " +
                		"is " + searchInfoList.size() + "!");
            if(searchInfoList.size() == 2){
                outgoingMsg.reset();
                VKmer srcNode = setOutgoingSrcAndDest(entry.getKey().get(), searchInfoList);
                sendMsg(srcNode, outgoingMsg);
            }
        }
        
        deleteVertex(getVertexId());
    }
    
    /**
     * step 3:
     */
    public void BFSearch(Iterator<BFSTraverseMessage> msgIterator){
    	BFSTraverseMessage incomingMsg;
        while(msgIterator.hasNext()){
            incomingMsg = msgIterator.next();
            if(incomingMsg.isTraverseMsg()){
                // check if find destination
            	// TODO explicitly set message type
            	// TODO Switch is better than if else
                int traversalLength = incomingMsg.getPathList().getCountOfPosition();
                if(incomingMsg.getSeekedVertexId().equals(getVertexId())){
                	//TODO change this length to internalKmerLength
                	//TODO keep track of the total kmerLength you've come (account for partial overlaps)
                	// final step to process BFS -- pathList and edgeTypesList
                    finalProcessBFS(incomingMsg); //TODO add 
                    if(isValidDestination(incomingMsg) && isInRange(traversalLength)){
                        // TODO store BFS paths until all finish, if more than 1, it's ambiguous
                        // send message to all the path nodes to add this common readId
                        sendMsgToPathNodeToAddCommondReadId(incomingMsg.getReadId(), incomingMsg.getPathList(),
                        		incomingMsg.getEdgeTypesList());
                        //set statistics counter: Num_RemovedLowCoverageNodes
                        incrementCounter(StatisticsCounter.Num_Scaffodings);
                        getVertexValue().setCounters(counters);
                        
                    }
                }
                if(isInRange(traversalLength)){
                    //continue to BFS
                    broadcaseBFSTraverse(incomingMsg);
            	}
//                } else {
//                    //begin(step == 3) or continue(step > 3) to BFS
//                    broadcaseBFSTraverse(incomingMsg);
//                }
            } else{
                // append common readId to the corresponding edge
                appendCommonReadId(incomingMsg);
            }
        }
        voteToHalt();
    }
    
    @Override
    public void compute(Iterator<BFSTraverseMessage> msgIterator) {
        initVertex();
        if(getSuperstep() == 1){
        	generateScaffoldingMap();
        } else if(getSuperstep() == 2){
        	processScaffoldingMap();
        } else if(getSuperstep() >= 3){
        	BFSearch(msgIterator);
        }
    }
    
    public static HashMapWritable<LongWritable, ArrayListWritable<SearchInfo>> readScaffoldingMapResult(Configuration conf) {
        try {
            VertexValueWritable value = (VertexValueWritable) IterationUtils
                    .readGlobalAggregateValue(conf, BspUtils.getJobId(conf));
            return value.getScaffoldingMap();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    public static void main(String[] args) throws Exception {
        Client.run(args, getConfiguredJob(null, ScaffoldingVertex.class));
    }
    
    public static PregelixJob getConfiguredJob(GenomixJobConf conf, Class<? extends BasicGraphCleanVertex<? extends VertexValueWritable, ? extends MessageWritable>> vertexClass) throws IOException {
        PregelixJob job = BasicGraphCleanVertex.getConfiguredJob(conf, vertexClass);
        job.setGlobalAggregatorClass(ScaffoldingAggregator.class);
        return job;
    }
}
