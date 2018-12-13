package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.Log;
import com.meluzin.tibcobwutils.build.deploymentbuild.tasks.Task;
import com.meluzin.tibcobwutils.build.deploymentbuild.tasks.TaskRunner;
import com.meluzin.tibcobwutils.build.deploymentbuild.tasks.impl.TaskBuildDeployment;
import com.meluzin.tibcobwutils.build.deploymentbuild.tasks.impl.TaskBuildLibrary;
import com.meluzin.tibcobwutils.build.deploymentbuild.tasks.impl.TaskValidateDeployment;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class BuildBranchParallel implements BuildContext {
	private static Logger log = Log.get();
	private Optional<String> jobNumber = Optional.empty();
	private Path sourceRoot;
	private Path libraries;
	private Path ears;
	private Path oldLibraries;
	private ForkJoinPool pool = new ForkJoinPool(10);
	private boolean error = false;
	private NodeBuilder changeLogSourceXml;
	private Set<Deployment> changedDeployments = new HashSet<>();
	private Set<Deployment> rebuiltDeployments = new HashSet<>();
	private Set<Library> rebuiltLibraries = new HashSet<>();
	private Set<Library> changedLibraries = new HashSet<>();
	private BuildLogRecorder buildLog = new BuildLogRecorder();
	private Optional<Path> buildLogPath;
	private Optional<Path> validationLogPath;
	private Optional<Path> xslForBuildLogOutput;
	public Optional<String> getJobNumber() {
		return jobNumber;
	}
	public BuildLogRecorder getBuildLog() {
		return buildLog;
	}
	public Path getEars() {
		return ears;
	}
	public synchronized Set<Deployment> getRebuiltDeployments() {
		return new HashSet<>(rebuiltDeployments);
	}
	public synchronized void addBuiltDeployment(Deployment deployment) {
		rebuiltDeployments.add(deployment);
	}
	public synchronized void addBuiltLibrary(Library lib) {
		rebuiltLibraries.add(lib);
	}
	public synchronized void addChangedLibrary(Library lib) {
		changedLibraries.add(lib);
	}
	public synchronized Set<Library> getChangedLibraries() {
		return new HashSet<>(changedLibraries);
	}
	public void setError(boolean error) {
		this.error = error;
	}
	public boolean isError() {
		return error;
	}
	public Path getLibraries() {
		return libraries;
	}
	public Path getOldLibraries() {
		return oldLibraries;
	}
	public Path getSourceRoot() {
		return sourceRoot;
	}
	
	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("BuildBranchParallel", true, "-")
				.description("Build libraries and deployments in parallel.");
		argParser.addArgument("-branch").type(String.class).required(true).help("Name of branch");
		argParser.addArgument("-job-number").type(String.class).required(false).help("Id of Jenkins job");
		argParser.addArgument("-ears").type(String.class).required(true).help("Path to ears directory");
		argParser.addArgument("-libraries").type(String.class).required(true).help("Path to libraries directory");
		argParser.addArgument("-old-libraries").type(String.class).required(true).help("Path to libraries archive directory");
		argParser.addArgument("-source").type(String.class).required(true).help("Path to source directory");
		argParser.addArgument("-change-log-source").type(String.class).required(true).help("Path to source code changelog");
		argParser.addArgument("-change-log-config").type(String.class).required(true).help("Path to config changelog");
		argParser.addArgument("-parallelism").type(Integer.class).required(false).help("Number of threads");
		argParser.addArgument("-build-log").type(String.class).required(false).help("Build log file");
		argParser.addArgument("-build-log-xsl").type(String.class).required(false).help("XSL for build log");
		argParser.addArgument("-validation-log").type(String.class).required(false).help("Validation log file");
		Namespace res = argParser.parseArgsOrFail(args);
		log.severe("Starting");
		Path changeLogSource = Paths.get(res.getString("change_log_source"));
		Path changeLogConfig = Paths.get(res.getString("change_log_config"));
		Optional<String> jobNumber = Optional.ofNullable(res.get("job_number"));
		String branch = res.getString("branch");
		String source = res.getString("source");
		String ears = res.getString("ears");
		String libraries = res.getString("libraries");
		String oldLibraries = res.getString("old_libraries");
		Optional<Path> buildLogPath = res.getString("build_log") != null ? Optional.of(Paths.get(res.getString("build_log"))) : Optional.empty();
		Optional<Path> buildLogXslPath = res.getString("build_log_xsl") != null ? Optional.of(Paths.get(res.getString("build_log_xsl"))) : Optional.empty();
		Optional<Path> validationLogPath = res.getString("validation_log") != null ? Optional.of(Paths.get(res.getString("validation_log"))) : Optional.empty();
		Path branchPath = Paths.get(source).toAbsolutePath();
		Set<Deployment> deployments =  new DeploymentLoader().loadDeployments(branchPath);
		Integer parallelism = res.getInt("parallelism");

		BuildBranchParallel b = new BuildBranchParallel(jobNumber, parallelism, branchPath, Paths.get(ears).toAbsolutePath(), Paths.get(libraries).toAbsolutePath(), Paths.get(oldLibraries).toAbsolutePath(), buildLogPath, validationLogPath, buildLogXslPath);
		Set<String> changed = b.computeChangedDeployments(deployments, changeLogSource, changeLogConfig, branch);
		b.build(deployments.stream().filter(d -> changed.contains(d.getName())).collect(Collectors.toSet()), deployments);
	}
	private Set<String> computeChangedDeployments(Set<Deployment> deployments, Path changeLogSource, Path changeLogConfig, String branch) {
		
		XmlBuilderFactory fac = new XmlBuilderFactory();
		this.changeLogSourceXml = fac.loadFromFile(changeLogSource);
		Set<String> changeDeploymentsSource = changeLogSourceXml.
			search(true, n -> "path".equals(n.getName())). // <path kind="file" action="M">/TIBCO/Branches/R170604/DTW2_API_IO/.designtimelibs</path>
			map(n -> n.getTextContent()).  // /TIBCO/Branches/R170604/DTW2_API_IO/.designtimelibs
			map(p -> p.split("/")).			// [,TIBCO,Branches,R170604,DTW2_API_IO,...]
			filter(parts -> parts.length > 4). 
			map(parts -> parts[4]).			// DTW2_API_IO
			collect(Collectors.toSet());

		Set<String> changedConfig = fac.loadFromFile(changeLogConfig).
				search(true, n -> "path".equals(n.getName())). // <path kind="file" action="A">/TIBCO/Config/M170422_ICM-107/BATCH_API_IO_update.xml</path>
				map(n -> n.getTextContent()).  // /TIBCO/Config/M170422_ICM-107/BATCH_API_IO_update.xml
				filter(p -> p.contains(branch)). 
				map(p -> p.split("/")).			// [,TIBCO,Config,M170422_ICM-107,BATCH_API_IO_update.xml]
				filter(parts -> parts.length > 4). 
				map(parts -> parts[4]).			// BATCH_API_IO_update.xml
				map(updateScript -> 
						deployments.stream().
							filter(d -> d.getDeclaredArchives().stream().
											anyMatch(archivePath -> archivePath.getFileName().toString().replace(".archive",  "").equals(updateScript.replace("_update.xml", "")))
								   ).findFirst().get().getName()
					).
				collect(Collectors.toSet());
		
		Set<String> changedFullConfigsSet = changeLogSourceXml.
				search(true, n -> "path".equals(n.getName())). // <path kind="file" action="A">/TIBCO/Branches/R170515/_config/ENV_EAI_TST2/CVW_ONLINE_IO.xml</path>
				map(n -> n.getTextContent()).  // /TIBCO/Branches/R170515/_config/ENV_EAI_TST2/CVW_ONLINE_IO.xml
				filter(p -> p.contains(branch+"/_config/")). 
				map(p -> p.split("/")).			// [,TIBCO,Branches,R170515,_config,ENV_EAI_TST2,CVW_ONLINE_IO.xml]
				filter(parts -> parts.length > 6). 
				map(parts -> parts[6].replace(".xml", "")).			// CVW_ONLINE_IO.xml
				map(fullConfig -> 
						deployments.stream().
							filter(d -> d.getDeclaredArchives().stream().
											anyMatch(archivePath -> archivePath.getFileName().toString().replace(".archive",  "").equals(fullConfig))
								   ).findFirst()
					).
				filter(v -> v.isPresent()).
				map(v -> v.get().getName()).
				collect(Collectors.toSet());
		
		List<String> changedDeployments = changeDeploymentsSource.stream().sorted().collect(Collectors.toList());
		log.info("Changed deployments: " + changedDeployments);
		log.info("Updated configuration: " + changedConfig.stream().sorted().collect(Collectors.toList()));
		List<String> changedFullConfigs = changedFullConfigsSet.stream().sorted().collect(Collectors.toList());
		log.info("Updated full configs: " + changedFullConfigs);
		changeDeploymentsSource.addAll(changedConfig);	
		changeDeploymentsSource.addAll(changedFullConfigsSet);
		return changeDeploymentsSource;
	}
	public BuildBranchParallel(Optional<String> jobNumber, Integer parallelism, Path sourceRoot, Path ears, Path libraries, Path oldLibraries, Optional<Path> buildLogPath, Optional<Path> validationLogPath, Optional<Path> xslForBuildLogOutput) {
		this.jobNumber = jobNumber;
		this.sourceRoot = sourceRoot;	
		this.ears = ears;
		this.libraries = libraries;
		this.oldLibraries = oldLibraries;
		this.pool = parallelism == null ? ForkJoinPool.commonPool() : new ForkJoinPool(parallelism);	
		this.buildLogPath = buildLogPath;
		this.validationLogPath = validationLogPath;
		this.xslForBuildLogOutput = xslForBuildLogOutput;
	}
	public void build(Set<Deployment> changedDeployments, Set<Deployment> allDeployments) {
		buildLog.start();
		this.changedDeployments = changedDeployments;
		BuildTaskComputer btc = new BuildTaskComputer(allDeployments, changedDeployments);
		btc.setPool(pool);
		Set<Deployment> currentlyBuilding = new HashSet<>();
		btc.getDeploymentsCanBeBuiltObservable().addObserver((o, arg) -> processDeploymentToBuild(btc, currentlyBuilding));
		btc.getFinished().addObserver((o, arg) -> onFinish());
		if (changedDeployments.size() > 0) { 
			btc.start();
			waitForFinish();
		}
		buildLog.finish();
		log.info("Rebuilt deployments: " + rebuiltDeployments.stream().sorted((a,b) -> a.getName().compareTo(b.getName())).collect(Collectors.toList()));
		log.info("Rebuilt libraries: " + rebuiltLibraries.stream().sorted((a,b) -> a.getName().compareTo(b.getName())).collect(Collectors.toList()));
		log.info("Changed libraries: " + changedLibraries.stream().sorted((a,b) -> a.getName().compareTo(b.getName())).collect(Collectors.toList()));
		NodeBuilder buildLogContent = buildLog.generateResult(this);
		List<String> validationLogContent = buildLog.generateValidationResult(this);
		log.info("Log result: " + buildLogContent);
		log.info("Log validation: " + validationLogContent);
		if (buildLogPath.isPresent()) {
			XmlBuilderFactory fac = new XmlBuilderFactory();
			NodeBuilder copy = buildLogContent.copy();
			if (xslForBuildLogOutput.isPresent()) {
				copy.addProcessingInstruction("xml-stylesheet", "type=\"text/xsl\" href=\""+xslForBuildLogOutput.get()+"\"");
			}
			fac.renderNode(copy, buildLogPath.get());
		}
		if (validationLogPath.isPresent()) {
			try (OutputStream o = new FileOutputStream(validationLogPath.get().toFile())) {
				IOUtils.writeLines(validationLogContent, "\n", o);				
			} catch (IOException e) {
				log.severe("Could not store validation log: " + e.getMessage());
				error = true;
			}
		}
		if (error) {
			log.info("done with errors");
			System.exit(1);
		} else {
			System.exit(0);
		}
	}

	private void onFinish() {
		log.fine("onFinished");
		synchronized (BuildBranchParallel.this) {
			BuildBranchParallel.this.notify();
		}
	}

	private void waitForFinish() {
		try {
			synchronized(this) {
				this.wait();
			}
			log.info("finished");
		} catch (InterruptedException e) {			
			log.severe(e.getMessage());
		}
	}

	private void processDeploymentToBuild(BuildTaskComputer btc, Set<Deployment> currentlyBuilding) {
		Set<Deployment> deploymentCanBeBuilt = btc.getDeploymentCanBeBuilt();
		log.info("Remaining to build: " + deploymentCanBeBuilt);
		deploymentCanBeBuilt.removeAll(currentlyBuilding);
		currentlyBuilding.addAll(deploymentCanBeBuilt);
		deploymentCanBeBuilt.forEach(d -> { 
			
			Stream<Task> librariesTasks = d.getDeclaredLibraries().stream().map(l -> TaskBuildLibrary.buildLibraryTask(this, l, btc));
			Stream<Task> deploymentTask = TaskBuildDeployment.buildDeploymentTask(this, d, btc);
			Stream<Task> validationTask = TaskValidateDeployment.validateDeploymentTask(this, d, btc);
			
			Stream<Task> tasks = Stream.concat(Stream.concat(librariesTasks, deploymentTask), validationTask);
			new TaskRunner().setOnErrorAction((task, ex) -> {
				log.info("Setting error=true because of " + ex.getMessage() + ex.getClass().getName());
				ex.printStackTrace();
				error = true;
			}).setOnFinishAction((finishedTasks) -> {
				btc.deplyomentBuilt(d);
			}).exec(pool, tasks);
		});
	}
	public NodeBuilder getChangeLogXml() {
		return changeLogSourceXml;
	}
	public Set<Deployment> getChangedDeployments() {
		return changedDeployments;
	}
}
