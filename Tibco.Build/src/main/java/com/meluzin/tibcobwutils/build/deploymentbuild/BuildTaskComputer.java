package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.meluzin.functional.Lists;
import com.meluzin.functional.Log;

public class BuildTaskComputer {
	private static Logger log = Log.get();
	private Map<Library, Set<Deployment>> libraryUsage;
	private Map<Library, Deployment> librarySource;		
	private Map<Deployment, Set<Deployment>> deploymentDependencies;

	private Set<Library> unchangedLibraries = new HashSet<>();
	private Set<Library> finishedLibraries = new HashSet<>();
	private Set<Deployment> deploymentsToBuild = new HashSet<>();
	private Set<Deployment> deploymentsCanBeBuilt = new HashSet<>();
	
	private Set<Deployment> changedDeployments;
	private Set<Deployment> alreadyBuiltDeplyoments = new HashSet<>();

	private BTCObservable deploymentsCanBeBuiltObservable = new BTCObservable();
	private BTCObservable finishedObservable = new BTCObservable();	
	
	private ForkJoinPool pool = ForkJoinPool.commonPool();
	
	private class BTCObservable extends Observable {
		public void setNewValueAndNotify(Object newVal) {
			this.setChanged();
			this.notifyObservers(newVal);
		}
		public void setNewValueAndNotify() {
			this.setChanged();
			this.notifyObservers();
		}
	}
	public BuildTaskComputer(Set<Deployment> allDeployments, Set<Deployment> changedDeployments) {
		this.libraryUsage = getLibraryUsage(allDeployments);
		this.librarySource = librarySource(allDeployments);		
		this.deploymentDependencies = getDeploymentDependencies(allDeployments, librarySource);
		this.changedDeployments = changedDeployments;
	}

	synchronized public Set<Deployment> getDeploymentCanBeBuilt() {
		return new HashSet<>(deploymentsCanBeBuilt);
	}
	
	public ForkJoinPool getPool() {
		return pool;
	}
	public void setPool(ForkJoinPool pool) {
		this.pool = pool;
	}
	
	synchronized public void libraryBuilt(Library lib, boolean changed) {
		log.fine("Library " + lib + " from " + lib.getSourcePath() + " has been built");
		finishedLibraries.add(lib);
		if (!changed) {
			unchangedLibraries.add(lib);
		}
		getPool().execute(() -> updateStatus(changedDeployments));
	}
	
	synchronized public void deplyomentBuilt(Deployment deployment) {
		log.fine("Deployment " + deployment + " has been built");
		alreadyBuiltDeplyoments.add(deployment);
		getPool().execute(() -> updateStatus(changedDeployments));
	}
	
	public void start() {
		updateStatus(changedDeployments);
	}

	synchronized private Set<Deployment> updateStatus(Set<Deployment> changedDeployments) {
		log.fine("Changed deployments: " + changedDeployments);
		log.fine("Unchanged libraries: " + unchangedLibraries);
		log.fine("Library usage: " + libraryUsage);
		deploymentsToBuild.clear();
		deploymentsToBuild.addAll(changedDeployments);		
		Set<Deployment> dependentDeployments = new HashSet<>(changedDeployments);
		Set<Library> librariesToBuild = new HashSet<>();
		while (!dependentDeployments.isEmpty()) {
			Set<Deployment> deploymentsToRebuild = new HashSet<>();
			dependentDeployments.forEach(d -> {
				d.getDeclaredLibraries().forEach(l -> {					
					if (!unchangedLibraries.contains(l)) {
						deploymentsToRebuild.addAll(libraryUsage.getOrDefault(l, new HashSet<>()));
					}
				});
				librariesToBuild.addAll(d.getDeclaredLibraries());
				deploymentsToBuild.add(d);
			});
			dependentDeployments.clear();
			dependentDeployments.addAll(deploymentsToRebuild);
		}
		log.fine("All deployments that should be build: " + deploymentsToBuild);
		Set<Deployment> newDeploymentCanBeBuilt = deploymentsToBuild.stream().
				// select deplyoments that does not depend on other deployment to build
				filter(d -> !alreadyBuiltDeplyoments.contains(d)).
				filter(d -> 
					d.getDependencies().stream().
						filter(l -> librariesToBuild.contains(l)).
						filter(l -> !finishedLibraries.contains(l)).
						count() == 0
				
					/*deploymentDependencies.get(d).size() == deploymentDependencies.get(d).stream().
						filter(dep -> !deploymentsToBuild.contains(dep) || alreadyBuiltDeplyoments.contains(dep)).count()*/).
				// select only deplyoments that have not beed built yet
				//filter(d -> !alreadyBuiltDeplyoments.contains(d)).				
				collect(Collectors.toSet());
		log.fine("Deployments can be build now: " + newDeploymentCanBeBuilt);
		if (!newDeploymentCanBeBuilt.equals(deploymentsCanBeBuilt)) {
			this.deploymentsCanBeBuilt = newDeploymentCanBeBuilt;
			getPool().execute(() -> deploymentsCanBeBuiltObservable.setNewValueAndNotify(newDeploymentCanBeBuilt));
		}
		if (this.deploymentsCanBeBuilt.size() == 0) {
			getPool().execute(() -> finishedObservable.setNewValueAndNotify());			
		}
		return deploymentsCanBeBuilt;
	}

	private Map<Deployment, Set<Deployment>> getDeploymentDependencies(Set<Deployment> allDeployments,
			Map<Library, Deployment> librarySource) {
		Map<Deployment, Set<Deployment>> deploymentDependencies = new HashMap<>();
		
		allDeployments.forEach(d -> {
			if (!deploymentDependencies.containsKey(d)) deploymentDependencies.put(d, new HashSet<>());
			d.getDependencies().forEach(l -> {
				deploymentDependencies.get(d).add(librarySource.get(l));
			});
		});
		return deploymentDependencies;
	}

	private Map<Library, Deployment> librarySource(Set<Deployment> allDeployments) {
		Map<Library, Deployment> librarySource = new HashMap<>();
		allDeployments.forEach(d -> {
			d.getDeclaredLibraries().forEach(l -> {
				librarySource.put(l, d);
			});
		});
		return librarySource;
	}
	
	public Deployment getLibrarySource(Library lib) {
		return librarySource.get(lib);
	}

	private Map<Library, Set<Deployment>> getLibraryUsage(Set<Deployment> allDeployments) {
		Map<Library, Set<Deployment>> libraryUsage = new HashMap<>();
		allDeployments.forEach(d -> {
			d.getDependencies().forEach(l -> {
				if (!libraryUsage.containsKey(l)) libraryUsage.put(l, new HashSet<>());
				libraryUsage.get(l).add(d);
			});
		});
		return libraryUsage;
	}
	
	public Observable getDeploymentsCanBeBuiltObservable() {
		return deploymentsCanBeBuiltObservable;
	}
	public Observable getFinished() {
		return finishedObservable;
	}
}
