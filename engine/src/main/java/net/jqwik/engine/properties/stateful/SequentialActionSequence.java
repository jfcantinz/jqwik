package net.jqwik.engine.properties.stateful;

import java.util.*;
import java.util.stream.*;

import org.opentest4j.*;

import net.jqwik.api.*;
import net.jqwik.api.stateful.*;
import net.jqwik.engine.support.*;

class SequentialActionSequence<M> implements ActionSequence<M> {

	protected ActionGenerator<M> actionGenerator;
	protected int intendedSize;
	protected final List<Action<M>> sequence = new ArrayList<>();
	private final List<Invariant<M>> invariants = new ArrayList<>();

	protected RunState runState = RunState.NOT_RUN;
	private M currentModel = null;

	SequentialActionSequence(ActionGenerator<M> actionGenerator, int intendedSize) {
		if (intendedSize < 1) {
			throw new IllegalArgumentException("The intended size of an ActionSequence must not be 0");
		}
		this.actionGenerator = actionGenerator;
		this.intendedSize = intendedSize;
	}

	@Override
	public synchronized List<Action<M>> runActions() {
		return sequence;
	}

	@Override
	public synchronized M run(M model) {
		if (runState != RunState.NOT_RUN) {
			return currentModel;
		}
		runState = RunState.RUNNING;
		currentModel = model;
		for (int i = 0; i < intendedSize; i++) {
			Action<M> action;
			try {
				action = actionGenerator.next(currentModel);
			} catch (NoSuchElementException nsee) {
				break;
			}
			sequence.add(action);
			try {
				currentModel = action.run(currentModel);
				checkInvariants();
			} catch (InvariantFailedError ife) {
				runState = RunState.FAILED;
				throw ife;
			} catch (Throwable t) {
				runState = RunState.FAILED;
				AssertionFailedError assertionFailedError = new AssertionFailedError(createErrorMessage("Run", t.getMessage()), t);
				assertionFailedError.setStackTrace(t.getStackTrace());
				throw assertionFailedError;
			}
		}
		if (sequence.isEmpty()) {
			throw new JqwikException("Could not generated a single action. At least 1 is required.");
		}
		runState = RunState.SUCCEEDED;
		return currentModel;
	}

	private void checkInvariants() {
		try {
			for (Invariant<M> invariant : invariants) {
				invariant.check(currentModel);
			}
		} catch (Throwable t) {
			throw new InvariantFailedError(createErrorMessage("Invariant", t.getMessage()), t);
		}
	}

	private String createErrorMessage(String name, String causeMessage) {
		String actionsString = sequence
			.stream() //
			.map(aTry -> "    " + aTry.toString()) //
			.collect(Collectors.joining(System.lineSeparator()));
		return String.format(
			"%s failed after following actions:%n%s%n  final currentModel: %s%n%s",
			name,
			actionsString,
			JqwikStringSupport.displayString(currentModel),
			causeMessage
		);
	}


	@Override
	public synchronized ActionSequence<M> withInvariant(Invariant<M> invariant) {
		invariants.add(invariant);
		return this;
	}

	@Override
	public RunState runState() {
		return runState;
	}

	@Override
	public synchronized M finalModel() {
		return currentModel;
	}

	@Override
	public String toString() {
		if (runState == RunState.NOT_RUN) {
			return String.format("ActionSequence[%s]: %s actions intended", runState.name(), intendedSize);
		}
		String actionsString = JqwikStringSupport.displayString(sequence);
		return String.format("ActionSequence[%s]: %s", runState.name(), actionsString);
	}
}
