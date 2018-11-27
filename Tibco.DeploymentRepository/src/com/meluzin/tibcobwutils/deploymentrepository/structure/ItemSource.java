package com.meluzin.tibcobwutils.deploymentrepository.structure;

import java.nio.file.Path;

public interface ItemSource {
	public String getName();
	public Item getRootFolder();
	public Path getAbsolutePath();
	public ItemSourceType getType();
}
