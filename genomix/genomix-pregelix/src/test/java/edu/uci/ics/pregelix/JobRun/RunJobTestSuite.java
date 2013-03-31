package edu.uci.ics.pregelix.JobRun;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.mapred.JobConf;

import edu.uci.ics.pregelix.core.jobgen.clusterconfig.ClusterConfig;
import edu.uci.ics.pregelix.core.util.PregelixHyracksIntegrationUtil;

public class RunJobTestSuite extends TestSuite{
	
	private static final Logger LOGGER = Logger.getLogger(RunJobTestSuite.class
			.getName());

	private static final String ACTUAL_RESULT_DIR = "actual";
	private static final String EXPECTED_RESULT_DIR = "src/test/resources/expected";
	private static final String PATH_TO_HADOOP_CONF = "src/test/resources/hadoop/conf";
	private static final String PATH_TO_CLUSTER_STORE = "src/test/resources/cluster/stores.properties";
	private static final String PATH_TO_CLUSTER_PROPERTIES = "src/test/resources/cluster/cluster.properties";
	private static final String PATH_TO_JOBS = "src/test/resources/jobs/";
	private static final String PATH_TO_IGNORE = "src/test/resources/ignore.txt";
	private static final String PATH_TO_ONLY = "src/test/resources/only.txt";
	private static final String FILE_EXTENSION_OF_RESULTS = "result";

	private static final String DATA_PATH = "data/webmap/part-1-out-200000";//sequenceFileMergeTest
	private static final String HDFS_PATH = "/webmap/";
	
	private static final String HYRACKS_APP_NAME = "pregelix";
	private static final String HADOOP_CONF_PATH = ACTUAL_RESULT_DIR
			+ File.separator + "conf.xml";
	private MiniDFSCluster dfsCluster;

	private JobConf conf = new JobConf();
	private int numberOfNC = 2;
	
	public void setUp() throws Exception {
		ClusterConfig.setStorePath(PATH_TO_CLUSTER_STORE);
		ClusterConfig.setClusterPropertiesPath(PATH_TO_CLUSTER_PROPERTIES);
		cleanupStores();
		PregelixHyracksIntegrationUtil.init("src/test/resources/topology.xml");
		PregelixHyracksIntegrationUtil.createApp(HYRACKS_APP_NAME);
		LOGGER.info("Hyracks mini-cluster started");
		FileUtils.forceMkdir(new File(ACTUAL_RESULT_DIR));
		FileUtils.cleanDirectory(new File(ACTUAL_RESULT_DIR));
		startHDFS();
	}

	private void cleanupStores() throws IOException {
		FileUtils.forceMkdir(new File("teststore"));
		FileUtils.forceMkdir(new File("build"));
		FileUtils.cleanDirectory(new File("teststore"));
		FileUtils.cleanDirectory(new File("build"));
	}
	
	private void startHDFS() throws IOException {
		conf.addResource(new Path(PATH_TO_HADOOP_CONF + "/core-site.xml"));
		conf.addResource(new Path(PATH_TO_HADOOP_CONF + "/mapred-site.xml"));
		conf.addResource(new Path(PATH_TO_HADOOP_CONF + "/hdfs-site.xml"));
		FileSystem lfs = FileSystem.getLocal(new Configuration());
		lfs.delete(new Path("build"), true);
		System.setProperty("hadoop.log.dir", "logs");
		dfsCluster = new MiniDFSCluster(conf, numberOfNC, true, null);
		FileSystem dfs = FileSystem.get(conf);
		Path src = new Path(DATA_PATH);
		Path dest = new Path(HDFS_PATH);
		dfs.mkdirs(dest);
		dfs.copyFromLocalFile(src, dest);

		DataOutputStream confOutput = new DataOutputStream(
				new FileOutputStream(new File(HADOOP_CONF_PATH)));
		conf.writeXml(confOutput);
		confOutput.flush();
		confOutput.close();
	}
	
	/**
	 * cleanup hdfs cluster
	 */
	private void cleanupHDFS() throws Exception {
		dfsCluster.shutdown();
	}

	public void tearDown() throws Exception {
		PregelixHyracksIntegrationUtil.destroyApp(HYRACKS_APP_NAME);
		PregelixHyracksIntegrationUtil.deinit();
		LOGGER.info("Hyracks mini-cluster shut down");
		cleanupHDFS();
	}
	
	public static Test suite() throws Exception {
		List<String> ignores = getFileList(PATH_TO_IGNORE);
		List<String> onlys = getFileList(PATH_TO_ONLY);
		File testData = new File(PATH_TO_JOBS);
		File[] queries = testData.listFiles();
		RunJobTestSuite testSuite = new RunJobTestSuite();
		testSuite.setUp();
		boolean onlyEnabled = false;

		if (onlys.size() > 0) {
			onlyEnabled = true;
		}
		for (File qFile : queries) {
			if (isInList(ignores, qFile.getName()))
				continue;

			if (qFile.isFile()) {
				if (onlyEnabled && !isInList(onlys, qFile.getName())) {
					continue;
				} else {
					String resultFileName = ACTUAL_RESULT_DIR + File.separator
							+ jobExtToResExt(qFile.getName());
					String expectedFileName = EXPECTED_RESULT_DIR
							+ File.separator + jobExtToResExt(qFile.getName());
					testSuite.addTest(new RunJobTestCase(HADOOP_CONF_PATH,
							qFile.getName(),
							qFile.getAbsolutePath().toString(), resultFileName,
							expectedFileName));
				}
			}
		}
		return testSuite;
	}
	
	/**
	 * Runs the tests and collects their result in a TestResult.
	 */
	@Override
	public void run(TestResult result) {
		OutputStreamWriter writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream("test/time",true));
		} catch (FileNotFoundException e1) { e1.printStackTrace();}
		long startTime = System.currentTimeMillis();
		
		try {
			int testCount = countTestCases();
			for (int i = 0; i < testCount; i++) {
				// cleanupStores();
				Test each = this.testAt(i);
				if (result.shouldStop())
					break;
				runTest(each, result);
			}
			tearDown();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		
		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		System.out.println(totalTime);
		try {
			writer.write("Time: " + totalTime);
			writer.close();
		} catch (IOException e) { // TODO Auto-generated catch block 
			e.printStackTrace();} 
	}

	protected static List<String> getFileList(String ignorePath)
			throws FileNotFoundException, IOException {
		BufferedReader reader = new BufferedReader(new FileReader(ignorePath));
		String s = null;
		List<String> ignores = new ArrayList<String>();
		while ((s = reader.readLine()) != null) {
			ignores.add(s);
		}
		reader.close();
		return ignores;
	}

	private static String jobExtToResExt(String fname) {
		int dot = fname.lastIndexOf('.');
		return fname.substring(0, dot + 1) + FILE_EXTENSION_OF_RESULTS;
	}

	private static boolean isInList(List<String> onlys, String name) {
		for (String only : onlys)
			if (name.indexOf(only) >= 0)
				return true;
		return false;
	}

}
