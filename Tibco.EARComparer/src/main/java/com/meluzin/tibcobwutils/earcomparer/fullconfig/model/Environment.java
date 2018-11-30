package com.meluzin.tibcobwutils.earcomparer.fullconfig.model;

import java.nio.file.Path;

public class Environment extends Value<String> {
	private Path path;
	public Environment(Path path) {
		super(path.getFileName().toString());
		this.path = path;
	}
	
	public Path getPath() {
		return path;
	}
	

}
