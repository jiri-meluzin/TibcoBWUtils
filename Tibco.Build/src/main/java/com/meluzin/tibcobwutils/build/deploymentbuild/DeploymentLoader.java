package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;

public class DeploymentLoader {

	
	public List<Deployment> loadDeployments(Path branchPath) {
		XmlBuilderFactory fac = new XmlBuilderFactory();
		FileSearcher search = new FileSearcher();
		Map<String, Library> libraries = new HashMap<>();
		Map<Path, Library> librariesFromPath = new HashMap<>();
		List<Deployment> deployments = new ArrayList<>();
		search.searchFiles(branchPath, "glob:**/*.libbuilder", true).forEach(p -> {
			Library l = new Library(p, p.getFileName().toString().replace(".libbuilder", ".projlib"));
			libraries.put(l.getName(), l);
			librariesFromPath.put(p, l);
		});
		search.searchFiles(branchPath, "glob:**/vcrepo.dat", true).forEach(p -> {
			List<Library> dependencies = new ArrayList<>();
			Path designtimelibs = p.getParent().resolve(".designtimelibs");
			if (designtimelibs.toFile().exists()) {
				try(Stream<String> designtimelibsLines = Files.lines(designtimelibs)) {
	
					dependencies.addAll(designtimelibsLines.
		                filter(s -> s.contains(".projlib")).
		               
		                map(s -> s.replaceAll("\\\\", "").split("=")).
		                sorted((parts1, parts2) -> Integer.parseInt(parts1[0]) - Integer.parseInt(parts2[0])).
		                map(parts -> parts[1]).
		                map(name -> libraries.get(name)).
		                collect(Collectors.toList()));
					
				} catch (IOException e) {
					throw new RuntimeException("Cannot load .designtimelibs file " + p, e);
				}	
			}
			
			List<Path> archives = search.searchFiles(p.getParent(), "glob:**/*.archive", true);	
			List<Library> declaredLibs =  
					search.searchFiles(p.getParent(), "glob:**/*.libbuilder", true).stream().map(lp -> librariesFromPath.get(lp)).collect(Collectors.toList());
				deployments.add(new Deployment(p.getParent(), dependencies, declaredLibs, archives));
		});
		
		
		
		return deployments;
		
	}
}
