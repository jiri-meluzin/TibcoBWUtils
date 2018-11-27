package com.meluzin.tibcobwutils.deploymentrepository.structure;

public interface Library extends ItemSource {
	public default String getAlias() {
		return getName();
	}
}
