package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.Log;

public class DeploymentLoader {
	private static Logger log = Log.get();
	
	
	private static final String GLOB_VCREPO_DAT = "glob:**/vcrepo.dat";
	private static final String GLOB_LIBBUILDER = "glob:**/*.libbuilder";
	private String globLibbuilder = GLOB_LIBBUILDER;
	private String globVcrepoDat = GLOB_VCREPO_DAT;
	
	
	public String getGlobLibbuilder() {
		return globLibbuilder;
	}
	public void setGlobLibbuilder(String globLibbuilder) {
		this.globLibbuilder = globLibbuilder;
	}
	public String getGlobVcrepoDat() {
		return globVcrepoDat;
	}
	public void setGlobVcrepoDat(String globVcrepoDat) {
		this.globVcrepoDat = globVcrepoDat;
	}

	public boolean filterLibraryPath(Path deploymentPath) {
		return true;
	}
	public boolean filterDeploymentPath(Path deploymentPath) {
		return true;
	}
	
	public Set<Deployment> loadDeployments(Path branchPath, Function<Path, Library> libraryProducer) {
		FileSearcher search = new FileSearcher();
		Map<String, Library> libraries = new HashMap<>();
		Map<Path, Library> librariesFromSourcePath = new HashMap<>();
		Set<Deployment> deployments = new HashSet<>();
		search.searchFiles(branchPath, globLibbuilder, true).stream().filter(this::filterLibraryPath).forEach(p -> {
			Library l = libraryProducer.apply(p);

			log.fine("Found libbuilder " + l.getName() + " " + l.getSourcePath());
			if (l.getName() == null) throw new RuntimeException("Could not resolve libbuilder " + p);
			libraries.put(l.getName(), l);
			librariesFromSourcePath.put(p, l);
		});
		search.searchFiles(branchPath, globVcrepoDat, true).stream().filter(this::filterDeploymentPath).forEach(p -> {
			List<Library> dependencies = new ArrayList<>();
			Path designtimelibs = p.getParent().resolve(".designtimelibs");
			if (designtimelibs.toFile().exists()) {
				try(Stream<String> designtimelibsLines = Files.lines(designtimelibs)) {
	
					dependencies.addAll(designtimelibsLines.
		                filter(s -> s.contains("\\=")).
		               
		                map(s -> s.replaceAll("\\\\", "").split("=")).
		                sorted((parts1, parts2) -> Integer.parseInt(parts1[0]) - Integer.parseInt(parts2[0])).
		                map(parts -> parts[1]).
		                map(name -> findLibrary(libraries, name, p.getParent())).
		                filter(l -> l.isPresent()).
		                map(l -> l.get()).
		                collect(Collectors.toList()));
					
				} catch (IOException e) {
					throw new RuntimeException("Cannot load .designtimelibs file " + p, e);
				}	
			}
			
			List<Path> archives = search.searchFiles(p.getParent(), "glob:**/*.archive", true);	
			List<Library> declaredLibs =  
					search.searchFiles(p.getParent(), globLibbuilder, true).stream().map(lp -> librariesFromSourcePath.get(lp)).collect(Collectors.toList());
				deployments.add(new Deployment(p.getParent(), dependencies, declaredLibs, archives));
		});
		
		
		
		return deployments;
		
	}
	private Optional<Library> findLibrary(Map<String, Library> libraries, String name, Path deploymentPath) {
		Library foundLibrary = libraries.get(name);
		if (foundLibrary == null) {
			log.fine("No library found for: " + name + " referenced from "+deploymentPath);
		}
		return Optional.ofNullable(foundLibrary);
	}
}
