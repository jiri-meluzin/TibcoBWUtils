package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.nio.file.Path;
import java.util.List;

public class Deployment {
	private Path path;
	private List<Library> dependencies;
	private List<Library> declaredLibraries;
	private List<Path> declaredArchives;
	
	public Deployment(Path path, List<Library> dependencies, List<Library> declaredLibraries, List<Path> declaredArchives) {
		super();
		this.path = path;
		this.dependencies = dependencies;
		this.declaredLibraries = declaredLibraries;
		this.declaredArchives = declaredArchives;
	}
	public List<Library> getDependencies() {
		return dependencies;
	}
	public List<Library> getDeclaredLibraries() {
		return declaredLibraries;
	}
	public List<Path> getDeclaredArchives() {
		return declaredArchives;
	}
	public Path getPath() {
		return path;
	}
	
	public String getName() {
		return path.getFileName().toString();			
	}
	@Override
	public String toString() {
		return getName();
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dependencies == null) ? 0 : dependencies.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((declaredLibraries == null) ? 0 : declaredLibraries.hashCode());
		result = prime * result + ((declaredArchives == null) ? 0 : declaredArchives.hashCode());
		
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
		Deployment other = (Deployment) obj;
		if (dependencies == null) {
			if (other.dependencies != null)
				return false;
		} else if (!dependencies.equals(other.dependencies))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (declaredLibraries == null) {
			if (other.declaredLibraries != null)
				return false;
		} else if (!declaredLibraries.equals(other.declaredLibraries))
			return false;
		if (declaredArchives == null) {
			if (other.declaredArchives != null)
				return false;
		} else if (!declaredArchives.equals(other.declaredArchives))
			return false;
		return true;
	}
	
	
	
}