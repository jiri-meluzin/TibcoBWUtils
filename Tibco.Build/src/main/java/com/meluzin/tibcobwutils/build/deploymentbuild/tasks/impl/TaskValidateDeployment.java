package com.meluzin.tibcobwutils.build.deploymentbuild.tasks.impl;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.meluzin.functional.Log;
import com.meluzin.tibcobwutils.build.deploymentbuild.BinExecutor;
import com.meluzin.tibcobwutils.build.deploymentbuild.BuildBranchParallel;
import com.meluzin.tibcobwutils.build.deploymentbuild.BuildTaskComputer;
import com.meluzin.tibcobwutils.build.deploymentbuild.Deployment;
import com.meluzin.tibcobwutils.build.deploymentbuild.tasks.Task;

public class TaskValidateDeployment implements Task {
	private static Logger log = Log.get();
	private BuildBranchParallel buildBranchParallel;
	private Deployment deployment;
	private BuildTaskComputer btc;
	private TaskValidateDeployment(BuildBranchParallel buildBranchParallel, Deployment deployment, BuildTaskComputer btc) {
		this.buildBranchParallel = buildBranchParallel;
		this.deployment = deployment;
		this.btc = btc;
	}
	@Override
	public void run() {
		log.info(Thread.currentThread().getName() + " "+"Validating " + deployment);
		Path aliasesPath = buildBranchParallel.getLibraries().resolve("aliases.txt");
		String validationExec = String.format("buildear -v -p %s -x -a %s",
				deployment.getPath(), 
				aliasesPath);
		BinExecutor result = BinExecutor.exec(validationExec);
		log.info("Validating " + deployment + " finished: " + result);		
		
		String configValidationExec = String.format("/tib/app/jenkins2/runtime/validate-config.sh  -prefs  %s -source %s -export %s -config %s", 
					aliasesPath, 
					deployment.getPath(),
					"/tib/app/jenkins2/home/userContent/artifacts/TIBCO/_deployed/",
					buildBranchParallel.getSourceRoot().resolve("_config/")
					
					);
		BinExecutor resultConfig = BinExecutor.exec(configValidationExec);
		log.info("Validating " + deployment + " (FullConfig) finished: " + resultConfig);		

		boolean b = resultConfig.getReturnCode() != 0;
		if (b) {
			log.info("Config validation failed" + result.getReturnCode());			
		}
		buildBranchParallel.setError(buildBranchParallel.isError()|| b);

		buildBranchParallel.getBuildLog().addDeploymentValidation(deployment, result);
		buildBranchParallel.getBuildLog().addDeploymentValidation(deployment, resultConfig);
		
	}
	public static Stream<Task> validateDeploymentTask(BuildBranchParallel buildBranchParallel, Deployment deployment, BuildTaskComputer btc) {
		return Stream.of(new TaskValidateDeployment(buildBranchParallel, deployment, btc));		
	}

}
