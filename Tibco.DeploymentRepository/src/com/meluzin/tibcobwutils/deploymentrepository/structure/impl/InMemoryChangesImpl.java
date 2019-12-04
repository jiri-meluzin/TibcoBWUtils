package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.meluzin.tibcobwutils.deploymentrepository.structure.Deployment;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSourceType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Library;

public class InMemoryChangesImpl implements Deployment {
	private DeploymentImpl deployment;
	private InMemoryChangesItem root;
	private Set<InMemoryChangesItem> removedItems = new HashSet<>();
	public InMemoryChangesImpl(DeploymentImpl deployment) {
		this.deployment = deployment;
		this.root = new InMemoryChangesItem(deployment.getRootFolder(), null, this);
	}
	@Override
	public String getName() {
		return deployment.getName()/* + "-InMemory"*/;
	}
	@Override
	public ItemSourceType getType() {
		return ItemSourceType.Memory;
	}

	@Override
	public Item getRootFolder() {
		return root;
	}

	@Override
	public Path getAbsolutePath() {
		return deployment.getAbsolutePath();
	}

	@Override
	public List<Library> getLibraries() {
		return deployment.getLibraries();
	}
	public Item createItem(Item parent, String name, boolean isFolder) {
		InMemoryChangesItem changesParent = (InMemoryChangesItem)parent;
		InMemoryChangesItem newItem = new InMemoryChangesItem(name, isFolder, changesParent, this);		
		changesParent.modify();
		changesParent.getChildren().add(newItem);
		return newItem;
	}
	public Path removeItem(Item itemToBeRemoved) {
		InMemoryChangesItem imcItem = (InMemoryChangesItem)itemToBeRemoved;
		removedItems.add(imcItem);
		InMemoryChangesItem changesParent = (InMemoryChangesItem)itemToBeRemoved.getParent();	
		changesParent.modify();
		changesParent.getChildren().remove(imcItem);
		return itemToBeRemoved.getPath();
	}
	@Override
	public Set<Item> getRemovedItems() {
		return Collections.unmodifiableSet(removedItems);
	}
	@Override
	public Path removeItem(Item parent, String name, boolean isFolder) {
		return deployment.removeItem(parent, name, isFolder);
	}
	public void save() {
		root.save();
	}
	DeploymentImpl getDeployment() {
		return deployment;
	}
}
