package com.meluzin.tibcobwutils.deploymentrepository.structure;

import java.nio.file.Path;
import java.util.Collection;

public interface Config {
	public Collection<Alias> getAliases();
	public Alias getAliasByName(String name);
	public Path getConfigPath();
}
