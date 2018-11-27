package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.meluzin.functional.FileSearcher;
import com.meluzin.stream.StreamUtils;
import com.meluzin.tibcobwutils.deploymentrepository.structure.FileSystemSource;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSource;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSourceType;

public class FileSystemSourceImpl implements FileSystemSource {
	private Path rootPath;
	public FileSystemSourceImpl(Path rootPath) {
		this.rootPath = rootPath;
	}
	@Override
	public String getName() {
		return rootPath.toString();
	}

	@Override
	public Item getRootFolder() {
		return null;
	}

	@Override
	public Path getAbsolutePath() {
		return rootPath;
	}
	@Override
	public ItemSourceType getType() {
		return ItemSourceType.Deployment;
	}
	
	public class FileSystemFolder implements Item {
		private Path path;
		private Item parent;
		public FileSystemFolder(Path path, Item parent) {
			this.path = path;
		}
		@Override
		public String getName() {
			return path.getFileName().toString();
		}

		@Override
		public boolean hasContent() {
			return !isFolder();
		}

		@Override
		public InputStream getContent() {
			return StreamUtils.readFile(path);
		}
		@Override
		public OutputStream setContent() {
			if (hasContent()) {
				try {
					return new FileOutputStream(path.toFile());
				} catch (FileNotFoundException e) {
					throw new RuntimeException("Could not find file for set content (" + path + ")");
				}	
			} else {
				throw new RuntimeException("Item does not have any content (" + path + ")");
			}
		}

		@Override
		public List<Item> getChildren() {			
			return new FileSearcher().searchFiles(path, "glob:**/*", false).stream().map(p -> (Item)new FileSystemFolder(p, this)).collect(Collectors.toList());
		}

		@Override
		public boolean isFolder() {
			return path.toFile().isDirectory();
		}

		@Override
		public Item getParent() {
			return parent;
		}

		@Override
		public ItemSource getItemSource() {
			return FileSystemSourceImpl.this;
		}

		@Override
		public Path getPath() {
			return rootPath.relativize(path);
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
