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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Hashtable;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;

import eu.diceh2020.jenkinsci.plugins.diceqt.*;

/***
 * The {@code DiceQTResultArchiver} implements collecting of the data placed in
 * the workspace by a Quality Testing tool. It also defines a configuration
 * parameter to be set in the post-build job step and used with each build within
 * the job, pointing to where the quality testing execution's output file will be
 * stored within the workspace.
 * 
 * The class extends {@code Recorder}, which is a suitable choice for collecting
 * job execution's results after the fact. It also implements
 * {@code SimpleBuildStep}, which lets us access the workspace's contents.
 * 
 * The {@code DiceQTResultArchiver} stores the build result representation as
 * an instance of the {@link DiceQTResultBuildAction}.
 * @author matej.artac (at) xlab.si
 *
 */
public class DiceQTResultArchiver extends Recorder implements SimpleBuildStep {
	
	/**
	 * Configuration parameter containing the path to the results file
	 * (e.g., a JSON file).
	 */
	private final String pathToResults;
	
	/**
	 * Getter used in the {@code config.jelly}.
	 */
	public String getPathToResults() {
		return this.pathToResults;
	}
	
	@DataBoundConstructor
	public DiceQTResultArchiver(String pathToResults) {
		this.pathToResults = pathToResults;
	}

	/**
	 * Mandatory for this type of build step.
	 */
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}
	
	public static void archiveResults(Run<?, ?> build, FilePath workspace,
			TaskListener listener, String pathToResults)
					throws InterruptedException, IOException {
		PrintStream logger = listener.getLogger();
		logger.println(String.format(
				"Collecting the quality metrics data from %s.",
				pathToResults));
		if (build == null) {
			logger.println("Run is null.");
		} else {
			logger.println(String.format("class: %s, build status usr: %s",
					build.getClass().getName(), build.getBuildStatusUrl()));
		}

		// compose a FilePath to point to results file: a combination
		// of workspace and the configuration parameter of the plugin
		FilePath resultsFilePath = new FilePath(workspace,
				pathToResults);

		Hashtable<String, Number> metrics = MetricsJsonParser
				.parse(resultsFilePath);

		if (metrics == null) {
			throw new FileNotFoundException(String.format(
					"The metrics file %s not found. The program "
					+ "called by the job didn't output any results?",
					pathToResults));
		}

		Enumeration<String> emetrics = metrics.keys();
		if (emetrics.hasMoreElements()) {
			logger.println("Obtained performance metrics:");
			while (emetrics.hasMoreElements()) {
				String metric = emetrics.nextElement();
				logger.println(String.format(" - %s: %f",
						metric, metrics.get(metric)));
			}
		} else {
			logger.println("Obtained an EMPTY performance metrics file.");
		}

		// Save the results with the job
		DiceQTResultBuildAction action = build.getAction(DiceQTResultBuildAction.class);
		boolean appending;
		if (action == null) {
			action = new DiceQTResultBuildAction(build, metrics);
			appending = false;
		} else {
			action.setMetrics(metrics);
			appending = true;
		}
		
		if (appending) {
			build.save();
		} else {
			build.addAction(action);
		}

		// Also save the project action;
		DiceQTResultProjectAction projectAction = build.getAction(
				DiceQTResultProjectAction.class);
		if (projectAction == null) {
			projectAction = new DiceQTResultProjectAction(
					build.getParent());
			build.addAction(projectAction);
		}
	}

	/**
	 * This method gets called in Jenkins after a build.
	 */
	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {

		archiveResults(run, workspace, listener, this.pathToResults);
	}

	/**
	 * Fetches the descriptor for Jenkins to display configuration.
	 */
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}
	
    /**
     * Descriptor for {@link DiceQTResultArchiver}. Used as a singleton. 
     * The class is marked as public so that it can be accessed from views.
     * <p/>
     * This class provides a link with the
     * <tt>src/main/resources/eu.diceh2020.jenkinsci.plugins.diceqt.DiceQTResultArchiver/*.jelly</tt>
     * to generate the actual HTML contents in the configuration views.
     * It needs to be decorated with {@code Extension}.
     */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public String getDisplayName() {
			return LocalMessages.getMessage(
					LocalMessages.ARCHIVER_DESCRIPTOR_DISPLAY_NAME);
		}
	}
}
