package com.meluzin.tibcobwutils.build.deploymentbuild.tasks;

public interface OnErrorAction {
	public void onException(Task task, Throwable ex);
}
