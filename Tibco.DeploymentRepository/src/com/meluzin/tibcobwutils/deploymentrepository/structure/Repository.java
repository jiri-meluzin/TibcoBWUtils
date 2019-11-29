package com.meluzin.tibcobwutils.deploymentrepository.structure;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Predicate;

import com.meluzin.fluentxml.xml.builder.SchemaRepository;

public interface Repository {
	public Path getPath();
	public Item getRoot();
	public Deployment getDeployment();
	public static Predicate<Item> SEARCH_SHARED_HTTP = item -> item.getName().endsWith(".sharedhttp");
	public static Predicate<Item> SEARCH_HTTP_CONNECTION = SEARCH_SHARED_HTTP.and(item -> item.loadAsXml().searchFirst(true, n -> "useSsl".equals(n.getName()) && "true".equals(n.getTextContent())) == null);
	public static Predicate<Item> SEARCH_HTTPS_CONNECTION = SEARCH_SHARED_HTTP.and(item -> item.loadAsXml().searchFirst(true, n -> "useSsl".equals(n.getName()) && "true".equals(n.getTextContent())) != null);
	public default List<Item> findAll(Predicate<Item> filter) {
		return findAll(getRoot(), filter);
	}
	public default Optional<Item> findItem(Path relativePath) {
		return resolveItem(getRoot(), relativePath);
	}
	public default Optional<Item> findItem(String relativePath) {
		return findItem(Paths.get(relativePath));
	}
	public default List<Item> findAll(Item relativeItem, Predicate<Item> filter) {
		Queue<Item> toSearch = new LinkedList<>();
		List<Item> result = new ArrayList<>();
		toSearch.add(relativeItem);
		while (!toSearch.isEmpty()) {
			Item item = toSearch.poll();
			if (filter.test(item)) result.add(item);
			List<Item> children = item.getChildren();
			toSearch.addAll(children);
		}
		return result;		
	}
	public default List<Item> findAll(Path relativePath, Predicate<Item> filter) {
		Optional<Item> findItem = findItem(relativePath);
		if (findItem.isPresent()) {
			return findAll(findItem.get(), filter);
		}
		return Arrays.asList();
	}
	public Optional<Item> resolveItem(Item relativeTo, Path relativePath);
	public default Optional<Item> resolveItem(Item relativeTo, String relativePath) {
		return resolveItem(relativeTo, Paths.get(relativePath));
	}
	
	public SchemaRepository resolve(Item rootSchemaItem);
	public default Item createItem(String relativePath) {
		return createItem(Paths.get(relativePath), false);
	}
	public default Item createItem(Path relativePath) {
		return createItem(relativePath, false);
	}
	public default Item createItem(String relativePath, boolean isFolder) {
		return createItem(Paths.get(relativePath), isFolder);		
	}
	public default Item createItem(Path relativePath, boolean isFolder) {
		Item item = getRoot();
		for (int i = 0; i < relativePath.getNameCount() - 1; i++) {
			String name = relativePath.getName(i).toString();
			Optional<Item> child = item.getChild(name);
			if (child.isPresent() && child.get().getItemSource().getType() != ItemSourceType.Library) {
				item = child.get();
			}
			else {
				item = createItem(item, name, true);
			}
		}
		return createItem(item, relativePath.getName(relativePath.getNameCount() - 1).toString(), isFolder);
	}
	public default Item createItem(Item parent, String name) {
		return createItem(parent, name, false);
	}
	public Item createItem(Item parent, String name, boolean isFolder);
	public List<Item> getAlternatives(Item item);
	public GlobalVariables getRootGlobalVariables();
	public void save();
	public boolean isChanged();
}
