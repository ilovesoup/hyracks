package edu.uci.ics.testselect;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import edu.uci.ics.hivesterix.logical.expression.HiveExpressionTypeComputer;
import edu.uci.ics.hivesterix.logical.expression.HiveNullableTypeComputer;
import edu.uci.ics.hivesterix.logical.expression.HivePartialAggregationTypeComputer;
import edu.uci.ics.hivesterix.runtime.factory.evaluator.HiveExpressionRuntimeProvider;
import edu.uci.ics.hivesterix.runtime.factory.nullwriter.HiveNullWriterFactory;
import edu.uci.ics.hivesterix.runtime.inspector.HiveBinaryBooleanInspectorFactory;
import edu.uci.ics.hivesterix.runtime.inspector.HiveBinaryIntegerInspectorFactory;
import edu.uci.ics.hivesterix.runtime.provider.HiveBinaryComparatorFactoryProvider;
import edu.uci.ics.hivesterix.runtime.provider.HiveNormalizedKeyComputerFactoryProvider;
import edu.uci.ics.hivesterix.runtime.provider.HivePrinterFactoryProvider;
import edu.uci.ics.hivesterix.runtime.provider.HiveSerializerDeserializerProvider;
import edu.uci.ics.hivesterix.runtime.provider.HiveTypeTraitProvider;
import edu.uci.ics.hyracks.algebricks.common.constraints.AlgebricksAbsolutePartitionConstraint;
import edu.uci.ics.hyracks.algebricks.common.constraints.AlgebricksPartitionConstraint;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.utils.Pair;
import edu.uci.ics.hyracks.algebricks.compiler.api.HeuristicCompilerFactoryBuilder;
import edu.uci.ics.hyracks.algebricks.compiler.api.ICompiler;
import edu.uci.ics.hyracks.algebricks.compiler.api.ICompilerFactory;
import edu.uci.ics.hyracks.algebricks.compiler.rewriter.rulecontrollers.SequentialFixpointRuleController;
import edu.uci.ics.hyracks.algebricks.compiler.rewriter.rulecontrollers.SequentialOnceRuleController;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IExpressionEvalSizeComputer;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IExpressionRuntimeProvider;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IExpressionTypeComputer;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IMergeAggregationExpressionFactory;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.INullableTypeComputer;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IPartialAggregationTypeComputer;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.LogicalExpressionJobGenToExpressionRuntimeProviderAdapter;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ScalarFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.IFunctionInfo;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IDataSink;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IDataSource;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IDataSourceIndex;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IMetadataProvider;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.DataSourceScanOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.EmptyTupleSourceOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.SelectOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.WriteOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.plan.ALogicalPlanImpl;
import edu.uci.ics.hyracks.algebricks.core.algebra.typing.ITypingContext;
import edu.uci.ics.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import edu.uci.ics.hyracks.algebricks.core.jobgen.impl.OperatorSchemaImpl;
import edu.uci.ics.hyracks.algebricks.core.jobgen.impl.PlanCompiler;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.AbstractRuleController;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.AlgebricksOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.PhysicalOptimizationConfig;
import edu.uci.ics.hyracks.algebricks.data.IBinaryBooleanInspectorFactory;
import edu.uci.ics.hyracks.algebricks.data.IBinaryComparatorFactoryProvider;
import edu.uci.ics.hyracks.algebricks.data.IBinaryHashFunctionFactoryProvider;
import edu.uci.ics.hyracks.algebricks.data.IBinaryHashFunctionFamilyProvider;
import edu.uci.ics.hyracks.algebricks.data.IBinaryIntegerInspectorFactory;
import edu.uci.ics.hyracks.algebricks.data.INormalizedKeyComputerFactoryProvider;
import edu.uci.ics.hyracks.algebricks.data.IPrinterFactory;
import edu.uci.ics.hyracks.algebricks.data.IPrinterFactoryProvider;
import edu.uci.ics.hyracks.algebricks.data.ISerializerDeserializerProvider;
import edu.uci.ics.hyracks.algebricks.data.ITypeTraitProvider;
import edu.uci.ics.hyracks.algebricks.examples.piglet.compiler.PigletPrinterFactoryProvider;
import edu.uci.ics.hyracks.algebricks.examples.piglet.metadata.PigletFileDataSink;
import edu.uci.ics.hyracks.algebricks.examples.piglet.runtime.PigletExpressionJobGen;
import edu.uci.ics.hyracks.algebricks.examples.piglet.types.CharArrayType;
import edu.uci.ics.hyracks.algebricks.examples.piglet.types.Type;
import edu.uci.ics.hyracks.algebricks.runtime.base.IPushRuntimeFactory;
import edu.uci.ics.hyracks.algebricks.runtime.operators.std.SinkWriterRuntimeFactory;
import edu.uci.ics.hyracks.algebricks.runtime.writers.PrinterBasedWriterFactory;
import edu.uci.ics.hyracks.api.application.INCApplicationContext;
import edu.uci.ics.hyracks.api.client.impl.JobSpecificationActivityClusterGraphGeneratorFactory;
import edu.uci.ics.hyracks.api.comm.IFrameReader;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.comm.IPartitionCollector;
import edu.uci.ics.hyracks.api.context.IHyracksJobletContext;
import edu.uci.ics.hyracks.api.context.IHyracksRootContext;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.ActivityId;
import edu.uci.ics.hyracks.api.dataflow.ConnectorDescriptorId;
import edu.uci.ics.hyracks.api.dataflow.IActivity;
import edu.uci.ics.hyracks.api.dataflow.IConnectorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.IOperatorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.OperatorDescriptorId;
import edu.uci.ics.hyracks.api.dataflow.TaskAttemptId;
import edu.uci.ics.hyracks.api.dataflow.TaskId;
import edu.uci.ics.hyracks.api.dataflow.state.IStateObject;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.INullWriterFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.dataset.IDatasetPartitionManager;
import edu.uci.ics.hyracks.api.deployment.DeploymentId;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.io.IIOManager;
import edu.uci.ics.hyracks.api.job.ActivityCluster;
import edu.uci.ics.hyracks.api.job.ActivityClusterGraph;
import edu.uci.ics.hyracks.api.job.ActivityClusterId;
import edu.uci.ics.hyracks.api.job.IActivityClusterGraphGenerator;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.api.job.JobStatus;
import edu.uci.ics.hyracks.api.job.profiling.counters.ICounterContext;
import edu.uci.ics.hyracks.api.resources.IDeallocatable;
import edu.uci.ics.hyracks.api.rewriter.ActivityClusterGraphRewriter;
import edu.uci.ics.hyracks.api.rewriter.runtime.SuperActivity;
import edu.uci.ics.hyracks.control.cc.job.TaskCluster;
import edu.uci.ics.hyracks.control.cc.work.JobCleanupWork;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.FloatSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.FloatParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IValueParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IntegerParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.UTF8StringParserFactory;
import edu.uci.ics.hyracks.dataflow.std.collectors.IPartitionAcceptor;
import edu.uci.ics.hyracks.dataflow.std.collectors.PartitionCollector;
import edu.uci.ics.hyracks.dataflow.std.file.ConstantFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.DelimitedDataTupleParserFactory;
import edu.uci.ics.hyracks.dataflow.std.file.FileScanOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.ITupleParserFactory;
import edu.uci.ics.hyracks.hdfs.lib.RawBinaryHashFunctionFactory;


public class TestSelect{
	static int varCounter=0;

	private static List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> buildDefaultLogicalRewrites() {
        List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> defaultLogicalRewrites = new ArrayList<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>>();
        SequentialFixpointRuleController seqCtrlNoDfs = new SequentialFixpointRuleController(false);
        SequentialFixpointRuleController seqCtrlFullDfs = new SequentialFixpointRuleController(true);
        SequentialOnceRuleController seqOnceCtrl = new SequentialOnceRuleController(true);
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrl,
                TestRules.buildTypeInferenceRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlFullDfs,
                TestRules.buildNormalizationRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                TestRules.buildCondPushDownRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                TestRules.buildJoinInferenceRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                TestRules.buildOpPushDownRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrl,
                TestRules.buildDataExchangeRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                TestRules.buildConsolidationRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlFullDfs,
                TestRules.inferTypesRuleCollection()));
        return defaultLogicalRewrites;
	}

	private static List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> buildDefaultPhysicalRewrites() {
        List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> defaultPhysicalRewrites = new ArrayList<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>>();
        SequentialOnceRuleController seqOnceCtrlAllLevels = new SequentialOnceRuleController(true);
        SequentialOnceRuleController seqOnceCtrlTopLevel = new SequentialOnceRuleController(false);
        defaultPhysicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrlAllLevels,
                TestRules.buildPhysicalRewritesAllLevelsRuleCollection()));
        defaultPhysicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrlTopLevel,
                TestRules.buildPhysicalRewritesTopLevelRuleCollection()));
        defaultPhysicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrlAllLevels,
                TestRules.prepareForJobGenRuleCollection()));
        return defaultPhysicalRewrites;
	}

	public static LogicalVariable newVariable() {
		return new LogicalVariable(varCounter++);
	}
	
	static void executeJobSpecification(JobSpecification jobSpec, int framesize) throws HyracksException{
		
		//iterate on the job specification
		//for each operator, call nextFrame
		JobSpecificationActivityClusterGraphGeneratorFactory activtyGen = new JobSpecificationActivityClusterGraphGeneratorFactory(jobSpec);
		JobId jid = new JobId(0);
		IActivityClusterGraphGenerator createActivityClusterGraphGenerator = activtyGen.createActivityClusterGraphGenerator(jid, null, null);
		
		//Hein???!!! Et pourquoi il bug s'il n'y a pas de final??
		final ActivityClusterGraph acg = createActivityClusterGraphGenerator.initialize();
		ActivityClusterGraphRewriter acgr = new ActivityClusterGraphRewriter();
		acgr.rewrite(acg);
		
	    //Now the ActivityCluster contains super activities, stored in this map:
		Map<ActivityClusterId, ActivityCluster> acm = acg.getActivityClusterMap();
		for (ActivityClusterId acid : acm.keySet()) {
		    
			ActivityCluster ac = acm.get(acid);
			for(ActivityId aid : ac.getActivityMap().keySet()){
				Map<ActivityId, IActivity> mapAct = ac.getActivityMap();
				IActivity acti = mapAct.get(aid);
				
				SuperActivity sact = new SuperActivity(acg, acid, aid);
				sact.addActivity(acti);
				
				IHyracksTaskContext ctx = new HyracksTaskContext4TestSelect(); 
				
				IRecordDescriptorProvider recordDescProvider = new IRecordDescriptorProvider() {
	                @Override
	                public RecordDescriptor getOutputRecordDescriptor(ActivityId aid, int outputIndex) {
	                    ActivityCluster ac = acg.getActivityMap().get(aid);
	                    IConnectorDescriptor conn = ac.getActivityOutputMap().get(aid).get(outputIndex);
	                    return ac.getConnectorRecordDescriptorMap().get(conn.getConnectorId());
	                }

	                @Override
	                public RecordDescriptor getInputRecordDescriptor(ActivityId aid, int inputIndex) {
	                    ActivityCluster ac = acg.getActivityMap().get(aid);
	                    IConnectorDescriptor conn = ac.getActivityInputMap().get(aid).get(inputIndex);
	                    return ac.getConnectorRecordDescriptorMap().get(conn.getConnectorId());
	                }
	            };
				int partition = 0;
				int nPartitions = 0;
				IOperatorNodePushable op = sact.createPushRuntime(ctx, recordDescProvider, partition, nPartitions);
				//IOperatorNodePushable op = ac.createPushRuntime(ctx, recordDescProvider, partition, nPartitions);
				IFrameWriter frameWriter = op.getInputFrameWriter(0);
			}
			
		}
		
		
		/*
		 * Implementation plan:
		 * … we can first get a IOperatorNodePushable by calling superActivity.createPushRuntime
[18:06:45] JArod Wen: then we should get the input frame writer from the IOperatorNodePushable, by calling IOperatorNodePushable.getInputFrameWriter(0)
[18:08:01] Keren  Ouaknine: sounds like a plan, once you have the frame, how do you process it via the chain of operators?
[18:08:09] JArod Wen: Then it would be similar to the Task.pushFrames() function, to push the frames from the input partition collectors to the IOperatorNodePushable
[18:08:10] JArod Wen: yes
[18:08:30] JArod Wen: once you push the frame into the IOperatorNodePushable, it will be pushed automatically to other operators in the downstream

		 * */		
		/***********CONTINUE FROM HERE********************/
		//Follow up who is calling JobSpecificationActivityClusterGraphGeneratorFactory and how you get from there to the open call on the operator
		//there will be a bunch of interfaces that you will have to re-implement or use existing implementations
		//in any case, you want to execute the jobSpec and see it running
		//You will also have to re-implement the PlanCompiler so you can actually have input and output adapters to mappers and reducers but that's for later..  
		
	}

	public static void main(String[] args) throws AlgebricksException {

		// schema: <nameEmployee, string>
		Pair<String, Type> p = new Pair("newEmployee", CharArrayType.INSTANCE);
		ArrayList<Pair<String, Type>> schemaInput = new ArrayList<Pair<String, Type>>();
		schemaInput.add(p);
		TestSchema schema = new TestSchema(schemaInput);

		//Write operator requires the schema of the fields to write, etc
		IDataSink dataSink = new TestDataSink("localhost:output1K.txt"); 

		//This section creates an expression for dataScan to convert the input into variables according to a schema.
		List<Mutable<ILogicalExpression>> expressions = new ArrayList<Mutable<ILogicalExpression>>();
		LogicalVariable variable = new LogicalVariable(1);
		VariableReferenceExpression varExpr1 = new VariableReferenceExpression(variable);
		VariableReferenceExpression varExpr2 = new VariableReferenceExpression(variable);

		//you need to provide two variables cause your finfo compares 
		expressions.add(new MutableObject<ILogicalExpression>(varExpr1));		
		expressions.add(new MutableObject<ILogicalExpression>(varExpr2));
		

		//Hard-coding the where function clause and corresponding arity + giving it the scanned data (expressions)
		FunctionIdentifier finfo = new FunctionIdentifier("algebricks", "eq", 2);
		TestFunction function = new TestFunction(finfo);
		ScalarFunctionCallExpression scalarExp = new ScalarFunctionCallExpression(function, expressions);
		MutableObject<ILogicalExpression> exprCondition = new MutableObject<ILogicalExpression>(scalarExp);


		List<Pair<String, Type>> fieldsSchema = schema.getSchema();
		List<LogicalVariable> variables = new ArrayList<LogicalVariable>();
		List<Object> types = new ArrayList<Object>();
		
		Pair<String, Type> pair = fieldsSchema.get(0);
		LogicalVariable v = variable;
		variables.add(v);
		types.add(pair.second);


		//define the input file name, and the schema - required for scanning in data?
		TestDataSource dataSource = new TestDataSource("localhost:inputKeren.txt", types.toArray());

		// roots contain a write->select->data, creating these operators below.
		// [A query can have multiple roots - not the case here. still there's a list structure]
		List<Mutable<ILogicalOperator>> roots = new ArrayList<Mutable<ILogicalOperator>>();

		//We are using the same expressions list for the writeOperator and to build the condition - makes sense?
		WriteOperator write = new WriteOperator(expressions, dataSink);


		DataSourceScanOperator dataScan = new DataSourceScanOperator(variables, dataSource);
		SelectOperator select = new SelectOperator(exprCondition);


		// chaining them: scan to select and select to write
		dataScan.getInputs().add(new MutableObject<ILogicalOperator>(new EmptyTupleSourceOperator()));
		select.getInputs().add(new MutableObject<ILogicalOperator>(dataScan));
		write.getInputs().add(new MutableObject<ILogicalOperator>(select));
		roots.add(new MutableObject<ILogicalOperator>(write));


		//IOperatorSchema outerPlanSchema = null;

		// Two steps:
		// 1. create a compiler object which performs the optimization on the plan

		HeuristicCompilerFactoryBuilder builder = new HeuristicCompilerFactoryBuilder();
		builder.setLogicalRewrites(buildDefaultLogicalRewrites());
		builder.setPhysicalRewrites(buildDefaultPhysicalRewrites());
		
		builder.setExpressionRuntimeProvider(new LogicalExpressionJobGenToExpressionRuntimeProviderAdapter(
				new PigletExpressionJobGen()));
        builder.setSerializerDeserializerProvider(new ISerializerDeserializerProvider() {
            @SuppressWarnings("unchecked")
            @Override
            public ISerializerDeserializer getSerializerDeserializer(Object type) throws AlgebricksException {
                return null;
            }
        });
        builder.setTypeTraitProvider(new ITypeTraitProvider() {
            public ITypeTraits getTypeTrait(Object type) {
                return null;
            }
        });
        builder.setPrinterProvider(PigletPrinterFactoryProvider.INSTANCE);
        builder.setExpressionRuntimeProvider(new LogicalExpressionJobGenToExpressionRuntimeProviderAdapter(
                new PigletExpressionJobGen()));
        builder.setExpressionTypeComputer(new IExpressionTypeComputer() {
            @Override
            public Object getType(ILogicalExpression expr, IMetadataProvider<?, ?> metadataProvider,
                    IVariableTypeEnvironment env) throws AlgebricksException {
                return null;
            }
        });
		final ICompilerFactory cFactory = builder.create();

		// 2. create the logical plan based on the roots above, to pass to createCompiler
		ALogicalPlanImpl plan = new ALogicalPlanImpl(roots);

		IMetadataProvider metaData = new IMetadataProvider() {

			@Override //TODO:
			public IDataSource findDataSource(Object id)
					throws AlgebricksException {
				// TODO Auto-generated method stub
				return null;
			}



			@Override
			public boolean scannerOperatorIsLeaf(IDataSource dataSource) {
				// TODO Auto-generated method stub
				return true;
				//return false;
			}

			@Override
			public Pair getWriteFileRuntime(IDataSink sink, int[] printColumns,
					IPrinterFactory[] printerFactories,
					RecordDescriptor inputDesc) throws AlgebricksException {
				TestDataSink ds = (TestDataSink) sink;
		        FileSplit[] fileSplits = ds.getFileSplits();
		        String[] locations = new String[fileSplits.length];
		        for (int i = 0; i < fileSplits.length; ++i) {
		            locations[i] = fileSplits[i].getNodeName();
		        }
		        IPushRuntimeFactory prf = new SinkWriterRuntimeFactory(printColumns, printerFactories, fileSplits[0]
		                .getLocalFile().getFile(), PrinterBasedWriterFactory.INSTANCE, inputDesc);
		        AlgebricksAbsolutePartitionConstraint constraint = new AlgebricksAbsolutePartitionConstraint(locations);
		        return new Pair<IPushRuntimeFactory, AlgebricksPartitionConstraint>(prf, constraint);
			}

			@Override //TODO:
			public Pair getWriteResultRuntime(IDataSource dataSource,
					IOperatorSchema propagatedSchema, List keys,
					LogicalVariable payLoadVar, JobGenContext context,
					JobSpecification jobSpec) throws AlgebricksException {
				// TODO Auto-generated method stub
				return null;
			}


			@Override
			public Pair getIndexInsertRuntime(IDataSourceIndex dataSource,
					IOperatorSchema propagatedSchema,
					IOperatorSchema[] inputSchemas,
					IVariableTypeEnvironment typeEnv, List primaryKeys,
					List secondaryKeys, ILogicalExpression filterExpr,
					RecordDescriptor recordDesc, JobGenContext context,
					JobSpecification spec) throws AlgebricksException {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Pair getIndexDeleteRuntime(IDataSourceIndex dataSource,
					IOperatorSchema propagatedSchema,
					IOperatorSchema[] inputSchemas,
					IVariableTypeEnvironment typeEnv, List primaryKeys,
					List secondaryKeys, ILogicalExpression filterExpr,
					RecordDescriptor recordDesc, JobGenContext context,
					JobSpecification spec) throws AlgebricksException {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public IDataSourceIndex findDataSourceIndex(Object indexId,
					Object dataSourceId) throws AlgebricksException {
				// TODO Auto-generated method stub
				return null;
			}

			@Override //TODO:
			public IFunctionInfo lookupFunction(FunctionIdentifier fid) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Pair getScannerRuntime(IDataSource dataSource,
					List scanVariables, List projectVariables,
					boolean projectPushed, IOperatorSchema opSchema,
					IVariableTypeEnvironment typeEnv, JobGenContext context,
					JobSpecification jobSpec, Object implConfig)
							throws AlgebricksException {
				Object[] colTypes = dataSource.getSchemaTypes();
				IValueParserFactory[] vpfs = new IValueParserFactory[colTypes.length];
				ISerializerDeserializer[] serDesers = new ISerializerDeserializer[colTypes.length];

				for (int i = 0; i < colTypes.length; ++i) {
					Type colType = (Type) colTypes[i];
					IValueParserFactory vpf;
					ISerializerDeserializer serDeser;
					switch (colType.getTag()) {
					case INTEGER:
						vpf = IntegerParserFactory.INSTANCE;
						serDeser = IntegerSerializerDeserializer.INSTANCE;
						break;

					case CHAR_ARRAY:
						vpf = UTF8StringParserFactory.INSTANCE;
						serDeser = UTF8StringSerializerDeserializer.INSTANCE;
						break;

					case FLOAT:
						vpf = FloatParserFactory.INSTANCE;
						serDeser = FloatSerializerDeserializer.INSTANCE;
						break;

					default:
						throw new UnsupportedOperationException();
					}
					vpfs[i] = vpf;
					serDesers[i] = serDeser;
				}

				ITupleParserFactory tpf = new DelimitedDataTupleParserFactory(vpfs, ',');
				RecordDescriptor rDesc = new RecordDescriptor(serDesers);

				//specify the file location
				FileSplit[] fs = ((TestDataSource)dataSource).getFileSplits();
				String[] locations = new String[fs.length];
				for (int i = 0; i < fs.length; ++i) {
					locations[i] = fs[i].getNodeName();
				}
				//just a wrapper for these splits
				IFileSplitProvider fsp = new ConstantFileSplitProvider(fs);

				IOperatorDescriptor scanner = new FileScanOperatorDescriptor(jobSpec, fsp, tpf, rDesc);
				AlgebricksAbsolutePartitionConstraint constraint = new AlgebricksAbsolutePartitionConstraint(locations);
				return new Pair<IOperatorDescriptor, AlgebricksPartitionConstraint>(scanner, constraint);

			}

			@Override 
			public Pair getResultHandleRuntime(IDataSink sink,
					int[] printColumns, IPrinterFactory[] printerFactories,
					RecordDescriptor inputDesc, boolean ordered,
					JobSpecification spec) throws AlgebricksException {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Pair getInsertRuntime(IDataSource dataSource,
					IOperatorSchema propagatedSchema,
					IVariableTypeEnvironment typeEnv, List keys,
					LogicalVariable payLoadVar, RecordDescriptor recordDesc,
					JobGenContext context, JobSpecification jobSpec)
							throws AlgebricksException {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Pair getDeleteRuntime(IDataSource dataSource,
					IOperatorSchema propagatedSchema,
					IVariableTypeEnvironment typeEnv, List keys,
					LogicalVariable payLoadVar, RecordDescriptor recordDesc,
					JobGenContext context, JobSpecification jobSpec)
							throws AlgebricksException {
				// TODO Auto-generated method stub
				return null;
			}

		};




		ICompiler compiler = cFactory.createCompiler(plan, metaData, 1); // KIS
		try {
			System.out.println("Optimization of the plan");
			compiler.optimize();
		} catch (AlgebricksException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} // this calls does the rewrites of the rules on the plan, which rules
		

		System.out.println(plan.toString());
		// up to now we built a physical optimized plan, now to the runtime part of it.

		JobSpecification jobSpec = compiler.createJob(null, null);
		
		try {
			executeJobSpecification(jobSpec, 32768);
		} catch (HyracksException e) {
			System.out.println("Runtime plan failed to run!");
			e.printStackTrace();
		}
		
	}//main
	
}//TestSelect


