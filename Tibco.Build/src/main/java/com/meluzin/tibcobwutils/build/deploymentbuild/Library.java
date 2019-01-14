package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.nio.file.Path;
import java.util.Optional;

public final class Library {
	private Optional<Path> outputPath;
	private Optional<Path> sourcePath;
	private Optional<String> alias;
	private String name;
	
	public Library(Optional<Path> outputPath, Optional<Path> sourcePath, Optional<String> alias, String name) {
		super();
		this.outputPath = outputPath;
		this.sourcePath = sourcePath;
		this.alias = alias;
		this.name = name;
	}
	public String getName() {
		return name;
	}
	public Optional<Path> getSourcePath() {
		return sourcePath;
	}
	
	public Optional<String> getAlias() {
		return alias;
	}
	public Optional<Path> getOutputPath() {
		return outputPath;
	}
	
	@Override
	public String toString() {
		return getName();
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((alias == null) ? 0 : alias.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((outputPath == null) ? 0 : outputPath.hashCode());
		result = prime * result + ((sourcePath == null) ? 0 : sourcePath.hashCode());
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
		Library other = (Library) obj;
		if (alias == null) {
			if (other.alias != null)
				return false;
		} else if (!alias.equals(other.alias))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (outputPath == null) {
			if (other.outputPath != null)
				return false;
		} else if (!outputPath.equals(other.outputPath))
			return false;
		if (sourcePath == null) {
			if (other.sourcePath != null)
				return false;
		} else if (!sourcePath.equals(other.sourcePath))
			return false;
		return true;
	}
	
	
	
	
}