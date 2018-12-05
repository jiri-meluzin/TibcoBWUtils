package com.meluzin.tibcobwutils.build.versioning;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ChangeInfo {
	private String author;
	private String revisionInfo;
	private Date committedAt;
	private String comment;
	private List<FileChangeInfo> changedFiles;
	private ChangeInfo(ChangeInfo.Builder builder) {
		this.author = builder.author;
		this.committedAt = builder.committedAt;
		this.changedFiles = builder.changedFiles;
		this.revisionInfo = builder.revisionInfo;
		this.comment = builder.comment;
	}
	public String getAuthor() {
		return author;
	}
	public List<FileChangeInfo> getChangedFiles() {
		return changedFiles;
	}
	public Date getCommittedAt() {
		return committedAt;
	}
	public String getRevisionInfo() {
		return revisionInfo;
	}
	public String getComment() {
		return comment;
	}
	@Override
	public String toString() {
		return revisionInfo + " " + author + " " + committedAt;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((author == null) ? 0 : author.hashCode());
		result = prime * result + ((changedFiles == null) ? 0 : changedFiles.hashCode());
		result = prime * result + ((comment == null) ? 0 : comment.hashCode());
		result = prime * result + ((committedAt == null) ? 0 : committedAt.hashCode());
		result = prime * result + ((revisionInfo == null) ? 0 : revisionInfo.hashCode());
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
		ChangeInfo other = (ChangeInfo) obj;
		if (author == null) {
			if (other.author != null)
				return false;
		} else if (!author.equals(other.author))
			return false;
		if (changedFiles == null) {
			if (other.changedFiles != null)
				return false;
		} else if (!changedFiles.equals(other.changedFiles))
			return false;
		if (comment == null) {
			if (other.comment != null)
				return false;
		} else if (!comment.equals(other.comment))
			return false;
		if (committedAt == null) {
			if (other.committedAt != null)
				return false;
		} else if (!committedAt.equals(other.committedAt))
			return false;
		if (revisionInfo == null) {
			if (other.revisionInfo != null)
				return false;
		} else if (!revisionInfo.equals(other.revisionInfo))
			return false;
		return true;
	}
	/**
	 * Creates builder to build {@link ChangeInfo}.
	 * @return created builder
	 */
	public static ChangeInfo.Builder builder() {
		return new Builder();
	}
	/**
	 * Builder to build {@link ChangeInfo}.
	 */
	public static final class Builder {
		private String author;
		private Date committedAt;
		private String revisionInfo;
		private String comment;
		private List<FileChangeInfo> changedFiles = Collections.emptyList();

		private Builder() {
		}

		public ChangeInfo.Builder withComment(String comment) {
			this.comment = comment;
			return this;
		}
		public ChangeInfo.Builder withAuthor(String author) {
			this.author = author;
			return this;
		}

		public ChangeInfo.Builder withRevisionInfo(String revisionInfo) {
			this.revisionInfo = revisionInfo;
			return this;
		}
		
		public ChangeInfo.Builder withCommittedAt(Date committedAt) {
			this.committedAt = committedAt;
			return this;
		}

		public ChangeInfo.Builder withChangedFiles(List<FileChangeInfo> changedFiles) {
			this.changedFiles = changedFiles;
			return this;
		}

		public ChangeInfo build() {
			return new ChangeInfo(this);
		}
	}
	
}