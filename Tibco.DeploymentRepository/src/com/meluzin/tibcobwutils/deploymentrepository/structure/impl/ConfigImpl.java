package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.functional.Log;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Alias;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Config;

public class ConfigImpl implements Config {
	private Map<String, Alias> aliases;
	private Path designerConfig;
	public ConfigImpl() {
		this(Paths.get(System.getProperty("user.home")).resolve(".TIBCO/Designer5.prefs"));	
	}
	public ConfigImpl(Path prefs) {
		designerConfig = prefs;
		try(Stream<String> configLines = Files.lines(designerConfig)) {
			this.aliases =
				configLines.
					filter(l -> l.startsWith("filealias.pref") || l.startsWith("tibco.alias.")).
					map(l -> l.trim().replaceAll("\\\\:", ":").replaceAll("\\\\=", "=").replaceAll("\\\\\\\\", "\\\\").split("=")).
					map(parts -> parts.length == 3 ? new AliasImpl(parts[1], Paths.get(parts[2])) : new AliasImpl(parts[0].replace("tibco.alias.", "") , Paths.get(parts[1]))).
					collect(Collectors.toMap(Alias::getAliasName, Function.identity()));
		} catch (IllegalStateException e) {
			throw new RuntimeException("Cannot read Designer prefs file from: " + designerConfig + "; " + e.getMessage(), e);
		} catch (IOException e) {
			throw new RuntimeException("Cannot read Designer prefs file from: " + designerConfig, e);
		}
	}
	@Override
	public Collection<Alias> getAliases() {
		return aliases.values();
	}

	
	@Override
	public Path getConfigPath() {
		return designerConfig;
	}
	@Override
	public Alias getAliasByName(String name) {
		Alias ret = aliases.get(name);
		if (ret == null) Log.get().warning("Warning: cannot resolve alias '" + name + "'");
		return ret;
	}

	public class AliasImpl implements Alias {
		private String aliasName;
		private Path targetPath;
		public AliasImpl(String aliasName, Path targetPath) {
			this.aliasName = aliasName;
			this.targetPath = targetPath;
		}

		@Override
		public String getAliasName() {
			return aliasName;
		}

		@Override
		public Path getTargetPath() {
			return targetPath;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((aliasName == null) ? 0 : aliasName.hashCode());
			result = prime * result
					+ ((targetPath == null) ? 0 : targetPath.hashCode());
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
			AliasImpl other = (AliasImpl) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (aliasName == null) {
				if (other.aliasName != null)
					return false;
			} else if (!aliasName.equals(other.aliasName))
				return false;
			if (targetPath == null) {
				if (other.targetPath != null)
					return false;
			} else if (!targetPath.equals(other.targetPath))
				return false;
			return true;
		}

		private ConfigImpl getOuterType() {
			return ConfigImpl.this;
		}
		
		@Override
		public String toString() {
			return "[" + aliasName + "=" + targetPath + "]";
		}
	}
}
