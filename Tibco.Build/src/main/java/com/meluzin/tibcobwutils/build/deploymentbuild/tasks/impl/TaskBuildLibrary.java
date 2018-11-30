package com.meluzin.tibcobwutils.build.deploymentbuild.tasks.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

import com.meluzin.functional.Log;
import com.meluzin.tibcobwutils.build.deploymentbuild.BinExecutor;
import com.meluzin.tibcobwutils.build.deploymentbuild.BuildBranchParallel;
import com.meluzin.tibcobwutils.build.deploymentbuild.BuildTaskComputer;
import com.meluzin.tibcobwutils.build.deploymentbuild.Deployment;
import com.meluzin.tibcobwutils.build.deploymentbuild.Library;
import com.meluzin.tibcobwutils.build.deploymentbuild.tasks.Task;

public class TaskBuildLibrary implements Task {
	private static Logger log = Log.get();
	private BuildBranchParallel buildBranchParallel;
	private Library library;
	private BuildTaskComputer btc;
	private boolean isChanged;
	private TaskBuildLibrary(BuildBranchParallel buildBranchParallel, Library l, BuildTaskComputer btc) {
		this.buildBranchParallel = buildBranchParallel;
		this.library = l;
		this.btc = btc;
	}
	@Override
	public void run() {
		log.info(Thread.currentThread().getName() + " "+  "Building " + library);
		
		Deployment deployment = btc.getLibrarySource(library);
		Path tmpProjlib = buildBranchParallel.getLibraries().resolve(library.getName() + ".tmp.projlib");
		String exec = String.format("buildlibrary -lib %s -p %s -o %s -x -s -a %s", 
				Paths.get("/").resolve(deployment.getPath().relativize(library.getPath())), // relative path to projlib inside deployment  
				deployment.getPath(), 											      // absolute path to deployment source
				tmpProjlib,                      // absolute path to built library output
				buildBranchParallel.getLibraries().resolve(library.getName().replace(".projlib", ".designtimelibs")) // absolute path to aliases file (TibLibBuilderDep calls it designtimelibs)
			);
		BinExecutor e = BinExecutor.exec(exec, Optional.of(Duration.ofMinutes(5)));
		log.info("Building " + library + " finished: " + e);
		String moveLib = String.format("mv %s %s", tmpProjlib, buildBranchParallel.getLibraries().resolve(library.getName()));
		BinExecutor emove = BinExecutor.exec(moveLib);
		log.info("Move built library " + library + " finished: " + emove);		
		boolean b = e.getReturnCode() != 0;
		if (b) {
			log.info("Building failed" + e.getReturnCode());			
		}
		buildBranchParallel.setError(buildBranchParallel.isError() || b);
		this.isChanged = compareNewlyBuiltLibrary(library);
		
		buildBranchParallel.addBuiltLibrary(library);
		if (this.isChanged) buildBranchParallel.addChangedLibrary(library);
		btc.libraryBuilt(library, this.isChanged);
		
		buildBranchParallel.getBuildLog().addDeploymentLibraryBuild(deployment, library, e);
	}
	
	private boolean compareNewlyBuiltLibrary(Library l) {
		String exec = String.format("/tib/app/jenkins2/runtime/compare-archive.sh -old %s -new %s", 
				buildBranchParallel.getOldLibraries().resolve(l.getName()), 
				buildBranchParallel.getLibraries().resolve(l.getName()));
		BinExecutor b = BinExecutor.exec(exec);
		log.info("Compared: result = " + b.getReturnCode() + " - " + exec) ;
		return b.getReturnCode() == 1;
	}
	public static TaskBuildLibrary buildLibraryTask(BuildBranchParallel buildBranchParallel, Library library, BuildTaskComputer btc) {
		return new TaskBuildLibrary(buildBranchParallel, library, btc);		
	}
}
