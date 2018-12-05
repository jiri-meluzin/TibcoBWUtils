package com.meluzin.tibcobwutils.build.versioning;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface VersionSystemLogParser {
	public List<ChangeInfo> getChanges(InputStream logStream);
	public default List<ChangeInfo> getChanges(Path path) {
		return getChanges(path.toFile());
	}
	public default List<ChangeInfo> getChanges(File path) {
		try (InputStream is = new FileInputStream(path)) {
			return getChanges(is);	
		} catch (IOException e) {
			throw new RuntimeException("Could not process the file: " + path, e);
		}
	}
}
