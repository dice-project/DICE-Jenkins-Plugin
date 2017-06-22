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

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.CategoryDataset;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.*;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import hudson.util.RunList;
import jenkins.model.RunAction2;

/***
 * This class implements the view summarizing the whole project.
 * It charts the performances across the history of application's
 * builds.
 *
 * @author matej.artac@xlab.si
 *
 */
public class DiceQTResultProjectAction implements RunAction2 {

	private final transient Job<?, ?> project;


	/**
	 * Compatibility constructor. Prevents mockit from getting confused.
	 * @param project
	 */
	DiceQTResultProjectAction(final AbstractProject<?, ?> project) {
		this.project = project;
	}

	/**
	 * Constructor
	 * @param project
	 */
	DiceQTResultProjectAction(final Job<?, ?> project) {
		this.project = project;
	}
	
	@Override
	public String getIconFileName() {
		return "graph.gif";
	}

	@Override
	public String getDisplayName() {
		return LocalMessages.getMessage(
				LocalMessages.PROJECT_ACTION_DISPLAY_NAME);
	}

	@Override
	public String getUrlName() {
		return "dice-dashboard";
	}

	/**
	* Method necessary to get the side-panel included in the Jelly file
	* @return this {@link Job}
	*/
	public Job<?, ?> getProject() {
		return this.project;
	}
	
	/**
	 * Extracts a merged list of metric names used in all
	 * builds.
	 * @return
	 */
	public ArrayList<String> getMetricNames() {
		return getCurrentBuildHistory().getMetrics();
	}
	
	/**
	 * Get a time series for the the given metric.
	 * @param metricName The name of the metric to query
	 * the history of.
	 * @return A sequence of metric values.
	 */
	public ArrayList<Number> getMetricHistory(String metricName) {
		return getCurrentBuildHistory().getHistory(metricName);
	}

	/**
	 * Returns a transposed representation of the metric history useful
	 * for outputting tables with builds for rows and metrics for
	 * columns.
	 * 
	 * @return a table, i.e., a list of lists of numbers. The outer
	 * list contains rows in the table, and the inner list represents
	 * columns (cells) in the row. The first column is always a build
	 * number. The rest of the values are in the order of metrics as
	 * returned by the {@code getMetricNames()}.
	 */
	public ArrayList<ArrayList<Number>> getMetricHistoryTable() {
		ArrayList<ArrayList<Number>> retval =
				new ArrayList<ArrayList<Number>>();
		
		// initialise the table
		MetricsHistory history = this.getCurrentBuildHistory();
		ArrayList<Integer> buildNumbers = history.getBuildNumbers();
		for (Integer buildNo : buildNumbers) {
			ArrayList<Number> row = new ArrayList<Number>();
			row.add(buildNo);
			retval.add(row);
		}
		
		// append metric values to the rows, one metric at a time
		ArrayList<String> metricNames = this.getMetricNames();
		for (String metricName : metricNames) {
			ArrayList<Number> metricHistory = this.getMetricHistory(
					metricName);
			for (int r = 0; r < metricHistory.size(); r++) {
				Number value = metricHistory.get(r);
				retval.get(r)
					.add(value);
			}
		}
		
		return retval;
	}
	
	private MetricsHistory getCurrentBuildHistory () {
		final MetricsHistory history = new MetricsHistory(0);
		
		RunList<?> buildList = this.project.getBuilds();
		for (Run<?, ?> build : buildList) {
			DiceQTResultBuildAction action = build.getAction(
					DiceQTResultBuildAction.class);
			if (action == null)
				continue;
			
			Hashtable<String, Number> metrics =
					action.getDiceQTResult().getMetrics();
			
			history.appendMetrics(metrics, build.getNumber());
		}
		
		return history;
	}
	
	public void doDisplayGraph(final StaplerRequest request,
			final StaplerResponse response) throws IOException {
		
		final MetricsHistory history = this.getCurrentBuildHistory();
		
		final Graph graph = new DiceGraph() {

			@Override
			protected DataSetBuilder<String, Integer> createDataset() {
				DataSetBuilder<String, Integer> dataSetBuilder =
						new DataSetBuilder<String, Integer>();
				
				ArrayList<Integer> buildNumbers = history.getBuildNumbers();
				for (String metricName : history.getMetrics()) {
					int buildNumIndex = 0;
					for (Number val : history.getHistory(metricName)) {
						dataSetBuilder.add(val, metricName,
								buildNumbers.get(buildNumIndex));
						buildNumIndex++;
					}
				}
				
				return dataSetBuilder;
			}
		};
		
		graph.doPng(request, response);
	}
	
	private abstract class DiceGraph extends Graph {
		
		public DiceGraph() {
			super(-1, 400, 300); // timestamp not available yet
		}
		
		protected abstract DataSetBuilder<String, Integer> createDataset();
		
		public JFreeChart createGraph() {
			final CategoryDataset dataset = createDataset().build();
			String title = "Build Quality Testing History";
			
			final JFreeChart chart = ChartFactory.createLineChart(
					title,
					"build #",
					"metric units",
					dataset,
					PlotOrientation.VERTICAL,
					true, // legend?
					true, // tooltips?
					false // generate URLs?
					);
			chart.setBackgroundPaint(Color.LIGHT_GRAY);
			
			return chart;
		}
	}

	@Override
	public void onAttached(Run<?, ?> r) {
		System.err.println("Project Action attached");
		System.err.println(r);
	}

	@Override
	public void onLoad(Run<?, ?> r) {
		System.err.println("Project Action loaded");
		System.err.println(r);
	}
}
