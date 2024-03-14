package com.meluzin.tibcobwutils.deploymentrepository.structure;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderSAXFactory;
import com.meluzin.functional.Log;

public interface Item {
	public String getName();
	public boolean hasContent();
	public InputStream getContent();
	public default NodeBuilder loadAsXml() {
		try {
			return XmlBuilderSAXFactory.getSingleton().parseDocument(getContent());
		} catch (RuntimeException ex) {
			throw new RuntimeException("Cannot load XML from " + getItemSource().getAbsolutePath() + "@" + getPath(), ex);
		}
	}
	public default Item updateContent(NodeBuilder xml) {
		try (OutputStream stream = setContent()) {
			XmlBuilderSAXFactory.getSingleton().renderNode(xml, stream);
		} catch (IOException e) {
			throw new RuntimeException("Could not update content (" + getPath() + ")", e);
		}
		return this;
	}
	public OutputStream setContent();
	public List<Item> getChildren();
	public default boolean hasChild(String name) {
		return getChild(name).isPresent();
	}
	public default Optional<Item> getChild(String name) {
		List<Item> items = getChildren().stream().filter(item -> name.equals(item.getName())).collect(Collectors.toList());
		if (items.size() == 0) return Optional.empty();
		if (items.size() > 1) {
			Log.get().info(items.toString());
		}
		return Optional.of(items.get(0));
	}
	public boolean isFolder();
	public Item getParent();
	public default boolean isRoot() {
		return getParent() == null;
	};
	public default Item getRoot() {
		Item current = this;
		while (!current.isRoot()) {
			current = current.getParent();
		}
		return current;
	};

	public void removeItem();
	public boolean isRemovedItem();
	public ItemSource getItemSource();
	public Path getPath();
	public static String getDeploymentReference(Path path) {
		return Paths.get("/").resolve(path).toString().replace("\\", "/");
	}
	public default String getDeploymentReference() {
		return getDeploymentReference(getPath());
	}
	public default ItemType getItemType() {
		return ItemType.fromItem(this);
	}
	public boolean isChanged();
	public void save();
}
