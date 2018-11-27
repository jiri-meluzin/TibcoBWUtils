package com.meluzin.tibcobwutils.deploymentrepository.structure;

import java.util.List;

public interface Deployment extends ItemSource {
	public List<Library> getLibraries();
}
