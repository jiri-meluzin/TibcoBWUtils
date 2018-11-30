package com.meluzin.tibcobwutils.build.deploymentbuild.tasks.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.Log;
import com.meluzin.tibcobwutils.build.deploymentbuild.BinExecutor;
import com.meluzin.tibcobwutils.build.deploymentbuild.BuildBranchParallel;
import com.meluzin.tibcobwutils.build.deploymentbuild.BuildTaskComputer;
import com.meluzin.tibcobwutils.build.deploymentbuild.Deployment;
import com.meluzin.tibcobwutils.build.deploymentbuild.tasks.Task;

public class TaskBuildDeployment implements Task {
	private static Logger log = Log.get();
	private BuildBranchParallel buildBranchParallel;
	private Deployment deployment;
	private BuildTaskComputer btc;
	private Path archive;
	private TaskBuildDeployment(BuildBranchParallel buildBranchParallel, Deployment deployment, Path archive, BuildTaskComputer btc) {
		this.buildBranchParallel = buildBranchParallel;
		this.deployment = deployment;
		this.btc = btc;
		this.archive = archive;
	}
	@Override
	public void run() {
		log.info(Thread.currentThread().getName() + " "+"Building " + deployment + " - " +archive);
		Path archiveRelPath = deployment.getPath().relativize(archive);
		String buildEarExec = 
				String.format("buildear -ear %s -p %s -o %s -x -a %s", 
					Paths.get("/").resolve(archiveRelPath), 
					deployment.getPath(), 
					buildBranchParallel.getEars().resolve(archiveRelPath).toString().replace(".archive", ".ear"), 
					buildBranchParallel.getLibraries().resolve("aliases.txt"));
		BinExecutor result = BinExecutor.exec(buildEarExec);
		
		
		log.info("Building " + deployment + " archive " + archive+ " finished: " + result);
		boolean b = result.getReturnCode() != 0;
		if (b) {
			log.info("Building failed" + result.getReturnCode());			
		}
		buildBranchParallel.setError(buildBranchParallel.isError()|| b);
		buildBranchParallel.addBuiltDeployment(deployment);
		
		buildBranchParallel.getBuildLog().addDeploymentArchiveBuild(deployment, archive, result);
		
	}
	public static Stream<Task> buildDeploymentTask(BuildBranchParallel buildBranchParallel, Deployment deployment, BuildTaskComputer btc) {
		FileSearcher fs = new FileSearcher();
		List<Path> archives = fs.searchFiles(deployment.getPath(), "glob:**/*.archive", true);
		return archives.stream().map(p ->  new TaskBuildDeployment(buildBranchParallel, deployment, p, btc));		
	}

}
