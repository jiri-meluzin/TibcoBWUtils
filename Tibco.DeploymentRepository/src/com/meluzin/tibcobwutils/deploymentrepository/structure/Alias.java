package com.meluzin.tibcobwutils.deploymentrepository.structure;

import java.nio.file.Path;

public interface Alias {
	public String getAliasName();
	public Path getTargetPath();
}
