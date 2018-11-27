package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.meluzin.functional.Log;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Alias;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSource;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSourceType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Library;

public class LibraryImpl implements Library {
	private Alias alias;
	private LibraryItem root;
	public LibraryImpl(Alias alias) {
		if (alias == null) throw new IllegalArgumentException("Alias cannot be null (probably unknown alias)");
		this.alias = alias;
		load();
	}
	@Override
	public ItemSourceType getType() {
		return ItemSourceType.Library;
	}
	private void load() {
        try (ZipFile zipFile = new ZipFile(getAbsolutePath().toFile())) {
            List<ZipEntry> entries = Collections.list(zipFile.entries()).stream().map(z -> (ZipEntry)z).collect(Collectors.toList());
            this.root = new LibraryRootItem(getAbsolutePath(), entries);
		} catch (IOException e) {
			throw new RuntimeException("Cannot read library file ("+getAbsolutePath()+")", e);
		}        
		
	}
	@Override
	public String getName() {
		return alias.getTargetPath().getFileName().toString();
	}
	@Override
	public String getAlias() {
		return alias.getAliasName();
	}

	@Override
	public Item getRootFolder() {		  
        return root;
	}

	@Override
	public Path getAbsolutePath() {
		return alias.getTargetPath();
	}
	
	public class LibraryRootItem extends LibraryItem implements Item {
		private Path projlib;
		public LibraryRootItem(Path projlib, List<ZipEntry> entries) {
			super(null, projlib, Paths.get("/"), null);
			this.projlib = projlib;
			entries.forEach(entry -> {
				if (entry.getName().startsWith("/")) {
					addItem(Paths.get(entry.getName()), entry, this);
				}
				else {
					addItem(Paths.get("/").resolve(entry.getName()), entry, this);
				}
			});
		}
		private void addItem(Path relativePath, ZipEntry entry, LibraryItem parent) {
			if (parent.getPath().equals(relativePath.getParent())) {
				parent.getChildren().add(new LibraryItem(parent, projlib, relativePath, entry));
			}
			else {
				Optional<LibraryItem> parentChild = parent.getChildren().stream().map(child -> (LibraryItem)child).filter(child -> relativePath.startsWith(child.getPath())).findFirst();
				if (parentChild.isPresent()) {
					addItem(relativePath, entry, parentChild.get());
				}
				else {
					LibraryItem newParent = new LibraryItem(parent, projlib, parent.getPath().resolve(relativePath.getName(parent.getPath().getNameCount())) ,null);
					parent.children.add(newParent);
					addItem(relativePath, entry, newParent);
				}
			}
		}
		@Override
		public String getName() {
			return "/";
		}

		@Override
		public Item getParent() {
			return null;
		}
		
	}
	
	public class LibraryItem implements Item {
		private Item parent;
		private Path relativePath;
		private Path projlib;
		private ZipEntry zipEntry;
		private List<Item> children;
		public LibraryItem(Item parent, Path projlib, Path relativePath, ZipEntry zipEntry) {
			this.parent = parent;
			this.projlib = projlib;
			this.relativePath = relativePath;
			this.children = new ArrayList<>();
			this.zipEntry = zipEntry;
		}
		
		@Override
		public String getName() {
			return relativePath.getFileName().toString();
		}

		@Override
		public List<Item> getChildren() {
			return children;
		}
		

		@Override
		public Item getParent() {
			return parent;
		}
		protected void setParent(Item newParent) {
			this.parent = newParent;
		}
		@Override
		public String toString() {
			return relativePath.toString();
		}
		
		public Path getPath() {
			return relativePath;
		}
		@Override
		public boolean hasContent() {
			return zipEntry != null;
		}
		@Override
		public boolean isFolder() {
			return zipEntry == null || zipEntry.isDirectory();
		}
		@Override
		public InputStream getContent() {
			if (hasContent()) {
				byte[] data;
		        try (ZipFile zipFile = new ZipFile(projlib.toFile())) {
		        	data = IOUtils.toByteArray(zipFile.getInputStream(zipEntry));		        	
		        } catch (IOException e) {
					throw new RuntimeException("Failed to read content (" + this + ")", e);
				}
		        return new ByteArrayInputStream(data);
			}
			else {
				throw new RuntimeException("Item does not have any content (" + this + ")");
			}
		}
		@Override
		public OutputStream setContent() {
			throw new RuntimeException("Library cannot be updated! (" + this + ")");
		}
		public void printStructure() {
			Log.get().finest(projlib + " -> " + this);
			children.forEach(c -> ((LibraryItem)c).printStructure());
		}

		@Override
		public ItemSource getItemSource() {
			return LibraryImpl.this;
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
