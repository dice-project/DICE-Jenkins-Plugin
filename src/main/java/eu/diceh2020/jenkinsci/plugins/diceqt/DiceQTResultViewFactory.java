package eu.diceh2020.jenkinsci.plugins.diceqt;

import java.util.Collection;
import java.util.Collections;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Job;
import jenkins.model.TransientActionFactory;

@Extension
public class DiceQTResultViewFactory
		extends TransientActionFactory<AbstractItem> {

	@Override
	public Class<AbstractItem> type() {
		return AbstractItem.class;
	}

	@Override
	public Collection<? extends Action> createFor(AbstractItem target) {
		final DiceQTResultProjectAction projectAction = 
				new DiceQTResultProjectAction((Job<?, ?>)target);

		if (projectAction.hasMetrics())
			return Collections.singleton(projectAction);
		else
			return Collections.emptyList();
	}
}