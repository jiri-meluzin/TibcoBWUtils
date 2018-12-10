package com.meluzin.tibcobwutils.build.changetracker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.Lists;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V2;
import com.meluzin.functional.T.V3;
import com.meluzin.functional.T.V4;
import com.meluzin.tibcobwutils.build.deploymentbuild.Deployment;
import com.meluzin.tibcobwutils.build.deploymentbuild.DeploymentLoader;
import com.meluzin.tibcobwutils.build.deploymentbuild.Library;
import com.meluzin.tibcobwutils.earcomparer.CompareResultStatus;
import com.meluzin.tibcobwutils.earcomparer.EARComparer;
import com.meluzin.tibcobwutils.earcomparer.EARVersionExtractor;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class ChangeTracker {
	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("ChangeTracker", true, "-")
				.description("Tracks changes in EAR and match them with SVN.");
		argParser.addArgument("-svn-log").type(String.class).required(true).help("Path to full SVN log of given branch: ex: /tib/app/jenkins2/home/workspace/R180518_ivo/svn-log.xml");
		argParser.addArgument("-branch").type(String.class).required(true).help("Name of branch: ex: R180518_ivo");
		argParser.addArgument("-artifacts").type(String.class).required(true).help("Path to artifacts dir: ex: /tib/app/jenkins2/home/workspace/R180518_ivo/change");
		argParser.addArgument("-source").type(String.class).required(true).help("Path to source dir: ex: /tib/app/jenkins2/home/workspace/R180518_ivo/source");
		argParser.addArgument("-init-build").type(String.class).required(true).help("Path to init build dir: ex: /tib/app/jenkins2/home/workspace/R180518_ivo/init-build");
		
		Namespace res = argParser.parseArgsOrFail(args);

		
		
		
		String BRANCH = res.getString("branch");
		Path svnLogPath = Paths.get(res.getString("svn_log"));
		Path ARTIFACTS_DIR = Paths.get(res.getString("artifacts"));
		Path SOURCE_DIR = Paths.get(res.getString("source"));
		Path INIT_BUILD_DIR = Paths.get(res.getString("init_build"));
		Path deploymentListPath = SOURCE_DIR.resolve("_config").resolve("deploymentList.xml");
		

		List<V3<Path, String, List<V4<String, String, String, String>>>> earModifications = loadEarChanges(BRANCH,
				svnLogPath, ARTIFACTS_DIR, SOURCE_DIR, INIT_BUILD_DIR, deploymentListPath);
		NodeBuilder changes = new XmlBuilderFactory().createRootElement("changes");
		earModifications.forEach(ear -> {
			changes.addChild("ear").
				addAttribute("name", ear.getA()).
				addAttribute("version", ear.getB()).
				addChild("changes").
					addChildren(ear.getC(), (a,b) -> {
						String[] split = a.getD() == null ? new String[0]  : a.getD().split(";");
						String msg = split.length < 3 ? null : Arrays.asList(Arrays.copyOfRange(split, Math.min(3, split.length - 1	), split.length)).stream().collect(Collectors.joining(";"));
						String branch = split.length < 1 ? null : split[0];
						String BUS = split.length < 2 ? null : split[1];
						String US = split.length < 4 ? null : split[2];
						b.addChild("change").
							addAttribute("revision", a.getA()).
							addAttribute("updated", a.getB()).
							addAttribute("author", a.getC()).
							addAttribute("branch", branch).
							addAttribute("BUS", BUS).
							addAttribute("US", US).
							setTextContent(msg);
					});
		});
		
		System.out.println(changes);
		
//		System.out.println(
//				earModifications.stream().
//					filter(v -> v.getC().size() > 0).
//					map(v -> v.getA() + " (" + v.getB() + ")" + "\n\t" + v.getC().stream().map(s -> s.toString()).collect(Collectors.joining("\n\t"))).
//					collect(Collectors.joining("\n"))
//		);
		
//		List<String> svnLogForFile = getSVNLogForFile(loadDeployments, ear, changedFile, svnBranchPath, svnLogPath);
		
	//	System.out.println(svnLogForFile);
		
		
	}

	public static List<V3<Path, String, List<V4<String, String, String, String>>>> loadEarChanges(String BRANCH,
			Path svnLogPath, Path ARTIFACTS_DIR, Path SOURCE_DIR, Path INIT_BUILD_DIR, Path deploymentListPath) {
		XmlBuilderFactory fac = new XmlBuilderFactory();
		NodeBuilder svnLog = fac.loadFromFile(svnLogPath);
		NodeBuilder deploymentList = fac.loadFromFile(deploymentListPath);
		Path svnBranchPath = Paths.get("/TIBCO/Branches").resolve(BRANCH);
		Set<Deployment> loadDeployments = new DeploymentLoader().loadDeployments(SOURCE_DIR);
		
		
		Set<String> availableProdEars = loadDeployments.
				stream().
				map(d -> d.getDeclaredArchives().stream().map(s -> s.getFileName().toString().replace(".archive", ""))).flatMap(s -> s).
				filter(d -> deploymentList.
						search(true, "deployment").
						anyMatch(dn -> d.equals(dn.getAttribute("name")) && dn.hasChild(true, n -> "environment".equals(n.getName()) && Lists.asList("ENV_EAI_PRD", "ENV_DMZ_EI_PRD").contains(n.getAttribute("domain"))))).
				collect(Collectors.toSet());
		
		
		// Deployment -> [(Status, Path, [(Revision, Author, Msg)...])...]
		Map<Path, List</*V1</*V3<CompareResultStatus, Path,*/ /*List<*/T.V4<String, String, String, String>>/*>>*/> fileModifications = getEarStream(ARTIFACTS_DIR, INIT_BUILD_DIR).
			filter(v -> availableProdEars.contains(v.getA().getFileName().toString().replace(".ear", ""))).
			map(v -> T.V(v.getA(), v.getB(), new EARComparer().compare(v.getB(), v.getA()))).
			filter(c -> c.getC().size() > 0  && c.getC().get(0).getStatus() != CompareResultStatus.Equals).
			sorted((a,b) -> a.getA().compareTo(b.getA())).
			map(differentEAR -> 
				T.V(
					differentEAR.getA() , 
					differentEAR.getC().
						stream().
						map(diff -> /*T.V(/*diff.getStatus(),  diff.getFile() ,*/ 
							getSVNLogForFile(BRANCH, svnLog, loadDeployments, differentEAR.getA(), diff.getFile(), svnBranchPath).stream()/*)*/
						).
						flatMap(s -> s).
						collect(Collectors.toList())
					)
			).collect(Collectors.toMap(v -> v.getA(), v-> v.getB()));;
		
		Map<Path, List<V4<String, String, String, String>>> configModifications = new FileSearcher().searchFiles(SOURCE_DIR.resolve("_config"), "glob:**/*.xml", true).
			stream().
			filter(v -> availableProdEars.contains(v.getFileName().toString().replace(".xml", ""))).
			filter(p -> Arrays.asList("ENV_EAI_PRD","ENV_DMZ_EI_PRD").contains(p.getParent().getFileName().toString())).
			map(p -> T.V(ARTIFACTS_DIR.resolve(p.getFileName().toString().replace(".xml", ".ear")), p)).
			filter(v -> v.getA().toFile().exists()).
			map(p -> T.V(p.getA(), searchChangeInLog(BRANCH, svnLog, new HashSet<String>(Arrays.asList(svnBranchPath.resolve("_config").resolve(p.getB().getParent().getFileName()).resolve(p.getB().getFileName()).normalize().toString().replace("\\", "/")))))).//getSVNLogForFile(svnLog, loadDeployments, p.getA(), p.getB(), svnBranchPath))).
			filter(v -> v.getB().size() > 0).
			collect(Collectors.toMap(v -> v.getA(), v -> v.getB()));
		
		//System.out.println(configModifications.stream().map(v -> v.getA() + "\n\t" + v.getB().stream().map(vv -> vv.toString()).collect(Collectors.joining("\n\t"))).collect(Collectors.joining("\n")));
		//System.out.println(fileModifications);	
		
		Map<Path, List<V4<String, String, String, String>>> allChanges = Stream.of(fileModifications, configModifications).
				flatMap(m -> m.entrySet().stream()).
				collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a,b) -> Stream.concat(a.stream(), b.stream()).collect(Collectors.toList())));
		
		List<V3<Path, String, List<V4<String,String,String,String>>>> earModifications = allChanges.entrySet().stream().
			
			map(v -> T.V(
					v.getKey().getFileName(), 
					EARVersionExtractor.getVersionFromEAR(v.getKey()).getB(), 
					//Stream.concat(
							v.getValue().stream()/*.map(c -> c.getA().stream()).flatMap(s -> s)/*, 
							getConfigModications(configModifications, v)
						)*/.
						distinct().					
						sorted((a,b) -> a.toString().compareTo(b.toString())).
						collect(Collectors.toList())
					)
				).
			filter(v -> v.getC().size() > 0).
			sorted((a,b) -> a.getA().compareTo(b.getA())).
			collect(Collectors.toList());
		return earModifications;
	}

	public static Stream<V4<String, String, String, String>> getConfigModications(
			Map<Path, List<V4<String, String, String, String>>> configModifications,
			V2<Path, List<V3<CompareResultStatus, Path, List<V4<String, String, String, String>>>>> v) {
		try {
			List<V4<String, String, String, String>> modifications = configModifications.get(v.getA());
			return modifications == null ? Stream.empty() : modifications.stream();
		} catch (Exception e) {
			throw e;
		}
	}

	public static Stream<V2<Path, Path>> getEarStream(Path ARTIFACTS_DIR, Path INIT_BUILD_DIR) {
		return new FileSearcher().searchFiles(ARTIFACTS_DIR, "glob:**/*.ear", false).
			stream().parallel().map(p -> T.V(p, INIT_BUILD_DIR.resolve(p.getFileName())));
	}

	public static List<T.V4<String, String, String, String>> getSVNLogForFile(String branch, NodeBuilder svnLog, Set<Deployment> loadDeployments, Path ear, Path changedFile,
			Path svnBranchPath) {
		//System.out.println(ear + " " + changedFile);
		String[] split = changedFile.toString().replace("\\", "/").split("!");
		String relativeFilePath = split[split.length - 1].substring(1);
		
		Deployment depl = getDeploymentFromEAR(loadDeployments, ear);
		List<Deployment> listOfDependentDeployments = getDeploymentTree(loadDeployments, depl);
		
		
		Set<String> svnPathsToSearch = listOfDependentDeployments.
				stream().
				map(d -> svnBranchPath.resolve(d.getName()).resolve(relativeFilePath).normalize().toString().replace("\\", "/")).
				collect(Collectors.toSet());

		//System.out.println(svnPathsToSearch);
		List<T.V4<String, String, String, String>> svnLogForFile = searchChangeInLog(branch, svnLog, svnPathsToSearch);
		//System.out.println(svnLogForFile);
		return svnLogForFile;
	}

	public static List<T.V4<String, String, String, String>> searchChangeInLog(String branch, NodeBuilder svnLog, Set<String> svnPathToSearch) {
		List<T.V4<String, String, String, String>> svnLogForFile = svnLog.
				search(true, "path").
				filter(n -> svnPathToSearch.contains(n.getTextContent())).
				map(n -> n.getParent().getParent()).
				map(n -> T.V(n.getAttribute("revision"), n.searchFirstByName("date").getTextContent(), n.searchFirstByName("author").getTextContent(), getMessage(n))).
				filter(v -> v.getD() == null || v.getD().startsWith(branch + ";") || v.getD().startsWith(branch.split("_")[0] + ";")).
				collect(Collectors.toList());
		return svnLogForFile;
	}

	public static String getMessage(NodeBuilder logEntry) {
		String textContent = logEntry.searchFirstByName("msg").getTextContent();
		return textContent == null ? null : textContent.trim();
	}

	public static Deployment getDeploymentFromEAR(Set<Deployment> loadDeployments, Path ear) {
		Optional<V2<Deployment, Path>> deployment = loadDeployments.stream().
			map(d -> d.getDeclaredArchives().stream().map(a -> T.V(d, a))).
			flatMap(s -> s).
			filter(v -> v.getB().getFileName().toString().replace(".archive", ".ear").equals(ear.getFileName().toString())).
			findFirst();

		Deployment depl = deployment.get().getA();
		return depl;
	}

	public static List<Deployment> getDeploymentTree(Set<Deployment> loadDeployments, Deployment depl) {
		Stack<Deployment> toSearch = new Stack<Deployment>();
		List<Deployment> found = new ArrayList<Deployment>();
		toSearch.add(depl);
		found.add(depl);
		while (!toSearch.isEmpty()) {
			List<Deployment> dependentDeployments = getDependentDeployments(loadDeployments, toSearch.pop());
			toSearch.addAll(dependentDeployments.stream().filter(d -> !found.contains(d)).collect(Collectors.toList()));
			found.addAll(dependentDeployments);
		}
		return found;
	}

	public static List<Deployment> getDependentDeployments(Set<Deployment> loadDeployments,
			Deployment deployment) {
		List<Library> dependencies = deployment.getDependencies();
		List<Deployment> dependentDeployments = dependencies.stream().map(dl -> loadDeployments.stream().filter(d -> d.getDeclaredLibraries().contains(dl)).findFirst().get()).collect(Collectors.toList());
		return dependentDeployments;
	}
}
