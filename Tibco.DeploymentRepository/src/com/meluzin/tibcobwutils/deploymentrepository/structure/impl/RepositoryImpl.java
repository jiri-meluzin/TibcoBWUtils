package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.SchemaLoader;
import com.meluzin.fluentxml.xml.builder.SchemaReference;
import com.meluzin.fluentxml.xml.builder.SchemaRepository;
import com.meluzin.functional.Lists;
import com.meluzin.functional.Log;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Config;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Deployment;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariables;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSource;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSourceType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Repository;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.RepositoryImpl.RepositoryItem.ComparableItem;

public class RepositoryImpl implements Repository {
	// private DeploymentImpl deployment;
	private InMemoryChangesImpl inMemoryChanges;
	private RepositoryItem root;
	private Path deploymentPath;
	private Config config;
	private GlobalVariables globalVariables;
	private DeploymentImpl deployment;

	public RepositoryImpl(Path deploymentPath, Config config) {
		this.config = config;
		this.deploymentPath = deploymentPath;
		load();
	}

	private void load() {
		deployment = new DeploymentImpl(deploymentPath, config);
		this.inMemoryChanges = new InMemoryChangesImpl(deployment);
		this.root = new RepositoryItem(inMemoryChanges);
		loadAllGlobalVariables();
	}

	private void loadAllGlobalVariables() {
		GlobalVariables vars = getRootGlobalVariables();
		List<GlobalVariables> allVars = Lists.asList();
		Queue<GlobalVariables> toProcess = new LinkedList<>();
		toProcess.add(vars);
		while (!toProcess.isEmpty()) {
			vars = toProcess.poll();
			allVars.add(vars);
			toProcess.addAll(vars.getChildVariables());
		}
		allVars.stream().parallel().forEach(p -> p.getAllVariables());
	}

	@Override
	public Item getRoot() {
		return root;
	}

	@Override
	public Deployment getDeployment() {
		return inMemoryChanges;
	}

	@Override
	public List<Item> getAlternatives(Item item) {
		return ((RepositoryItem) item).alternatives;
		// return ((RepositoryItem)resolveItem(getRoot(),
		// item.getPath()).get()).alternatives;
	}

	@Override
	public Optional<Item> resolveItem(Item currentItem, Path relativePath) {
		Path folder = relativePath.getName(0);
		Optional<Item> child = Optional.empty();
		if ("..".equals(folder.toString())) {
			if (currentItem.isFolder())
				child = Optional.of(currentItem.getParent());
			else
				child = Optional.of(currentItem.getParent().getParent());
		} else
			child = (currentItem.isFolder() ? currentItem : currentItem.getParent()).getChild(folder.toString());
		if (relativePath.getNameCount() <= 1) {
			return child;
		} else if (child.isPresent()) {
			return resolveItem(child.get(), relativePath.subpath(1, relativePath.getNameCount()));
		} else {
			return child;
		}
	}

	@Override
	public Path getPath() {
		return deploymentPath;
	}

	public static void print(GlobalVariables vars) {
		Log.get().finest(vars.getPath() + "\n" + vars.getAllVariables().toString().replaceAll("],", "]\n"));
		vars.getChildVariables().forEach(v -> print(v));

	}

	@Override
	public SchemaRepository resolve(Item rootSchemaItem) {
		return new RepositorySchemaLoader().resolve(rootSchemaItem.getPath());
	}

	@Override
	public Item createItem(Item parent, String name, boolean isFolder) {
		RepositoryItem repositoryItemParent = (RepositoryItem) parent;
		Optional<Item> previousChild = repositoryItemParent.getChild(name);
		List<Item> alternatives = Arrays.asList();
		ItemSourceType type = ItemSourceType.Memory;
		if (previousChild.isPresent()) {
			Item item = previousChild.get();
			type = item.getItemSource().getType();
			if (type == ItemSourceType.Memory) {
				return item;
			}
			alternatives = ((RepositoryItem) item).alternatives;
		}
		Item createdItem = inMemoryChanges.createItem(repositoryItemParent.getCurrentItem(), name, isFolder);
		
		if (previousChild.isPresent()/* && type == ItemSourceType.Deployment */) {
			repositoryItemParent.children.remove(previousChild.get());
			try (OutputStream setContent = createdItem.setContent()){
				IOUtils.copy(previousChild.get().getContent(), setContent);
			} catch (IOException e) {
				throw new RuntimeException("Could not copy content from previous version of item to new item ("+createdItem.getDeploymentReference()+")", e);
			}
		}
		repositoryItemParent.children.add(new RepositoryItem(repositoryItemParent, createdItem, alternatives));
		repositoryItemParent.children.sort((a, b) -> {
			return new ComparableItem(a).compareTo(new ComparableItem(b));
		});
		return findItem(createdItem.getPath()).get();
	}


	@Override
	public void removeItem(Item itemToBeRemoved) {
		itemToBeRemoved.removeItem();
	}

	@Override
	public void save() {
		//deployment.getRemovedItems().forEach(i -> deployment.removeItem(i));
		inMemoryChanges.getRemovedItems().stream().sorted((a,b)->-a.getPath().compareTo(b.getPath())).forEach(i -> inMemoryChanges.removeItem(i.getParent(), i.getName(), i.isFolder()));
		getRootGlobalVariables().save();
		root.save();
	}
	@Override
	public boolean isChanged() {
		return root != null && hasChangedChild(root);
	}

	public boolean hasChangedChild(Item root) {
		return root.getChildren().stream().anyMatch(c -> c.isChanged() || hasChangedChild(c));
	}
	@Override
	public GlobalVariables getRootGlobalVariables() {
		if (globalVariables == null) {
			Item root2 = getRoot();
			Optional<Item> child = root2.getChild("defaultVars");
			Item defaultVars = child.orElseGet(() -> createItem("defaultVars", true));
			Item substvar = defaultVars.getChild("defaultVars.substvar").orElse(null);
			globalVariables = new GlobalVariablesImpl(substvar, defaultVars, this);
		}
		return globalVariables;
	}

	public class RepositorySchemaLoader extends SchemaLoader {
		private Item findItem(Path path) {
			Optional<Item> item = RepositoryImpl.this.findItem(path);
			if (!item.isPresent())
				throw new RuntimeException("Cannot find item (" + path + ")");
			return item.get();
		}

		@Override
		protected void resolve(SchemaReference schemaReference) {
			Path path = schemaReference.getRefPath();
			Item item = findItem(schemaReference.getSourcePath() != null
					? schemaReference.getSourcePath().getParent().resolve(path).normalize() : path);
			super.resolve(item.getPath());
		}

		@Override
		protected NodeBuilder loadXml(Path source) {
			Item item = findItem(source);
			return item.loadAsXml();
		}
	}

	public static class RepositoryItem implements Item {
		private List<Item> children;
		private Item currentItem;
		private RepositoryItem parent;
		private List<Item> alternatives;
		private boolean invalidChildren;

		public RepositoryItem(InMemoryChangesImpl deployment) {
			this.parent = null;
			this.currentItem = deployment.getRootFolder();
			loadChildren(Stream
					.concat(Stream.of(this.currentItem),
							deployment.getLibraries().stream().map(lib -> lib.getRootFolder()))
					.collect(Collectors.toList()));
		}

		public RepositoryItem(RepositoryItem parent, Item currentItem, List<Item> alternatives) {
			this.parent = parent;
			this.currentItem = currentItem;
			loadChildren(alternatives);
		}

		private void loadChildren(List<Item> alternatives) {
			this.alternatives = alternatives;
			Map<ComparableItem, List<Item>> childrenMap = alternatives.stream().filter(item -> item.isFolder())
					.map(item -> item.getChildren()).flatMap(children -> children.stream())
					.collect(Collectors.groupingBy(item -> new ComparableItem(item)));

			this.children = childrenMap.entrySet().stream()
					.sorted((entry1, entry2) -> entry1.getKey().compareTo(entry2.getKey()))
					.map(entry -> new RepositoryItem(this, entry.getValue().get(0), entry.getValue()))
					.collect(Collectors.toList());
			invalidChildren = false;
		}

		public static class ComparableItem implements Comparable<ComparableItem> {
			private Item item;

			public ComparableItem(Item item) {
				this.item = item;
			}

			@Override
			public int compareTo(ComparableItem o) {
				if (o.isFolder() && !isFolder())
					return 1;
				if (!o.isFolder() && isFolder())
					return -1;
				return getName().compareToIgnoreCase(o.getName());

			}

			public String getName() {
				return item.getName();
			}

			public boolean isFolder() {
				return item.isFolder();
			}

			public Item getItem() {
				return item;
			}

			@Override
			public int hashCode() {
				final int prime = 31;
				int result = 1;
				result = prime * result + (isFolder() ? 1231 : 1237);
				result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
				return result;
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				if (obj == null)
					return false;
				if (getClass() != obj.getClass())
					return false;
				ComparableItem other = (ComparableItem) obj;
				if (isFolder() != other.isFolder())
					return false;
				if (getName() == null) {
					if (other.getName() != null)
						return false;
				} else if (!getName().equals(other.getName()))
					return false;
				return true;
			}
		}

		@Override
		public String getName() {
			return currentItem.getName();
		}

		@Override
		public boolean hasContent() {
			return currentItem.hasContent();
		}

		@Override
		public InputStream getContent() {
			return currentItem.getContent();
		}

		@Override
		public List<Item> getChildren() {
			if (invalidChildren)
				loadChildren(alternatives);
			return children;
		}

		protected void invalidateChildren() {
			this.invalidChildren = true;
		}

		@Override
		public Item getParent() {
			return parent;
		}

		public void print(Path rootPath) {
			Path currentPath = rootPath.resolve(getName());
			Log.get().finest(currentPath + " ~ " + alternatives.stream()
					.map(item -> item.getItemSource().getAbsolutePath()).collect(Collectors.toList()));
			Log.get().finest(getPath() + " ~ " + alternatives.stream()
					.map(item -> item.getItemSource().getAbsolutePath()).collect(Collectors.toList()));
			getChildren().forEach(child -> ((RepositoryItem) child).print(currentPath));
		}
		@Override
		public void removeItem() {
			currentItem.removeItem();
			parent.children.remove(this);
		}
		@Override
		public boolean isRemovedItem() {
			return currentItem.isRemovedItem();
		}

		@Override
		public boolean isFolder() {
			return currentItem.isFolder();
		}

		@Override
		public ItemSource getItemSource() {
			return currentItem.getItemSource();
		}

		@Override
		public Path getPath() {
			return currentItem.getPath();
		}

		@Override
		public OutputStream setContent() {
			return currentItem.setContent();
		}

		@Override
		public String toString() {
			return getPath().toString() + " " + isChanged();
		}

		@Override
		public boolean isChanged() {
			return currentItem.isChanged();
		}

		@Override
		public void save() {
			if (currentItem.isChanged()) {
				currentItem.save();
				children.forEach(Item::save);
			}
		}

		public Item getCurrentItem() {
			return currentItem;
		}
	}
}
