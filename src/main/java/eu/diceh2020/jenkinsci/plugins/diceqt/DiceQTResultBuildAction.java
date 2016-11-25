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

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerProxy;

import hudson.model.*;
import hudson.util.StreamTaskListener;

public class DiceQTResultBuildAction implements Action, StaplerProxy {
	
	// Stores the build results
	private Hashtable<String, Number> metrics = null;

	private final AbstractBuild<?, ?> build;
	private transient WeakReference<DiceQTBuildResult> diceQTResult;
	
	private transient static final Logger logger = 
			Logger.getLogger(DiceQTResultBuildAction.class.getName());

	public DiceQTResultBuildAction(AbstractBuild<?, ?> build, 
			Hashtable<String, Number> metrics) {
		this.build = build;
		this.metrics = Utilities.clone(metrics);
	}
	
	public AbstractBuild<?, ?> getBuild() {
		return this.build;
	}

	@Override
	public String getIconFileName() {
		return "orange-square.png";
	}

	@Override
	public String getDisplayName() {
		return "DICE build metrics";
	}

	@Override
	public String getUrlName() {
		return "build-metrics";
	}

	public Hashtable<String, Number> getMetrics() {
		return Utilities.clone(metrics);
	}

	public void setMetrics(Hashtable<String, Number> metrics) {
		this.metrics = Utilities.clone(metrics);
	}

	@Override
	public DiceQTBuildResult getTarget() {
		return getDiceQTResult();
	}
	
	public DiceQTBuildResult getDiceQTResult() {
		DiceQTBuildResult result = null;
		WeakReference<DiceQTBuildResult> wr = this.diceQTResult;
		if (wr != null) {
			result = wr.get();
			if (result != null)
				return result;
		}
		
		try {
			result = new DiceQTBuildResult(this,
					StreamTaskListener.fromStdout());
		} catch (IOException e) {logger.log(Level.SEVERE,
				"Error creating new DiceQTResultHistory()", e);
		}
		this.diceQTResult = new WeakReference<DiceQTBuildResult>(
				result);
		return result;
		
	}
	
	public void setDiceQTResultHistory(
			WeakReference<DiceQTBuildResult> diceQTResultHistory) {
		this.diceQTResult = diceQTResultHistory;
	}
}
