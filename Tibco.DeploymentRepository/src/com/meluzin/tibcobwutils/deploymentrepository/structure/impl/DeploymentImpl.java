package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.Log;
import com.meluzin.stream.StreamUtils;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Config;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Deployment;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSource;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSourceType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Library;

public class DeploymentImpl implements Deployment {
	private Path deploymentPath;
	private Config config;
	private String name;
	private List<Library> libraries;
	private Set<Item> removedItems = new HashSet<>();
	public DeploymentImpl(Path deploymentPath, Config config) {
		if (!isValidPath(deploymentPath)) {
			throw new IllegalArgumentException(deploymentPath + " is not a valid deployment path");
		}
		this.deploymentPath = deploymentPath;
		this.config = config;
		load();
	}
	@Override
	public ItemSourceType getType() {
		return ItemSourceType.Deployment;
	}
	private void load() {
		NodeBuilder rootFolderInfo = getRootFolderInfo(getRootFolderPath(deploymentPath));
		this.name = rootFolderInfo.getAttribute("name");
		this.libraries = loadDesignTimeLibs();
	}
	private boolean isValidPath(Path deploymentPath) {
		if (deploymentPath == null) throw new IllegalArgumentException("deploymentPath is null");
		if (!deploymentPath.toFile().isDirectory()) {
			System.err.println(deploymentPath + " is not a directory");
			return false;
		}
		Path rootFolderPath = getRootFolderPath(deploymentPath);
		if (!rootFolderPath.toFile().isFile()) {
			System.err.println(deploymentPath + " does not contain .folder file");
			return false;			
		}
		NodeBuilder rootFolderInfo = getRootFolderInfo(rootFolderPath);
		if (rootFolderInfo == null) {
			System.err.println(rootFolderPath + " does not contain ae.rootfolder resourceType");
			return false;
		}		
		return true;
	}
	private Path getRootFolderPath(Path deploymentPath) {
		return deploymentPath.resolve(".folder");
	}
	private NodeBuilder getRootFolderInfo(Path rootFolderPath) {
		XmlBuilderFactory fac = new XmlBuilderFactory();
		NodeBuilder folder = fac.loadFromFile(rootFolderPath);
		return folder.searchFirst(true, n -> n.hasAttribute("resourceType") && "ae.rootfolder".equals(n.getAttribute("resourceType")));
	}
	public Set<Item> getRemovedItems() {
		return removedItems;
	}
	public Path removeItem(Item itemToRemove) {
		if (itemToRemove.isFolder()) {
			itemToRemove.getChildren().forEach(i -> removeItem(itemToRemove));
		}
		return removeItem(itemToRemove.getParent(), itemToRemove.getName(), itemToRemove.isFolder());
	}
	public Path removeItem(Item parent, String name, boolean isFolder) {
		Path itemAbsolutePath = getItemAbsolutePath(parent, name);
		
		File file = itemAbsolutePath.toFile();
		if (file.exists()) {
			boolean delete = file.delete();
			Log.get().info("removed file ("+delete+") " + itemAbsolutePath);
		} else {
			Log.get().info("cannot remove non-existant file " + itemAbsolutePath);
		}
		return itemAbsolutePath;
	}
	public Path createItem(Item parent, String name, boolean isFolder) {
		Path relativePath = getItemRelativePath(parent, name);
		Path p = getItemAbsolutePath(relativePath);
		if (isFolder) {
			if (!p.toFile().mkdirs()) {
				//throw new RuntimeException("Could not create directory, directory already exists (" + p + ")");
			}
			Log.get().info("new dir " + p);
		}
		else {
			try {				
				if (!p.toFile().createNewFile()) {
					//throw new RuntimeException("Could not create file, file already exists (" + p + ")");				
				}
				Log.get().info("new file " + p);
			} catch (IOException e) {
				throw new RuntimeException("Could not create file (" + p + ")", e);
			}
		}		
		return relativePath;
	}
	protected Path getItemAbsolutePath(Path relativePath) {
		return deploymentPath.resolve(relativePath);
	}
	protected Path getItemAbsolutePath(Item parent, String name) {
		Path relativePath = getItemRelativePath(parent, name);
		return getItemAbsolutePath(relativePath);
	}
	protected Path getItemRelativePath(Item parent, String name) {
		Path newItemPath = (parent == null ? Paths.get("/") : parent.getPath()).resolve(name);
		Path relativePath = Paths.get(newItemPath.toString().substring(1)); // remove starting \
		return relativePath;
	}
	@Override
	public String getName() {
		return name;
	}

	@Override
	public Item getRootFolder() {
		return new DeploymentItemImpl(deploymentPath, null);
	}

	@Override
	public Path getAbsolutePath() {
		return deploymentPath.toAbsolutePath();
	}

	@Override
	public List<Library> getLibraries() {
		return libraries;
	}
	private List<Library> loadDesignTimeLibs() {
		Path designTimeLibsPath = deploymentPath.resolve(".designtimelibs");
		if (!designTimeLibsPath.toFile().exists()) return new ArrayList<>();
		try(Stream<String> designtimelibsLines = Files.lines(designTimeLibsPath)) {

			List<String> aliases = designtimelibsLines.
                filter(s -> !s.startsWith("#")).
                map(s -> s.replaceAll("\\\\", "").split("=")).
                sorted((parts1, parts2) -> Integer.parseInt(parts1[0]) - Integer.parseInt(parts2[0])).
                map(parts -> parts[1]).collect(Collectors.toList());
			List<String> missingAliases = aliases.stream().
                filter(alias -> config.getAliasByName(alias) == null).collect(Collectors.toList());
			if (!missingAliases.isEmpty()) 
				throw new RuntimeException("Could not resolve following libraries: " + missingAliases + "\n" + "Please check " + config.getConfigPath());
			return 
					aliases.stream().
	                map(alias -> new LibraryImpl(config.getAliasByName(alias))).
	                collect(Collectors.toList());
		} catch (Exception e) {
			throw new RuntimeException("Cannot load .designtimelibs file ("+designTimeLibsPath+" using config " + config.getConfigPath() + ")", e);
		}	
	}
	public class DeploymentItemImpl implements Item {
		private Path itemPath;
		private DeploymentItemImpl parent;
		private List<DeploymentItemImpl> children = new ArrayList<>();
		public DeploymentItemImpl(Path itemPath, DeploymentItemImpl parent) {
			this.itemPath = itemPath;
			this.parent = parent;
			if (itemPath.toFile().isDirectory()) 
			this.children =  new FileSearcher().iterateFiles(itemPath, "glob:**/*", "glob:**/*.{svn,folder}", false).map(p -> new DeploymentItemImpl(p, DeploymentItemImpl.this)).collect(Collectors.toList());
		}
		@Override
		public String getName() {
			return parent == null ? "/" : itemPath.getFileName().toString();
		}

		@Override
		public List<Item> getChildren() {
			return Collections.unmodifiableList(children);
		}
		@Override
		public Item getParent() {
			return parent;
		}
		
		@Override
		public boolean hasContent() {
			return itemPath.toFile().isFile();
		}
		@Override
		public InputStream getContent() {
			if (hasContent()) {
				return StreamUtils.readFile(itemPath);	
			} else {
				throw new RuntimeException("Item does not have any content (" + itemPath + ")");
			}
		}
		@Override
		public OutputStream setContent() {
			if (hasContent()) {
				try {
					return new FileOutputStream(itemPath.toFile());
				} catch (FileNotFoundException e) {
					throw new RuntimeException("Could not find file for set content (" + itemPath + ")");
				}	
			} else {
				throw new RuntimeException("Item does not have any content (" + itemPath + ")");
			}
		}
		@Override
		public boolean isFolder() {
			return itemPath.toFile().isDirectory();
		}
		@Override
		public boolean isRemovedItem() {
			return getRemovedItems().contains(this);
		}
		@Override
		public void removeItem() {
			children.remove(this);
			getRemovedItems().add(this);
		}
		@Override
		public ItemSource getItemSource() {
			return DeploymentImpl.this;
		}
		@Override
		public Path getPath() {
			return Paths.get("/").resolve(((DeploymentItemImpl)getRoot()).itemPath.relativize(itemPath).normalize());
		}
		
		@Override
		public boolean isChanged() {
			return false;
		}
		@Override
		public void save() {
			throw new RuntimeException("Not supported method");
		}
	}
}
