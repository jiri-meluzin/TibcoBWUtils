package com.meluzin.tibcobwutils.earcomparer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.Lists;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.SDKPropertiesLoader;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class FullInstallSetParallel {
	public static void main(String[] args) {

		try {
			System.out.println(Lists.asList(args));
			ArgumentParser argParser = ArgumentParsers.newArgumentParser("Full install set", true, "-").description("Compares deployed and built ears and makes set of ears for installation");
			argParser.addArgument("-deployed").type(String.class).required(true).help("Path to deployed ears. Ex: /tib/app/jenkins2/home/userContent/TIBCO/_deployed/ENV_EAI_TST");
			argParser.addArgument("-built").type(String.class).required(true).help("Path to built ears. Ex: /tib/app/jenkins2/home/userContent/TIBCO/R180217_miloslav");
			argParser.addArgument("-init-built").type(String.class).required(true).help("Path to initially built ears: Ex: /tib/app/jenkins2/workspace/workspace/R180217_miloslav/init-built");
			argParser.addArgument("-domain").type(String.class).required(true).help("Name of domain: Ex: ENV_EAI_TST2");
			argParser.addArgument("-env").type(String.class).required(true).help("Name of env, usually same as domain except TED instance, where it is extended with TED isntance name: Ex: ENV_EAI_TED_cardik");
			argParser.addArgument("-tibcohome").type(String.class).required(true).help("Path to tibco home. Ex: /tib/app/tibco");
			argParser.addArgument("-output").type(String.class).required(true).help("Output path - changed EARs will be copied there. Ex: /tib/app/jenkins2/home/userContent/TIBCO/R180217_miloslav/ENV_EAI_TST2");
			argParser.addArgument("-earmask").type(String.class).required(false).help("EAR mask - restricts comparing only for given mask. Ex: *");
			
			Namespace res = argParser.parseArgsOrFail(args);
			Path deployed = Paths.get(res.getString("deployed"));
			Path built = Paths.get(res.getString("built"));
			Path initBuilt = Paths.get(res.getString("init_built"));
			String domain = res.get("domain");
			String env = res.get("env");
			Path tibcoHome = Paths.get(res.getString("tibcohome") == null ? "t:/tib/app/tibco" : res.getString("tibcohome"));
			Path output = Paths.get(res.getString("output"));
			String mask = res.getString("earmask") == null ? "*" : res.getString("earmask").replace("'", "");
			
			new FullInstallSetParallel(deployed, built, initBuilt, domain, env, tibcoHome, output, mask).compare();
			
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
	}
	private Path deployed;
	private Path built;
	private Path initBuilt;
	private String domain;
	private String env;
	private Path tibcoHome;
	private Path output;
	private String mask;
	public FullInstallSetParallel(Path deployed, Path built, Path initBuilt, String domain, String env, Path tibcoHome, Path output, String mask) {
		this.deployed = deployed;
		this.built = built;
		this.initBuilt = initBuilt;
		this.domain = domain;
		this.env = env;
		this.tibcoHome = tibcoHome;
		this.output = output;
		this.mask = mask;
	}
	
	public void compare() {
		
		SDKPropertiesLoader loader = new SDKPropertiesLoader(tibcoHome);
		List<CompareResult> result;
		ConfigComparer configComparer = new ConfigComparer(loader);
		
		List<Path> builtEars = new FileSearcher().searchFiles(built, "glob:**/*"+mask+"*.ear", false);
		List<Path> initBuiltEars = new FileSearcher().searchFiles(initBuilt, "glob:**/*"+mask+"*.ear", false);
		
		Set<String> finalSet = builtEars.stream().map(p -> p.getFileName().toString()).collect(Collectors.toSet());
		initBuiltEars.stream().filter(p -> !finalSet.contains(p.getFileName().toString())).forEach(p -> builtEars.add(p));
		Path builtConfigPath = built.resolve("_config");
		Path configPath = builtConfigPath.resolve(env);
		Path deployedDomainDir = deployed/*.resolve(domain)*/;
		Path deploymentListPath = builtConfigPath.resolve("deploymentList.xml");
		NodeBuilder deploymentList = new XmlBuilderFactory().loadFromFile(deploymentListPath);

		if (!deployedDomainDir.toFile().exists()) {
			System.out.println("Missing deployed directory: " + deployedDomainDir);
			System.exit(1);
		}
		if (!built.toFile().exists()) {
			System.out.println("Missing built directory: " + built);
			System.exit(1);
		}
		
		System.out.println("Generating version files...");
		EARVersionExtractor.main(new String[] { "-directory", built.toAbsolutePath().toString(), "-out", "FILES" });
		EARVersionExtractor.main(new String[] { "-directory", initBuilt.toAbsolutePath().toString(), "-out", "FILES" });
		System.out.println("Done.");
		System.out.println("Checking changes in EARs...");
		
		builtEars.parallelStream().forEach(builtEar -> {
			String earName = builtEar.getFileName().toString().replace(".ear", "");
			String configName = earName + ".xml";
			String versionName = earName + ".version";
			Path fullConfigBuilt = configPath.resolve(configName);
			Path versionBuilt = builtEar.getParent().resolve(versionName);
			Path fullConfigDeployed = deployedDomainDir.resolve(configName);
			Path deployedEar = deployedDomainDir.resolve(builtEar.getFileName());
			boolean shouldBeInstallOnThisDomain = deploymentShouldBeInstalled(deploymentList, earName);
			if (shouldBeInstallOnThisDomain) {
				if (!deployedEar.toFile().exists()) {
					System.out.println(builtEar.getFileName() + ": new ear on " + domain);
					copyEarToOutput(builtEar, versionBuilt, fullConfigBuilt);
				} else {
					List<CompareResult> earCompareResult = new  EARComparer(true).compare(deployedEar, builtEar);
					boolean earDiffers = differs(earCompareResult);
					
					if (earDiffers) {
						outputAndCopy(builtEar, versionBuilt, fullConfigBuilt, earCompareResult);
					} else {
						List<CompareResult> fullConfigCompareResult = configComparer.compareFullConfig(fullConfigBuilt, fullConfigDeployed);
						boolean fullConfigDiffers = differs(fullConfigCompareResult);
						if (fullConfigDiffers) {
							outputAndCopy(builtEar, versionBuilt, fullConfigBuilt, fullConfigCompareResult);				
						} else {
							System.out.println(builtEar.getFileName() + ": no change " + domain);
						}
					}		
				}
			} else {
				System.out.println(builtEar.getFileName() + ": should not be installed on " + domain);
			}
		});
		System.out.println("Done.");
	}

	public void outputAndCopy(Path builtEar, Path builtEarVersion, Path fullConfigBuilt, List<CompareResult> fullConfigCompareResult) {
		synchronized (this) {
			fullConfigCompareResult.forEach(r -> {
				System.out.println(builtEar.getFileName()+"\t"+r);
			});	
		}
		copyEarToOutput(builtEar, builtEarVersion, fullConfigBuilt);
	}

	public void copyEarToOutput(Path builtEar, Path builtEarVersion, Path fullConfigBuilt) {
		copyFile(builtEar);
		copyFile(builtEarVersion);
		copyFile(fullConfigBuilt);
	}

	public void copyFile(Path fileToCopy) {
		File outputFile = output.resolve(fileToCopy.getFileName()).toFile();
		try {			
			outputFile.getParentFile().mkdirs();
			FileUtils.copyFile(fileToCopy.toFile(), outputFile);
		} catch (IOException e) {
			throw new RuntimeException("Could not copy file "+fileToCopy+" to output " + outputFile, e);
		}
	}

	public boolean deploymentShouldBeInstalled(NodeBuilder deploymentList, String earName) {
		return deploymentList.
			search(node -> "deployment".equals(node.getName()) && earName.equals(node.getAttribute("name"))).
			anyMatch(deployment -> 
				deployment.search(true, "environment").anyMatch(environment -> 
					domain.equals(environment.getAttribute("domain"))
					)
			);
	}

	public boolean differs(List<CompareResult> compare) {
		return compare.stream().anyMatch(r -> r.getStatus() != CompareResultStatus.Equals);
	}
	
}
