package com.meluzin.tibcobwutils.earcomparer;

import java.nio.file.Path;

public class CompareResult {
	private CompareResultStatus status;
	private String message;
	private Path file;
	
	public CompareResult(Path file, CompareResultStatus status, String message) {
		super();
		this.file = file;
		this.status = status;
		this.message = message;
	}
	public CompareResultStatus getStatus() {
		return status;
	}
	public String getMessage() {
		return message;
	}
	public Path getFile() {
		return file;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((file == null) ? 0 : file.hashCode());
		result = prime * result + ((message == null) ? 0 : message.hashCode());
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
		CompareResult other = (CompareResult) obj;
		if (file == null) {
			if (other.file != null)
				return false;
		} else if (!file.equals(other.file))
			return false;
		if (message == null) {
			if (other.message != null)
				return false;
		} else if (!message.equals(other.message))
			return false;
		if (status != other.status)
			return false;
		return true;
	}
	@Override
	public String toString() {
		return status + ": " + message;
	}
}