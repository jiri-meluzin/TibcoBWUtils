package com.meluzin.tibcobwutils.earcomparer.fullconfig.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V2;
import com.meluzin.tibcobwutils.earcomparer.load.LoadZipFile;

public class SDKPropertiesLoader {
	public static final Path TIBCO_BW_PATH_WINDOWS = Paths.get("t:/tib/app/tibco/bw/");
	public static final Path TIBCO_ADAPTERS_PATH_WINDOWS = Paths.get("t:/tib/app/tibco/adapter/");
	private Path tibcoAdaptersPath = TIBCO_ADAPTERS_PATH_WINDOWS;
	private Path tibcoBWPath = TIBCO_BW_PATH_WINDOWS;
	private Map<String, Set<T.V2<String, String>>> bwPropertiesNames;
	private Map<String, Set<T.V2<String, String>>> adaptersPropertiesNames;
	private Map<String, String> availableAdapters;
	
	public Path getTibcoBWPath() {
		return tibcoBWPath;
	}
	public Path getTibcoAdaptersPath() {
		return tibcoAdaptersPath;
	}

	public SDKPropertiesLoader(Path tibcoHome) {
		this(tibcoHome.resolve("adapter"), tibcoHome.resolve("bw"));
	}
	public SDKPropertiesLoader(Path tibcoAdaptersPath, Path tibcoBWPath) {
		this.tibcoAdaptersPath = tibcoAdaptersPath;
		this.tibcoBWPath = tibcoBWPath;
	}
	
	public Map<String, Set<T.V2<String, String>>> getAdaptersPropertiesNames() {
		if (adaptersPropertiesNames == null) load();
		return adaptersPropertiesNames;
	}
	public Map<String, Set<T.V2<String, String>>> getBwPropertiesNames() {
		if (bwPropertiesNames == null) load();
		return bwPropertiesNames;
	}
	public Map<String, String> getAvailableAdapters() {
		if (availableAdapters == null) load();
		return availableAdapters;
	}
	
	private void load() {

		availableAdapters = new FileSearcher().
			searchFiles(tibcoAdaptersPath, "glob:**/*", false).
			stream().
			map(p -> p.getFileName().toString()).
			map(n -> T.V(n, n.replaceFirst("ad", ""))).
			collect(Collectors.toMap(v -> v.getB(), v -> v.getA()));
		//System.out.println(collect);
		
		Map<String, NodeBuilder> adapterProperties = availableAdapters.values().stream().	
			map(s -> T.V(s, new FileSearcher().searchFiles(tibcoAdaptersPath.resolve(s), "glob:**/*", false).stream().sorted((a,b) -> b.toString().compareTo(a.toString())).findFirst().get())).
			map(adPath -> new FileSearcher().searchFiles(adPath.getB().resolve("lib"), "glob:**/*.jar", true).
				stream().
				map(p -> new LoadZipFile().load(p).stream().filter(v -> v.getA().matches(".*com.tibco.deployment.*xml"))
						.filter(v -> filterXmlPropertyFile(adPath, v) )
						.map(v -> new XmlBuilderFactory().parseDocument(new String(v.getB())))).
				flatMap(s ->s).map(s->  T.V(adPath.getA(),s)))
				//filter(s -> s.size() > 0)
				.flatMap(s -> s)
				.collect(Collectors.toMap(v -> v.getA(), v -> v.getB()));
		adaptersPropertiesNames = getPropertiesNamesAndValues(adapterProperties);
		
		List<Path> BWPaths = new FileSearcher().searchFiles(tibcoBWPath, "glob:**/*", false).stream().sorted((a,b) -> b.toString().compareTo(a.toString())).collect(Collectors.toList());
		Map<String, NodeBuilder> bwProperties = BWPaths.
				stream().
				map(BWPath -> T.V(BWPath.getFileName().toString(), new XmlBuilderFactory().loadFromFile(BWPath.resolve("lib/com/tibco/deployment/bwengine.xml")))).
				collect(Collectors.toMap(v -> v.getA(), v -> v.getB()));

		bwPropertiesNames = getPropertiesNamesAndValues(bwProperties);
		//adaptersPropertiesNames.entrySet().forEach(v -> System.out.println(v));
		//byPropertiesNames.entrySet().forEach(v -> System.out.println(v));
	}

	private Map<String, Set<V2<String, String>>> getPropertiesNamesAndValues(
			Map<String, NodeBuilder> adapterProperties) {
		return adapterProperties.entrySet().stream().map(p -> T.V(p.getKey(), p.getValue().search(true, "option").map(n -> T.V(n.getTextContent(), n.getParent().searchFirstByName("default").getTextContent())).collect(Collectors.toSet()))).collect(Collectors.toMap(v -> v.getA(), v -> v.getB()));
	}

	private boolean filterXmlPropertyFile(V2<String, Path> adPath, V2<String,byte[]> v) {
		
		String[] split = v.getA().split("[\\,/]");
		String a = split[split.length - 1];
		switch (adPath.getA()) {
			case "adfiles":
				switch (a) {
					case "FileAdapter.xml": return true;
				}
				return false;
			case "adr3":
				switch (a) {
				case "adr3.xml": return true;
			}
			return false;
		}
		return true;
	}
}
