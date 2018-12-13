package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.util.Optional;
import java.util.Set;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;

public interface BuildContext {
	public Optional<String> getJobNumber();

	public NodeBuilder getChangeLogXml();

	public Set<Deployment> getChangedDeployments();

	public Set<Library> getChangedLibraries();
}
