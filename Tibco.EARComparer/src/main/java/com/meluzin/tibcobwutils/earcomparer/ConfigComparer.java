package com.meluzin.tibcobwutils.earcomparer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.Lists;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.SDKPropertiesLoader;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class ConfigComparer {
	private SDKPropertiesLoader loader;
	public ConfigComparer(SDKPropertiesLoader loader) {
		this.loader = loader;
	}
	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("Config Comparer", true, "-")
				.description("Compares two config files . Returns 0 when ear are equal, 1 when ear are different, on error 2");
		argParser.addArgument("-old").type(String.class).required(false).help("Path to old ear. Ex: T:/Source/R160729/AP_API_IO_TST1.xml");
		argParser.addArgument("-new").type(String.class).required(true).help("Path to new ear. Ex: T:/Source/R160924/AP_API_IO_TST1.xml");
		argParser.addArgument("-mode").choices("FULL-FULL", "UPDATE-UPDATE", "CHECK").type(String.class).required(true).help("CompareMode - FU");
		argParser.addArgument("-env").type(String.class).required(false).help("Env part - TST, TST2, PRD");
		argParser.addArgument("-verbose").type(String.class).required(false).help("Quiet output");
		argParser.addArgument("-tibcohome").type(String.class).required(false).help("Path to tibco home. Ex: T:/tib/app/tibco");

		
		Namespace res = argParser.parseArgsOrFail(args);
		try {
			String oldPath = res.get("old");
			String newPath = res.get("new");
			String env = res.get("env");
			String mode = res.get("mode");
			String verbose = res.get("verbose");
			Path tibcoHome = Paths.get(res.getString("tibcohome") == null ? "t:/tib/app/tibco" : res.getString("tibcohome"));
			SDKPropertiesLoader loader = new SDKPropertiesLoader(tibcoHome);
			List<CompareResult> result;
			ConfigComparer configComparer = new ConfigComparer(loader);
			Path config = Paths.get(newPath);
			switch (mode) {
				case "FULL-FULL":
					result = configComparer.compareFullConfig(Paths.get(oldPath), config);
					break;
				case "CHECK":
					boolean empty = configComparer.isUpdateEmpty(config, env);
					if (empty) {
						result = Lists.asList(new CompareResult(config, CompareResultStatus.Equals, "Update config is empty"));						
					} else {
						result = Lists.asList(new CompareResult(config, CompareResultStatus.DifferentContent, "Update config is not empty"));
					}
					break;
				case "UPDATE-UPDATE":
					result = configComparer.compareUpdates(Paths.get(oldPath), config, env);
					break;
				default:
					System.err.println("Unknown mode: " + mode);
					System.exit(2);
					return;				
			}
			if (!"quiet".equals(verbose) || result.get(0).getStatus() != CompareResultStatus.Equals) System.out.println(result);
			if (result.get(0).getStatus() == CompareResultStatus.Equals) {
				System.exit(0);
			} else {
				System.exit(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
		
		
	}
	public boolean isUpdateEmpty(Path config, String env) {
		XmlBuilderFactory fac = new XmlBuilderFactory();
		NodeBuilder n = fac.loadFromFile(config);
		if (isUpdateConfig(n)) {
			throw new RuntimeException("Given file ("+config+") is not update config, does not contain applicationConfiguration root element");
		}
		NodeBuilder envNode = n.searchFirst(true, en -> "env".equals(en.getName()) && env.equals(en.getTextContent()));
		if (envNode == null) return true;
		NodeBuilder app = envNode.getParent().searchFirstByName("application");
		if (app == null) return true;
		if (app.getChildren().size() == 0) return true;
		NodeBuilder nvPairs = app.getChildren().get(0);
		if (nvPairs.getChildren().size() == 0 || nvPairs.getChildren().size() == 1 && nvPairs.getChildren().get(0).isTextNode()) return true;
		return false;
		
	}
	private boolean isUpdateConfig(NodeBuilder n) {
		return !n.getName().equals("applicationConfiguration");
	}
	public List<CompareResult> compareUpdates(Path config1, Path config2, String env) {
		NodeBuilder nvPairs1 = loadAndSortUpdateConfig(config1, env);
		NodeBuilder nvPairs2 = loadAndSortUpdateConfig(config2, env);
		if (nvPairs1.equalsTo(nvPairs2)) {
			return Lists.asList(new CompareResult(config1, CompareResultStatus.Equals, config1 + " = " + config1));
		}
		else {
			return Lists.asList(new CompareResult(config1, CompareResultStatus.DifferentContent, config1 + " != " + config2));			
		}
	}
	public List<CompareResult> compareFullConfig(Path config1, Path config2) {
		NodeBuilder n1 = loadFullConfig(config1);
		NodeBuilder n2 = loadFullConfig(config2);
		if (n1.equalsTo(n2)) {
			return Lists.asList(new CompareResult(config1, CompareResultStatus.Equals, config1 + " = " + config1));
		}
		else {
			return Lists.asList(new CompareResult(config1, CompareResultStatus.DifferentContent, config1 + " != " + config2 + " " + n2.getFirstDiff(n1)));			
		}
	}
	private NodeBuilder loadFullConfig(Path config1) {
		return new NormalizeConfig(loader).loadFullConfig(config1, true);
	}
	private NodeBuilder loadAndSortUpdateConfig(Path config1, String env) {
		return new NormalizeConfig(loader).loadConfigPart(config1, env);
	}
}
