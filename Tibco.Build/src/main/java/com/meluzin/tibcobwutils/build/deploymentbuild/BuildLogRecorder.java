package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.Log;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V2;

public class BuildLogRecorder {
	private static Logger log = Log.get();

	private Map<Deployment, List<BinExecutor>> validationLogs = new HashMap<>();
	private Map<Deployment, List<Path>> deploymentArchive = new HashMap<>();
	private Map<Path, BinExecutor> archiveLogs = new HashMap<>();
	private Map<Library, BinExecutor> projlibLogs = new HashMap<>();
	private Map<Deployment, List<Library>> deploymentLibrary = new HashMap<>();

	private Date started = new Date();
	private Date finished = new Date();
	
	public BuildLogRecorder start() {
		this.started = new Date();
		return this;
	}
	
	public BuildLogRecorder finish() {
		this.finished = new Date();
		return this;
	}
	
	public synchronized BuildLogRecorder addDeploymentValidation(Deployment deployment, BinExecutor log) {
		initMaps(deployment);
		this.validationLogs.get(deployment).add(log);
		return this;
	}
	public synchronized BuildLogRecorder addDeploymentArchiveBuild(Deployment deployment, Path archive, BinExecutor log) {
		initMaps(deployment);
		this.deploymentArchive.get(deployment).add(archive);
		this.archiveLogs.put(archive, log);
		return this;
	}
	public synchronized BuildLogRecorder addDeploymentLibraryBuild(Deployment deployment, Library library, BinExecutor log) {
		initMaps(deployment);
		this.deploymentLibrary.get(deployment).add(library);
		this.projlibLogs.put(library, log);
		return this;
	}
	private void initMaps(Deployment deployment) {
		if (!this.deploymentArchive.containsKey(deployment)) {
			this.deploymentArchive.put(deployment, new ArrayList<>());
		}
		if (!this.deploymentLibrary.containsKey(deployment)) {
			this.deploymentLibrary.put(deployment, new ArrayList<>());
		}
		if (!this.validationLogs.containsKey(deployment)) {
			this.validationLogs.put(deployment, new ArrayList<>());
		}
	}
	
	public NodeBuilder generateResult(BuildContext buildBranchParallel) {
		NodeBuilder result = initResult();
		if (buildBranchParallel.getJobNumber().isPresent()) {
			result.addAttribute("job", buildBranchParallel.getJobNumber().get());
		}
		result.appendChild(buildBranchParallel.getChangeLogXml().copy().setName("sourceRepoLog"));
		result.addChild("changedDeployments").addChildren(buildBranchParallel.getChangedDeployments(),(d,n)->n.addChild("deployment").setTextContent(d.getName()).addAttribute("path", d.getPath()));
		Set<Deployment> deployments = Stream.concat( Stream.concat( validationLogs.keySet().stream(), deploymentArchive.keySet().stream()), deploymentLibrary.keySet().stream()).distinct().collect(Collectors.toSet());
		
		result.addChildren(deployments.stream().sorted((a,b) -> a.getName().compareTo(b.getName())), (d, p) -> {
			NodeBuilder deployment = p.addChild("deployment").addAttribute("name", d.getName());
			deployment.addChild("libraries").addChildren(d.getDeclaredLibraries(), (lib, dp) -> {
				NodeBuilder library = dp.addChild("library").addAttribute("name", lib.getName()).addAttribute("changed", buildBranchParallel.getChangedLibraries().contains(lib));
				if (projlibLogs.containsKey(lib)) {
					BinExecutor be = projlibLogs.get(lib);
					addBinExecItem(library, be);
				}
			});		
			if (deploymentArchive.containsKey(d)) {	
				deployment.addChild("archives").addChildren(deploymentArchive.get(d), (archive, dp) -> {
					NodeBuilder library = dp.addChild("archive").addAttribute("name", archive.getFileName());
					if (archiveLogs.containsKey(archive)) {
						BinExecutor be = archiveLogs.get(archive);
						addBinExecItem(library, be);
					}
				});		
			}
			if (validationLogs.containsKey(d)) {
				List<BinExecutor> be = validationLogs.get(d);
				
				BuildLogAnalyzer buildLogAnalyzer = createBuildLogAnalyzer();
				BinExecutor b = buildLogAnalyzer.getCodeValidation(be);
				NodeBuilder validation = deployment.addChild("validation-code").addAttribute("hasError", buildLogAnalyzer.containsCodeValidationErrors(be));
				//be.forEach(action);
				addBinExecItem(validation, b);
				b = buildLogAnalyzer.getConfigValidation(be);
				validation = deployment.addChild("validation-config").addAttribute("hasError", buildLogAnalyzer.containsConfigValidationErrors(be));
				//be.forEach(action);
				addBinExecItem(validation, b);				
			};		
		});
		
		return result;
	}
	public List<String> generateValidationResult() {
		
		Set<Deployment> deployments = getDeployments();
		BuildLogAnalyzer bla = createBuildLogAnalyzer();
		return
			deployments.stream().
				filter(d -> validationLogs.containsKey(d)).
				map(d -> T.V(d, validationLogs.get(d))).
				//flatMap(v -> v).
				filter( v -> bla.containsCodeValidationErrors(v.getB()) || bla.containsConfigValidationErrors(v.getB())).
				map(v -> extractLines(v).stream().map(line -> v.getA().getName() + ": " + line)).
				flatMap(s -> s).
				//sorted().
				collect(Collectors.toList());
	}

	protected BuildLogAnalyzer createBuildLogAnalyzer() {
		return new BuildLogAnalyzer();
	}

	private List<String> extractLines(V2<Deployment, List<BinExecutor>> v) {
		BuildLogAnalyzer bla = createBuildLogAnalyzer();
		return v.getB().stream().map(vv -> bla.isCodeValidation(vv) || bla.isConfigValidation(vv) ? splitErrorOutputToList(vv) :  new ArrayList<String>()).map(l -> l.stream()).flatMap(l -> l).collect(Collectors.toList());
	}

	private List<String> splitErrorOutputToList(BinExecutor vv) {
		return Arrays.asList(vv.getStdError() == null ? new String[0] : vv.getStdError().split("\n"));
	}

	private Set<Deployment> getDeployments() {
		Set<Deployment> deployments = Stream.concat( Stream.concat( validationLogs.keySet().stream(), deploymentArchive.keySet().stream()), deploymentLibrary.keySet().stream()).distinct().collect(Collectors.toSet());
		return deployments;
	}

	/*private void getActions() {
		Map<BinExecutor, Deployment> actions = 
				Stream.concat(validationLogs.entrySet().stream().map(a -> T.V(a.getKey(), a.getValue())), 
						Stream.concat(archiveLogs.entrySet().stream().map(a -> T.V(deploymentArchive.get(a.getKey()), a.getValue())), 
								projlibLogs.entrySet().stream().map(a -> T.V(deploymentLibrary.get(a.getKey()), a.getValue())))).
				map(a -> { Map<BinExecutor, Deployment> m = new HashMap<BinExecutor, Deployment>(); m.put(a.getB(), (Deployment) a.getA()); return m;}).
				reduce(new HashMap<>(), (a,b) -> {
					Map<BinExecutor, Deployment> m = new HashMap<>();
					m.putAll(a);
					m.putAll(b);
					return m;
				});
	}*/

	private NodeBuilder initResult() {
		XmlBuilderFactory fac = new XmlBuilderFactory();
		Date from = started;
		Date to = finished == null ? new Date() : finished;
		Duration duration = Duration.ofMillis(to.getTime() - from.getTime());
		
		NodeBuilder result = fac.createRootElement("buildLog").
				addAttribute("started", started).
				addAttribute("finished", finished).
				addAttribute("duration", duration);
		return result;
	}
	private void addBinExecItem(NodeBuilder library, BinExecutor be) {
		if (be == null) {
			library.addAttribute("disabled", true);
		} else {
			library.addAttribute("started", be.getStarted());
			library.addAttribute("finished", be.getFinished());
			library.addAttribute("cmd", be.getExec());
			library.addAttribute("returnCode", be.getReturnCode());
			library.addChild("error").setTextContent(be.getStdError());
			library.addChild("out").setTextContent(be.getStdOutput());
		}
	}
	
}
