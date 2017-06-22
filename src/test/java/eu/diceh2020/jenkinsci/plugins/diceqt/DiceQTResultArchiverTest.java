package eu.diceh2020.jenkinsci.plugins.diceqt;

/*-
 * #%L
 * DICE Jenkins plug-in
 * %%
 * Copyright (C) 2016 XLAB d.o.o.
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.*;
import hudson.model.TaskListener;
import hudson.util.RunList;
import junit.framework.Assert;
import junit.framework.TestCase;
import eu.diceh2020.jenkinsci.plugins.diceqt.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class DiceQTResultArchiverTest {

	private Launcher launcher = mock(Launcher.class);
	private PrintStream logger = mock(PrintStream.class);
	private BuildListener listener = mock(BuildListener.class);
	private FreeStyleBuild build = mock(FreeStyleBuild.class);
	private FreeStyleProject job = mock(FreeStyleProject.class);
	private Run<?, ?> run = mock(Run.class);
	private List<FreeStyleBuild> buildList = new ArrayList<FreeStyleBuild>();

	@Before
	public void setUp() throws Exception {
		when(listener.getLogger()).thenReturn(logger);
		when(job.getBuilds()).thenReturn(RunList.fromRuns(buildList));
		when(build.getParent()).thenReturn(job);
	}
	
	@Test
	public void testPerformSingleMetric() throws Exception {
		
		String fileContent = "{'latency': 123.55}";
		Hashtable<String, Number>  expected = new Hashtable<String, Number>();
		expected.put("latency", 123.55);
		
		FilePath resultsFilePath = Utilities.createTemporaryFile(
				fileContent);
		FilePath workspace = resultsFilePath.getParent();
		
		DiceQTResultArchiver archiver = this.getArchiver(
				new DiceQTResultArchiver(resultsFilePath.getName()));
		
		archiver.perform((Run<?, ?>) run, workspace, launcher, listener);
		
		ArgumentCaptor<Action> actionArgument = ArgumentCaptor.forClass(
				Action.class);
		verify(run, times(2)).addAction(actionArgument.capture());
		List<Action> allArguments = actionArgument.getAllValues();
		assertEquals(DiceQTResultBuildAction.class,
				allArguments.get(0).getClass());
		assertEquals(DiceQTResultProjectAction.class,
				allArguments.get(1).getClass());
		assertEquals(expected, ((DiceQTResultBuildAction)allArguments.get(0))
				.getMetrics());
	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void testPerformNoResults() throws Exception {
		FilePath nonExistentFilePath = new FilePath(new File(
				"/tmp/non-existent.ne.json"));
		String metricsFileName = nonExistentFilePath.getName();
		if (nonExistentFilePath.exists())
			throw new Exception("A problem with test: "
					+ "the non-existing file should not exist");
		
		FilePath workspace = nonExistentFilePath.getParent();
		
		DiceQTResultArchiver archiver = this.getArchiver(
				new DiceQTResultArchiver(metricsFileName));

		thrown.expect(FileNotFoundException.class);
		thrown.expectMessage("metrics file");
		thrown.expectMessage(metricsFileName);
		archiver.perform((Run<?, ?>) run, workspace, launcher, listener);
	}
	
	@Test
	public void testPerformEmptyResults() throws Exception {
		String fileContent = "";
		Hashtable<String, Number>  expected = new Hashtable<String, Number>();
		
		FilePath resultsFilePath = Utilities.createTemporaryFile(
				fileContent);
		FilePath workspace = resultsFilePath.getParent();
		
		DiceQTResultArchiver archiver = this.getArchiver(
				new DiceQTResultArchiver(resultsFilePath.getName()));
		
		archiver.perform((Run<?, ?>) run, workspace, launcher, listener);
		
		ArgumentCaptor<Action> actionArgument = ArgumentCaptor.forClass(
				Action.class);
		verify(run, times(2)).addAction(actionArgument.capture());
		List<Action> allArguments = actionArgument.getAllValues();
		assertEquals(DiceQTResultBuildAction.class, allArguments.get(0).getClass());
		assertEquals(DiceQTResultProjectAction.class, allArguments.get(1).getClass());

		assertEquals(expected, ((DiceQTResultBuildAction)allArguments.get(0)).getMetrics());
	}
	
	/**
	 * Test obtaining the merged metric when there are no
	 * builds. 
	 * @throws Exception
	 */
	@Test
	public void testGetMetricNamesEmpty() throws Exception {
		DiceQTResultProjectAction projectAction = 
				new DiceQTResultProjectAction(job);
		ArrayList<String> metricNames = projectAction.getMetricNames();
		
		assertTrue(metricNames.isEmpty());
	}
	
	/**
	 * Test obtaining the merged metric names. Each build
	 * produced all the metrics. 
	 * @throws Exception
	 */
	@Test
	public void testGetMetricNamesFull() throws Exception {
		this.factoryCreateBuildsFull();
		TreeSet<String> expectedMetricNames = new TreeSet<String>();
		expectedMetricNames.add("latency");
		expectedMetricNames.add("throughput");
		expectedMetricNames.add("duration");
		
		DiceQTResultProjectAction projectAction = 
				new DiceQTResultProjectAction(job);
		ArrayList<String> metricNames = projectAction.getMetricNames();
		
		TreeSet<String> metricNamesSet = new TreeSet<String>(metricNames);
		assertEquals(expectedMetricNames, metricNamesSet);
	}
	
	/**
	 * Test obtaining the merged metric names. Builds have
	 * a mixed set of metric names.
	 * @throws Exception
	 */
	@Test
	public void testGetMetricNamesMixed() throws Exception {
		buildList.add(createBuild(job, 1, Result.SUCCESS, false,
				"{'latency': 123.55}"));
		buildList.add(createBuild(job, 2, Result.SUCCESS, false,
				"{'latency': 329.12}"));
		buildList.add(createBuild(job, 3, Result.SUCCESS, false,
				"{'throughput': 205.1}"));
		buildList.add(createBuild(job, 4, Result.SUCCESS, false,
				"{'duration': 4.9}"));

		TreeSet<String> expectedMetricNames = new TreeSet<String>();
		expectedMetricNames.add("latency");
		expectedMetricNames.add("throughput");
		expectedMetricNames.add("duration");
		
		DiceQTResultProjectAction projectAction = 
				new DiceQTResultProjectAction(job);
		ArrayList<String> metricNames = projectAction.getMetricNames();
		
		TreeSet<String> metricNamesSet = new TreeSet<String>(metricNames);
		assertEquals(expectedMetricNames, metricNamesSet);
	}
		
	/**
	 * Test obtaining a history (a series) of the available
	 * metrics.
	 * @throws Exception
	 */
	@Test
	public void testGetMetricHistoryFull() throws Exception {
		this.factoryCreateBuildsFull();
		Hashtable<String, List<Number>> expectedHistory = 
				new Hashtable<String, List<Number>>();
		List<Number> latencyHistory = new ArrayList<Number>();
		expectedHistory.put("latency", latencyHistory);
		latencyHistory.add(123.55);
		latencyHistory.add(329.12);
		latencyHistory.add(211.38);
		latencyHistory.add(310.31);
		latencyHistory.add(291.44);
		List<Number> throughputHistory = new ArrayList<Number>();
		expectedHistory.put("throughput", throughputHistory);
		throughputHistory.add(200.2);
		throughputHistory.add(199.8);
		throughputHistory.add(205.1);
		throughputHistory.add(183.4);
		throughputHistory.add(193.1);
		List<Number> durationHistory = new ArrayList<Number>();
		expectedHistory.put("duration", durationHistory);
		durationHistory.add(5.1);
		durationHistory.add(4.2);
		durationHistory.add(6.3);
		durationHistory.add(4.9);
		durationHistory.add(5.2);
		
		DiceQTResultProjectAction projectAction = 
				new DiceQTResultProjectAction(job);

		MetricsHistoryTest.assertEqualsDelta(
				expectedHistory.get("throughput"),
				projectAction.getMetricHistory("throughput"));
		MetricsHistoryTest.assertEqualsDelta(
				expectedHistory.get("duration"),
				projectAction.getMetricHistory("duration"));
		MetricsHistoryTest.assertEqualsDelta(
				expectedHistory.get("latency"),
				projectAction.getMetricHistory("latency"));
	}
	
	/**
	 * Tests the ability to obtain an iterable set of metric history
	 * rows useful for displaying tables. Each row starts with the build
	 * number. The rest of the entries in the row correspond to the
	 * value of the metric measured in the build.
	 */
	@Test
	public void testGetReportTableRows() throws Exception {
		this.factoryCreateBuildsFull();
		
		ArrayList<String> expectedMetricNames =
				new ArrayList<String>();
		expectedMetricNames.add("throughput");
		expectedMetricNames.add("latency");
		expectedMetricNames.add("duration");
		
		ArrayList<ArrayList<Number>> expectedTable = 
				new ArrayList<ArrayList<Number>>();
		
		ArrayList<Number> row;
		row = new ArrayList<Number>(); expectedTable.add(row);
		row.add(1); row.add(200.2); row.add(123.55); row.add(5.1);
		row = new ArrayList<Number>(); expectedTable.add(row);
		row.add(2); row.add(199.8); row.add(329.12); row.add(4.2);
		row = new ArrayList<Number>(); expectedTable.add(row);
		row.add(3); row.add(205.1); row.add(211.38); row.add(6.3);
		row = new ArrayList<Number>(); expectedTable.add(row);
		row.add(4); row.add(183.4); row.add(310.31); row.add(4.9);
		row = new ArrayList<Number>(); expectedTable.add(row);
		row.add(5); row.add(193.1); row.add(291.44); row.add(5.2);
		
		DiceQTResultProjectAction projectAction = 
				new DiceQTResultProjectAction(job);
		
		ArrayList<String> metricNames =
				projectAction.getMetricNames();
		ArrayList<ArrayList<Number>> table =
				projectAction.getMetricHistoryTable();
		
		assertEquals(expectedMetricNames, metricNames);
		assertEquals(expectedTable.size(), table.size());
		for (int r = 0; r < table.size(); r++) {
			MetricsHistoryTest.assertEqualsDelta(
					expectedTable.get(r), table.get(r));
		}
	}
	
	/***
	 * Creates a list of builds, each containing a full list
	 * of metrics.
	 * @throws Exception
	 */
	private void factoryCreateBuildsFull() throws Exception {
		buildList.add(createBuild(job, 1, Result.SUCCESS, false,
				"{'latency': 123.55, 'throughput': 200.2, 'duration': 5.1}"));
		buildList.add(createBuild(job, 2, Result.SUCCESS, false,
				"{'latency': 329.12, 'throughput': 199.8, 'duration': 4.2}"));
		buildList.add(createBuild(job, 3, Result.SUCCESS, false,
				"{'latency': 211.38, 'throughput': 205.1, 'duration': 6.3}"));
		buildList.add(createBuild(job, 4, Result.SUCCESS, false,
				"{'latency': 310.31, 'throughput': 183.4, 'duration': 4.9}"));
		buildList.add(createBuild(job, 5, Result.SUCCESS, false,
				"{'latency': 291.44, 'throughput': 193.1, 'duration': 5.2}"));
	}
	
	private FreeStyleBuild createBuild(FreeStyleProject project,
			int buildNum, Result result, boolean building,
			String fileContent)
					throws Exception {
		FreeStyleBuild build = spy(new FreeStyleBuild(project));
		Hashtable<String, Number> metrics = 
				MetricsJsonParser.parse(fileContent);
		
		DiceQTResultBuildAction action = new DiceQTResultBuildAction(
				build, metrics);
		
		when(build.getResult()).thenReturn(result);
		when(build.isBuilding()).thenReturn(building);
		when(build.getAction(DiceQTResultBuildAction.class))
			.thenReturn(action);
		when(build.getNumber()).thenReturn(buildNum);
		when(build.getId()).thenReturn(String.format("%d", buildNum));
		
		return build;
	}
	
	private DiceQTResultArchiver getArchiver(DiceQTResultArchiver archiver)
			throws Exception {
		
		DiceQTResultArchiver spy = spy(archiver);
		
		return spy;
	}
}
