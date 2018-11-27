package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import com.meluzin.functional.Log;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSource;

public class InMemoryChangesItem implements Item {
	private Item originalItem;
	private boolean changed = false;
	private String name;
	private boolean isFolder;
	private byte[] content = new byte[0];
	private List<Item> children = new ArrayList<>();
	private InMemoryChangesItem parent;
	private Path path;
	private InMemoryChangesImpl inMemoryChangesImpl;
	public InMemoryChangesItem(String newItemName, boolean isFolder, InMemoryChangesItem parent, InMemoryChangesImpl inMemoryChangesImpl) {
		this.parent = parent;
		this.changed = true;
		this.name = newItemName;
		this.isFolder = isFolder;
		this.path = parent.getPath().resolve(newItemName);
		this.inMemoryChangesImpl = inMemoryChangesImpl;
	}
	public InMemoryChangesItem(Item originalItem, InMemoryChangesItem parent, InMemoryChangesImpl inMemoryChangesImpl) {
		this.originalItem = originalItem;
		this.parent = parent;
		this.changed = originalItem == null;
		this.inMemoryChangesImpl = inMemoryChangesImpl;
	}
	InMemoryChangesImpl getInMemoryChangesImpl() {
		return inMemoryChangesImpl;
	}
	public boolean isChanged() {
		return changed;
	}
	public void modify() {
		if (!changed) {
			Log.get().info("modifying item: " + getPath());
			name = originalItem.getName();
			isFolder = originalItem.isFolder();
			path = originalItem.getPath();
			if (isFolder) {
				children.addAll(getChildren());
			}
			//children = isFolder ? getChildren() : children;
			if (!isFolder) {
				try (InputStream stream = originalItem.getContent()) {
					content = IOUtils.toByteArray(stream);
				} catch (IOException e) {
					throw new RuntimeException("Cannot copy content from original item (path = " + path + ")", e);
				}
			}
			if (parent != null) parent.modify();
			changed = true;
		}			
	}
	@Override
	public String getName() {
		return isChanged() ? name : originalItem.getName();
	}

	@Override
	public boolean hasContent() {
		return isChanged() ? !isFolder() : originalItem.hasContent();
	}

	@Override
	public InputStream getContent() {
		return isChanged() ? new ByteArrayInputStream(content) : originalItem.getContent();
	}

	@Override
	public OutputStream setContent() {			
		return new ByteArrayOutputStream() {
			@Override
			public void close() throws IOException {					
				super.close();			
				modify();		
				content = toByteArray();
			}
		};
	}

	@Override
	public List<Item> getChildren() {
		return isChanged() ? children : originalItem.getChildren().stream().map(item -> new InMemoryChangesItem(item, this, inMemoryChangesImpl)).collect(Collectors.toList());
	}

	@Override
	public boolean isFolder() {
		return isChanged() ? isFolder : originalItem.isFolder();
	}

	@Override
	public InMemoryChangesItem getParent() {
		return parent;
	}

	@Override
	public ItemSource getItemSource() {
		return isChanged() ? inMemoryChangesImpl : originalItem.getItemSource();
	}

	@Override
	public Path getPath() {
		return isChanged() ? path : originalItem.getPath();
	}
	
	public void save() {
		if (isChanged()) {
			Path p = inMemoryChangesImpl.getDeployment().createItem(parent, getName(), isFolder);
			if (hasContent()) {
				try (FileOutputStream stream = new FileOutputStream(inMemoryChangesImpl.getDeployment().getAbsolutePath().resolve(p).toFile())) {
					stream.write(content);
					Log.get().info("File stored: " + p);
				} catch (IOException e) {
					throw new RuntimeException("Cannot save item (" + p + ")", e);
				}
			}
			//getChildren().forEach(item -> ((InMemoryChangesItem)item).save());
		}
	}
	@Override
	public String toString() {
		return getName() + " " + isChanged();
	}
}