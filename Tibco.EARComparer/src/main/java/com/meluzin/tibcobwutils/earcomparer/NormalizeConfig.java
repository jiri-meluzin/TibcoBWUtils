package com.meluzin.tibcobwutils.earcomparer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.Lists;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V2;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.PasswordDecrypter;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.FullConfigsModel;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.SDKPropertiesLoader;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class NormalizeConfig {
	private SDKPropertiesLoader loader;
	public NormalizeConfig(SDKPropertiesLoader loader) {
		this.loader = loader;
	}

	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("Config normalizer", true, "-")
				.description("Normalizes full config XML - orders elements");
		argParser.addArgument("-config").type(String.class).required(true).help("Path to config. Ex: T:/Source/R160729/AP_API_IO_TST1.xml");
		argParser.addArgument("-out").type(String.class).required(false).help("Path to config. Ex: T:/Source/R160729/AP_API_IO_TST1.xml");
		argParser.addArgument("-tibcohome").type(String.class).required(false).help("Path to tibco home. Ex: T:/tib/app/tibco");
		
		Namespace res = argParser.parseArgsOrFail(args);
		Path config = Paths.get(res.getString("config"));
		Path outPath = null;
		Path tibcoHome = Paths.get(res.getString("tibcohome") == null ? "t:/tib/app/tibco" : res.getString("tibcohome"));
		SDKPropertiesLoader loader = new SDKPropertiesLoader(tibcoHome);
		if (res.get("out") != null) {
			outPath = Paths.get(res.getString("out"));
		}
		NodeBuilder out = new NormalizeConfig(loader).loadFullConfig(config, true);
		if (outPath != null) new XmlBuilderFactory().renderNode(out, outPath);
		else System.out.println(out);
	}
	
	public NodeBuilder loadFullConfig(Path config1, boolean decrypt) {
		XmlBuilderFactory fac = new XmlBuilderFactory().setPreserveWhitespace(false);
		NodeBuilder n1 = fac.loadFromFile(config1);
		n1.search(true, p -> "NVPairs".equals(p.getName())).forEach(p -> { 
			if ("binding".equals(p.getParent().getName())) {
				p.addAttribute("name", "INSTANCE_RUNTIME_VARIABLES");				
			}
			p.sortChildren(sortNVPairs()); 
			if (decrypt) {
				EARComparer.decryptNVPasswords(p);
			}
		});
		n1.search(true, "bw").forEach(bw -> {
			String version = FullConfigsModel.getServiceProductVersion(bw).findFirst().get();
			normalizeBWSDKVariables(bw, version);
			
			bw.sortChildren((a,b) -> {
				int ret = a.getName().compareTo(b.getName());
				if (ret == 0) {
					return EARComparer.compareAttributeValue(a, b, "name");
				}
				return ret;
			});
		});
		n1.search(true, p -> "bwprocesses".equals(p.getName())).forEach(p -> {
			p.sortChildren(sortBwprocesses());
		});
		if (decrypt) {
			n1.search(true, p -> "password".equals(p.getName())).forEach(p -> p.setTextContent(new PasswordDecrypter().decrypt(p.getTextContent())));
		}
		n1.search(true, n -> Lists.asList("httpRepoInstance", "rvRepoInstance", "checkpoints").contains(n.getName())).
			collect(Collectors.toList()).
			forEach(n -> n.getParent().removeChild(n));		
		return n1;
	}

	public void normalizeBWSDKVariables(NodeBuilder bw) {
		String version = loader.getBwPropertiesNames().keySet().stream().map(v -> Float.parseFloat(v)).sorted().findFirst().get().toString();
		normalizeBWSDKVariables(bw, version);
	}

	public void normalizeBWSDKVariables(NodeBuilder bw, String version) {
		Set<V2<String, String>> availableSDKVariables = loader.getBwPropertiesNames().get(version);
		if (availableSDKVariables == null) {
			throw new IllegalArgumentException("Cannot find BW SDK Properties for version " + version + " at " + loader.getTibcoBWPath());
		}
		List<NodeBuilder> variableWithDefaultValue = bw.search(true, "name").
			map(n -> T.V(n, availableSDKVariables.stream().filter(v -> v.getA().equals(n.getTextContent())).findFirst())).
			filter(v -> v.getB().isPresent()).
			filter(v -> sdkVariableHasSameValue(v)).
			map(v -> v.getA().getParent()).
			collect(Collectors.toList());
		variableWithDefaultValue.forEach(v -> v.getParent().removeChild(v));
	}

	private boolean sdkVariableHasSameValue(V2<NodeBuilder, Optional<V2<String, String>>> v) {
		String defaultValue = v.getB().get().getB();
		NodeBuilder valueElement = v.getA().getParent().searchFirstByName("value");
		return defaultValue == valueElement.getTextContent() || defaultValue.equals(valueElement.getTextContent());
	}
	
	public NodeBuilder removeRedundants(NodeBuilder fullConfig) {
//		fullConfig.search(true, "NVPairs").filter(n -> "Global Variables".equals(n.getAttribute("name"))).map(n -> n.getChildren().stream())..collect(Collectors.toMap(n -> n.search, valueMapper));
		return fullConfig;
	}
	
	private Comparator<NodeBuilder> sortNVPairs() {
		return (a,b) -> {
			int c = a.getName().compareTo(b.getName());
			if (c == 0) {
				return EARComparer.compareElementText(a, b, "name");
			}
			else {
				return c;
			}
		};
	}
	private Comparator<NodeBuilder> sortBwprocesses() {
		return (a,b) -> {
			int c = a.getName().compareTo(b.getName());
			if (c == 0) {
				return EARComparer.compareAttributeValue(a, b, "name");
			}
			else {
				return c;
			}
		};
	}
	private NodeBuilder loadAndSortUpdateConfig(Path config1, String env) {
		NodeBuilder nvPairs1 = loadConfigPart(config1, env);
		EARComparer.decryptNVPasswords(nvPairs1);
		nvPairs1.sortChildren(sortNVPairs());
		return nvPairs1;
	}
	
	public NodeBuilder loadConfigPart(Path config1, String env) {
		XmlBuilderFactory fac = new XmlBuilderFactory().setPreserveWhitespace(false);
		NodeBuilder n = fac.loadFromFile(config1);
		NodeBuilder nvPairs = n.searchFirst(true, node -> "NVPairs".equals(node.getName()) && node.getParent().getParent().hasChild(ch -> "env".equals(ch.getName()) && env.compareToIgnoreCase(ch.getTextContent().toLowerCase()) == 0));
		return nvPairs;
	}
}
