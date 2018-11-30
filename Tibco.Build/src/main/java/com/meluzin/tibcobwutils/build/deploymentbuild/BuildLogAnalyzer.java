package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.util.List;

import com.meluzin.functional.Lists;

public class BuildLogAnalyzer {
	
	private static final List<String> IGNORE_LIST = Lists.asList(
			"InstanceId defined in project: The variable InstanceId is defined in the project. It must only be defined at run-time.",
			"Errors were detected in the project. The archive was not built",
			"Aborting."
			);

	public boolean isCodeValidation(BinExecutor errorLog) {
		if (errorLog == null) return false;
		
		return errorLog.getExec().contains("buildear");		
	}
	public boolean isConfigValidation(BinExecutor errorLog) {
		if (errorLog == null) return false;
		
		return errorLog.getExec().contains("validate-config");		
	}
	public BinExecutor getCodeValidation(List<BinExecutor> errorLog) {
		if (errorLog == null) return null;
		
		return errorLog.stream().filter(b -> isCodeValidation(b)).findAny().get() ;		
	}
	public boolean containsCodeValidationErrors(List<BinExecutor> errorLog) {
		BinExecutor b = getCodeValidation(errorLog);
		return b.getStdError() != null && Lists.asList(b.getStdError().split("\n")).stream().anyMatch(line -> !IGNORE_LIST.contains(line));		
	}
	public BinExecutor getConfigValidation(List<BinExecutor> errorLog) {
		if (errorLog == null) return null;
		
		return errorLog.stream().filter(b -> isConfigValidation(b)).findAny().get() ;		
	}
	public boolean containsConfigValidationErrors(List<BinExecutor> errorLog) {
		BinExecutor b = getConfigValidation(errorLog);
		return b.getStdError() != null && b.getStdError().length() > 0;		
	}
}
