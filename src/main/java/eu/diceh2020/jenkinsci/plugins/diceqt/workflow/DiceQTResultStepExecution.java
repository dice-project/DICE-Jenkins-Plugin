package eu.diceh2020.jenkinsci.plugins.diceqt.workflow;

import java.io.PrintStream;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import eu.diceh2020.jenkinsci.plugins.diceqt.DiceQTResultArchiver;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Implements the execution of the DICEQualityCheck.
 * @author matej.artac@xlab.si
 *
 */
public class DiceQTResultStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

	private static final long serialVersionUID = -2900018672800475708L;

	@StepContextParameter
	private transient TaskListener listener;

	@StepContextParameter
	private transient FilePath ws;

	@StepContextParameter
	private transient Run<?, ?> build;

	@Inject
	private transient DiceQTResultStep step;

	@Override
	protected Void run() throws Exception {

		final String pathToResults = step.getPathToResults();
		PrintStream logger = listener.getLogger();
		logger.println(String.format("Hello from 'run'. pathToResults: %s",
				pathToResults));
		DiceQTResultArchiver.archiveResults(build, ws,
				listener, pathToResults);

		return null;
	}

}
