package com.meluzin.tibcobwutils.build.deploymentbuild.tasks;

import java.util.Set;

public interface OnFinishAction {
	public void onFinish(Set<Task> actions);
} 
