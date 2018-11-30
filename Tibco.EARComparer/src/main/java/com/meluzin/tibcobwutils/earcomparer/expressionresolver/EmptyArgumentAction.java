package com.meluzin.tibcobwutils.earcomparer.expressionresolver;

import java.util.Map;

import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class EmptyArgumentAction implements ArgumentAction {
	private boolean available = false;
	@Override
	public void run(ArgumentParser parser, Argument arg, Map<String, Object> attrs, String flag, Object value)
			throws ArgumentParserException {
	}

	@Override
	public void onAttach(Argument arg) {
	}

	@Override
	public boolean consumeArgument() {
		available = true;
		return false;
	}
	public boolean isAvailable() {
		return available;
	}
}