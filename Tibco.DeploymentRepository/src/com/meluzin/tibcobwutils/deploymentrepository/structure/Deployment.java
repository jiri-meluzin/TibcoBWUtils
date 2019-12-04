package com.meluzin.tibcobwutils.deploymentrepository.structure;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface Deployment extends ItemSource {
	public List<Library> getLibraries();
	public Set<Item> getRemovedItems();
	public Path removeItem(Item itemToRemove);
	public Path removeItem(Item parent, String name, boolean isFolder);
}
