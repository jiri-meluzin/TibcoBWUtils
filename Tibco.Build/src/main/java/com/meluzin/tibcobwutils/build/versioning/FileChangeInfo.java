package com.meluzin.tibcobwutils.build.versioning;

import java.nio.file.Path;
import java.util.Optional;


public class FileChangeInfo {
	private Path location;
	private Optional<Path> originalLocation;
	private ChangeStatus status;
	private FileChangeInfo(FileChangeInfo.Builder builder) {
		this.location = builder.location;
		this.originalLocation = builder.originalLocation;
		this.status = builder.status;
	}
	/**
	 * Creates builder to build {@link FileChangeInfo}.
	 * @return created builder
	 */
	public static FileChangeInfo.Builder builder() {
		return new Builder();
	}
	public Path getLocation() {
		return location;
	}
	public Optional<Path> getOriginalLocation() {
		return originalLocation;
	}
	public ChangeStatus getStatus() {
		return status;
	}
	@Override
	public String toString() {
		return getStatus() + " " + getLocation();
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((location == null) ? 0 : location.hashCode());
		result = prime * result + ((originalLocation == null) ? 0 : originalLocation.hashCode());
		result = prime * result + ((status == null) ? 0 : status.hashCode());
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
		FileChangeInfo other = (FileChangeInfo) obj;
		if (location == null) {
			if (other.location != null)
				return false;
		} else if (!location.equals(other.location))
			return false;
		if (originalLocation == null) {
			if (other.originalLocation != null)
				return false;
		} else if (!originalLocation.equals(other.originalLocation))
			return false;
		if (status != other.status)
			return false;
		return true;
	}
	/**
	 * Builder to build {@link FileChangeInfo}.
	 */
	public static final class Builder {
		private Path location;
		private Optional<Path> originalLocation = Optional.empty();
		private ChangeStatus status;

		private Builder() {
		}

		public FileChangeInfo.Builder withLocation(Path location) {
			this.location = location;
			return this;
		}

		public FileChangeInfo.Builder withOriginalLocation(Optional<Path> originalLocation) {
			this.originalLocation = originalLocation;
			return this;
		}

		public FileChangeInfo.Builder withStatus(ChangeStatus status) {
			this.status = status;
			return this;
		}

		public FileChangeInfo build() {
			return new FileChangeInfo(this);
		}
	}
	
}