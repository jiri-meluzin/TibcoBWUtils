package com.meluzin.tibcobwutils.earcomparer.fullconfig.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
import com.meluzin.tibcobwutils.deploymentrepository.analyzer.FullConfigAnalyzer;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariable;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariables;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Repository;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.ConfigImpl;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.PasswordDecrypter;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.RepositoryImpl;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.Service.ServiceType;

public class FullConfigsModel {
	private static final String PROCESS_ARCHIVE_ELEMENT_NAME = "processArchive";
	private static final String ADAPTER_ARCHIVE_ELEMENT_NAME = "adapterArchive";
	private static final List<String> ARCHIVES_ELEMENT_NAMES = Lists.asList(ADAPTER_ARCHIVE_ELEMENT_NAME, PROCESS_ARCHIVE_ELEMENT_NAME);
	private static final String BW = "bw";
	private static final String INSTANCE_RUNTIME_VARIABLES = "INSTANCE_RUNTIME_VARIABLES";
	private static final String RUNTIME_VARIABLES = "Runtime Variables";
	private static final String GLOBAL_VARIABLES = "Global Variables";
	private static final String SDK_VARIABLES = "Adapter SDK Properties";

	private static final List<String> ALL_VARIRABLES = Lists.asList(INSTANCE_RUNTIME_VARIABLES, RUNTIME_VARIABLES, GLOBAL_VARIABLES);
	private static final List<String> INSTANCE_VARIABLES = Lists.asList(INSTANCE_RUNTIME_VARIABLES, RUNTIME_VARIABLES);
	
	private static final Map<String, ServiceType> SERVICE_TYPES = Stream.of(T.V(ADAPTER_ARCHIVE_ELEMENT_NAME, ServiceType.Adapter), T.V(PROCESS_ARCHIVE_ELEMENT_NAME, ServiceType.BW)).collect(Collectors.toMap(v -> v.getA(), v -> v.getB()));
	
	private Map<V2<Environment, Archive>, NodeBuilder> configs = new HashMap<>();
	private Map<V2<Environment, Archive>, NodeBuilder> origConfigs = new HashMap<>();
	private List<Archive> availableArchives;
	private List<Environment> availableEnvironemnts;
	private Repository repo;
	private Map<String, Set<T.V2<String, String>>> bwPropertiesNames;
	private Map<String, Set<T.V2<String, String>>> adaptersPropertiesNames;
	private Map<String, String> availableAdapters;
	
	public FullConfigsModel(Repository repo, SDKPropertiesLoader loader) {		
		this.repo = repo;
		this.bwPropertiesNames = loader.getBwPropertiesNames();
		this.adaptersPropertiesNames = loader.getAdaptersPropertiesNames();
		this.availableAdapters = loader.getAvailableAdapters();
		Path configPath = repo.getPath().getParent().resolve("_config");
		
		availableArchives = repo.findAll(i -> i.getItemType() == ItemType.Archive).stream().map(i -> new Archive(i)).collect(Collectors.toList());
		availableEnvironemnts = new FileSearcher().searchFiles(configPath, "glob:**/*", false).stream().filter(p -> p.toFile().isDirectory()).map(p -> new Environment(p)).collect(Collectors.toList());
		
		availableArchives.forEach(archive -> {
			availableEnvironemnts.stream().
				map(e -> T.V(e,archive, getConfigPath(archive, e))).
				filter(fc -> fc.getC().toFile().exists()).
				map(fc -> T.V(fc.getA(), fc.getB(), new XmlBuilderFactory().loadFromFile(fc.getC()))).
				forEach(fc -> {
					configs.put(T.V(fc.getA(), fc.getB()), fc.getC());
					origConfigs.put(T.V(fc.getA(), fc.getB()), fc.getC().copy());
				});
				;
		});
		
	}

	
	private static Optional<String> translateArchiveAdapterTypeToAdapterFolderName(Map<String, String> collect, String adapterName) {
		if (!collect.containsKey(adapterName)) {
			String adapterName2 = adapterName;
			Optional<String> adapter = collect.keySet().stream().filter(ad -> adapterName2.contains(ad)).findAny();
			if (adapter.isPresent()) {
				adapterName = adapter.get();
			}
		}
		return collect.containsKey(adapterName) ? Optional.of(collect.get(adapterName)) : Optional.empty();
	}
	private Set<V2<String, String>> getServiceSDKProperties(Environment env, Archive archive, Service service) {
		String version = getServiceProductVersion(env, archive, service);
		
		switch (service.getServiceType()) {
		case BW:
			return bwPropertiesNames.get(version);
		case Adapter:
			return adaptersPropertiesNames.get(translateArchiveAdapterTypeToAdapterFolderName(availableAdapters, service.getArchiveAdapterName()));
		default:
			break;
		}
		return new HashSet<>();
	}


	public String getServiceProductVersion(Environment env, Archive archive, Service service) {
		return getInstances(env, archive, service).stream().map(n -> getInstanceConfig(env, archive, service, n)).map(n -> n.get()).map(n -> getServiceProductVersion(n)).flatMap(s -> s).sorted().findFirst().get();
	}


	public static Stream<String> getServiceProductVersion(NodeBuilder instanceConfig) {
		return instanceConfig.search(true, "product").map(p -> p.search("version").map(c -> c.getTextContent())).flatMap(n -> n);
	}

	private Path getConfigPath(Archive archive, Environment e) {
		return e.getPath().resolve(archive.getValue() + ".xml");
	}
	
	public List<Archive> getAvailableArchives() {
		return availableArchives;
	}
	
	public List<Environment> getAvailableEnvironemnts() {
		return availableEnvironemnts;
	}

	public NodeBuilder getGlobalVariables(Environment env, Archive archive) {
		return configs.get(T.V(env, archive)).searchFirstByName("NVPairs");
	}
	public NodeBuilder getConfig(Environment env, Archive archive) {
		return configs.get(T.V(env, archive));
	}
	public NodeBuilder getConfig(T.V2<Environment, Archive> archiveEnv) {
		return configs.get(archiveEnv);
	}
	public boolean isConfigAvailable(Environment env, Archive archive) {
		return configs.get(T.V(env, archive)) != null;
	}
	public boolean isConfigAvailable(T.V2<Environment, Archive> archiveEnv) {
		return configs.get(archiveEnv) != null;
	}

	public boolean isVariableDefinedInAllConfigs(GlobalVariable variable) {		
		return configs.values().stream().filter(checkVariableInConfig(variable)).count() == 0;		
	}

	public boolean isServiceInstanceVariableDefinedInAllConfigs(GlobalVariable variable) {		
		return configs.entrySet().stream().
				map(v -> 
					getServices(v.getKey().getA(), v.getKey().getB()).
						stream().map(s -> T.V(v.getKey().getA(),v.getKey().getB(),s))
					).
				flatMap(v -> v).
				map(v -> getInstances(v.getA(), v.getB(), v.getC()).
							stream().map(instance -> getInstanceConfig(v.getA(), v.getB(), v.getC(), instance).get())).
				flatMap(n -> n).
				filter(checkServiceInstanceVariableInConfig(variable)).
				count() == 0;		
	}

	private Predicate<NodeBuilder> checkVariableInConfig(
			GlobalVariable variable) {
		return v -> 
			variable == null ||  
			(variable.isDeploymentSettable() && !variable.isInternalVariable() &&
			isVariableMissingInConfig(variable, Optional.of(v.searchFirstByName("NVPairs"))));
	}
	

	private Predicate<NodeBuilder> checkServiceInstanceVariableInConfig(
			GlobalVariable variable) {
		return v -> {
			boolean b = variable == null ||  
			(variable.isServiceSettable() && !variable.isInternalVariable() && (!v.search("NVPairs").anyMatch(n -> INSTANCE_VARIABLES.contains(n.getAttribute("name"))) ||
			isVariableMissingInConfig(variable, v.search("NVPairs").filter(n -> INSTANCE_VARIABLES.contains(n.getAttribute("name"))).findAny())));
			return b;
		};
	}

	public Set<String> getVariablesNotDefinedInDeployment(GlobalVariables rootGlobalVariables, NodeBuilder nvPairs) {
		return nvPairs.search(true, "name").map(n -> n.getTextContent()).filter(s -> !isVariableDefinedInDeployment(rootGlobalVariables, s)).collect(Collectors.toSet());		
	}
	public Set<String> getDeploymentSettableVariablesNotDefinedInDeploymentAllConfigs(GlobalVariables rootGlobalVariables) {
		return 
				configs.values().stream().
					map(n -> n.search("NVPairs").map(nn -> nn.search(true, "name")).flatMap(nn -> nn)). // search all name elements from NVPairs
					flatMap(n -> n).
					map(n -> n.getTextContent()). // map to names of variables 
					filter(s -> !isVariableDefinedInDeployment(rootGlobalVariables, s) || !getVariable(rootGlobalVariables, s).get().isDeploymentSettable()).
					collect(Collectors.toSet());		
	}
	public Set<String> getVariablesWithWrongTypes(GlobalVariables rootGlobalVariables) {
		return 
				configs.values().stream().
					map(n -> n.search("NVPairs").map(nn -> nn.search(true, "name")).flatMap(nn -> nn)). // search all name elements from NVPairs
					flatMap(n -> n).
					filter(n -> !isVariableTypeCorrect(rootGlobalVariables, n)).
					map(n -> n.getTextContent()).
					collect(Collectors.toSet());		
	}
	public void fixDeploymentVariablesTypes(GlobalVariables rootGlobalVariables) {		
		configs.values().stream().
			map(n -> n.search("NVPairs").map(nn -> nn.search(true, "name")).flatMap(nn -> nn)). // search all name elements from NVPairs
			flatMap(n -> n).
			filter(n -> !isVariableTypeCorrect(rootGlobalVariables, n)).
			forEach(n -> {
				Optional<GlobalVariable> variable = getVariable(rootGlobalVariables, n.getTextContent());
				String type = variable.get().getType();				
				n.getParent().setName("NameValuePair" + ("String".equals(type) ? "" : type));
				NodeBuilder valueNode = n.getParent().searchFirstByName("value");
				String textContent = valueNode.getTextContent();
				
				switch (type) {
					case "Password":
						if (textContent != null) {
							String encrypted = new PasswordDecrypter().encrypt(textContent);
							valueNode.setTextContent(encrypted);
						};
						break;
					case "Boolean":
						if ("true".equals(textContent)) {
							valueNode.setTextContent("true");
						}
						else {
							valueNode.setTextContent("false");
						}
						break;
					case "Integer": 
						try {
							Long l = Long.parseLong(textContent);
							valueNode.setTextContent(l);
						} catch (NumberFormatException e) {
							valueNode.setTextContent(0);
						}
						break;
				}
			});		
	}

	
	public boolean isVariableTypeCorrect(GlobalVariables rootGlobalVariables, NodeBuilder s) {
		if (isVariableDefinedInDeployment(rootGlobalVariables, s.getTextContent())) {
			Optional<GlobalVariable> variable = getVariable(rootGlobalVariables, s.getTextContent());
			
			String varType = variable.get().getType();
			String fullConfigVarType = s.getParent().getName().replace("NameValuePair", "");
			if (fullConfigVarType.length() == 0) {
				fullConfigVarType = "String";
			}
			return varType.equals(fullConfigVarType);
		}
		return true;
	}
	public Set<String> getInstanceSettableVariablesNotDefinedInDeploymentAllConfigs(GlobalVariables rootGlobalVariables) {
		return 
				configs.values().stream().
					map(n -> n.
							search("services").
							map(s -> s.getChildren().stream()).
							flatMap(x -> x).
							map(m -> 
								m.search(true, "NVPairs").
									filter(c -> INSTANCE_VARIABLES.contains(c.getAttribute("name"))).
									map(nn -> nn.search(true, "name")).
									flatMap(nn -> nn))). // search all name elements from NVPairs
					flatMap(n -> n).flatMap(n -> n).
					map(n -> n.getTextContent()). // map to names of variables 
					filter(s -> !isVariableDefinedInDeployment(rootGlobalVariables, s) || !getVariable(rootGlobalVariables, s).get().isServiceSettable()).
					collect(Collectors.toSet());		
	}
	public Set<String> getMissingProcesses(Archive archive) {
		Set<String> starters = findBWProcessStarters(archive);
		return 
				configs.
					entrySet().stream().filter(v -> v.getKey().getB().equals(archive)).
					map(v -> v.getValue()).
					map(n -> n.search(true, "bwprocess").map(nn -> nn.getAttribute("name"))). // search all name elements from NVPairs
					flatMap(n -> n).
					filter(s -> !starters.contains(s)).
					collect(Collectors.toSet());		
	}
	private Map<Archive, Set<String>> startersCache = new HashMap<>();
	public Set<String> findBWProcessStarters(Archive archive) {
		if (!startersCache.containsKey(archive)) {
			Set<String> starters = new FullConfigAnalyzer(repo).findBWProcessStarters(archive.getArchive());
			startersCache.put(archive, starters);
		}
		return startersCache.get(archive);
	}
	
	public static void main(String[] args) {
		SDKPropertiesLoader loader = new SDKPropertiesLoader(SDKPropertiesLoader.TIBCO_ADAPTERS_PATH_WINDOWS, SDKPropertiesLoader.TIBCO_BW_PATH_WINDOWS);
		Repository repo = new RepositoryImpl(Paths.get("T:/source/R171104_karel/CRM"), new ConfigImpl(Paths.get("c:/users/jirka/.tibco/Designer5.prefs.R171104_karel")));
		measure(_x -> new FullConfigsModel(repo, loader).getDuplicateVariables(), "getDuplicateVariables");
		repo.findAll(t -> t.getItemType() == ItemType.Archive).forEach(a -> measure(_x -> new FullConfigsModel(repo, loader).getDuplicateProcesses(new Archive(a)), "getDuplicateProcesses " + a));
		repo.findAll(t -> t.getItemType() == ItemType.Archive).forEach(a -> measure(_x -> new FullConfigsModel(repo, loader).getMissingProcesses(new Archive(a)), "getMissingProcesses " + a));

		measure(_x -> new FullConfigsModel(repo, loader).getDuplicateServiceVariables(), "getDuplicateServiceVariables");
		measure(_x -> new FullConfigsModel(repo, loader).getDeploymentSettableVariablesNotDefinedInDeploymentAllConfigs(repo.getRootGlobalVariables()), "getDeploymentSettableVariablesNotDefinedInDeploymentAllConfigs");
		measure(_x -> new FullConfigsModel(repo, loader).getInstanceSettableVariablesNotDefinedInDeploymentAllConfigs(repo.getRootGlobalVariables()), "getInstanceSettableVariablesNotDefinedInDeploymentAllConfigs");
	}
	private static void measure(Consumer<Void> x, String info) {
		long start = Calendar.getInstance().getTimeInMillis();
		x.accept(null);
		long end = Calendar.getInstance().getTimeInMillis();
		System.out.println("took " + (end  - start) + " " + info);
		
	}
	
	public Set<String> getDuplicateProcesses(Archive archive) {
		Map<V2<String, String>, List<V2<V2<Environment, Archive>, String>>> collect = configs.
			entrySet().stream().filter(v -> v.getKey().getB().equals(archive)).
			map(v -> v.getValue().search(true, "bwprocess").map(nn -> T.V(v.getKey(), nn.getAttribute("name")))). // search all name elements from NVPairs
			flatMap(n -> n).
			collect(Collectors.groupingBy(s -> (V2<String, String>)T.V(s.getA().getA().getValue(), s.getB())));
		return 
				collect.
					entrySet().
					stream().
					filter(v -> v.getValue().size() > 1).
					map(v -> v.getKey().getB()).
					collect(Collectors.toSet());		
	}
	public Set<String> getDuplicateVariables() {
		Map<V3<String, String, String>, List<V2<V2<Environment, Archive>, String>>> collect = configs.
			entrySet().stream().
			map(v -> v.getValue().searchFirstByName("NVPairs").search(true, "name").map(nn -> T.V(v.getKey(), nn.getTextContent()))). // search all name elements from NVPairs
			flatMap(n -> n).
			collect(Collectors.groupingBy(s -> (V3<String, String, String>)T.V(s.getA().getA().getValue(), s.getA().getB().getValue(), s.getB())));
		return 
				collect.
					entrySet().
					stream().
					filter(v -> v.getValue().size() > 1).
					map(v -> v.getKey().getC()).
					collect(Collectors.toSet());		
	}
	public Set<String> getDuplicateServiceVariables() {
		Map<V4<String, String, String, String>, List<V3<V2<Environment, Archive>, String, String>>> collect = 
				configs.
			entrySet().stream().
			map(v -> 
				v.getValue().
					search("services").
					map(s -> s.getChildren().stream()).
					flatMap(n -> n).
					map(x -> 
						x.
							search(true, "NVPairs").
							filter(n -> INSTANCE_VARIABLES.contains(n.getAttribute("name"))).
							map(c -> 
								c.
									search(true, "name").
									map(nn -> T.V(v.getKey(), nn.getParent().getParent().getXPath(), nn.getTextContent()))
							)
					)
			).
			flatMap(n -> n).				// search all name elements from NVPairs
			flatMap(n -> n).
			flatMap(n -> n).
			collect(Collectors.groupingBy(s -> (V4<String, String, String, String>)T.V(s.getA().getA().getValue(), s.getA().getB().getValue(), s.getB(), s.getC())));
		return 
				collect.
					entrySet().
					stream().
					filter(v -> v.getValue().size() > 1).
					map(v -> v.getKey().getD()).
					collect(Collectors.toSet());		
	}

	public void removeMissingProcesses(Archive archive) {
		Set<String> missing = getMissingProcesses(archive);
		configs.
			entrySet().stream().filter(v -> v.getKey().getB().equals(archive)).
			map(v -> v.getValue()).
			map(n -> n.search(true, "bwprocess").filter(nn -> missing.contains(nn.getAttribute("name")))).
			flatMap(n -> n).
			collect(Collectors.toList()).
			forEach(n -> n.getParent().removeChild(n)); // search all name elements from NVPairs
	}
	
	public void removeDuplicateProcesses(Archive archive) {
		Set<String> duplicated = getDuplicateProcesses(archive);
		Map<V2<String, String>, List<V3<V2<Environment, Archive>, String, NodeBuilder>>> toRemove = configs.
			entrySet().stream().filter(v -> v.getKey().getB().equals(archive)).
			map(v -> v.getValue().search(true, "bwprocess").map(nn -> T.V(v.getKey(), nn.getAttribute("name"), nn))).
			flatMap(n -> n).
			filter(v -> duplicated.contains(v.getB())).
			collect(Collectors.groupingBy(t -> (V2<String, String>)T.V(t.getA().getA().getValue(), t.getB())));
		toRemove.
			entrySet().stream().
			forEach(v -> {
				v.getValue().stream().skip(1).forEach(n -> n.getC().getParent().removeChild(n.getC()));
			});
	}
	
	public void removeDuplicateVariables() {
		Set<String> duplicated = getDuplicateVariables();
		Map<V3<String, String, String>, List<V3<V2<Environment, Archive>, String, NodeBuilder>>> toRemove = configs.
			entrySet().stream().
			map(v -> v.getValue().searchFirstByName("NVPairs").search(true, "name").map(nn -> T.V(v.getKey(), nn.getTextContent(), nn.getParent()))). // search all name elements from NVPairs
			//map(v -> v.getValue().search(true, "bwprocess").map(nn -> T.V(v.getKey(), nn.getAttribute("name"), nn))).
			flatMap(n -> n).
			filter(v -> duplicated.contains(v.getB())).
			collect(Collectors.groupingBy(t -> (V3<String, String, String>)T.V(t.getA().getA().getValue(), t.getA().getB().getValue(), t.getB())));
		toRemove.
			entrySet().stream().
			forEach(v -> {
				v.getValue().stream().skip(1).forEach(n -> n.getC().getParent().removeChild(n.getC()));
			});
	}

	
	public void removeDuplicateServiceVariables() {
		Set<String> duplicated = getDuplicateServiceVariables();
		Map<V4<String, String, String, String>, List<V4<V2<Environment, Archive>, String, String, NodeBuilder>>> toRemove = 
				configs.
			entrySet().stream().
			map(v -> v.getValue().
					search("services").
					map(s -> s.getChildren().stream()).
					flatMap(n -> n).map(x -> x.search(true, "NVPairs").filter(n -> INSTANCE_VARIABLES.contains(n.getAttribute("name")))
						.map(c -> c.search(true, "name").map(nn -> T.V(v.getKey(), nn.getParent().getParent().getXPath(), nn.getTextContent(), nn.getParent()))))). // search all name elements from NVPairs
			flatMap(n -> n).
			flatMap(n -> n).
			flatMap(n -> n).
			filter(v -> duplicated.contains(v.getC())).
			collect(Collectors.groupingBy(t -> (V4<String, String, String, String>)T.V(t.getA().getA().getValue(), t.getA().getB().getValue(), t.getB(), t.getC())));
		toRemove.
			entrySet().stream().
			forEach(v -> {
				v.getValue().stream().skip(1).forEach(n -> n.getD().getParent().removeChild(n.getD()));
			});
	}

	public Optional<NodeBuilder> getGlobalVariableElement(Environment env, Archive archive, GlobalVariable value) {
		String path = value.getPath();
		return getGlobalVariables(env, archive).
				search(n -> path.equals(n.searchFirstByName("name").getTextContent())).findAny();
	}

	public List<Service> getServices(Environment env, Archive archive) {
		NodeBuilder configElement = getConfig(env, archive);
		if (configElement == null) return Lists.asList();
		return configElement.search("services").
				map(n -> n.getChildren().stream()).flatMap(n -> n).map(n -> new Service(n.getAttribute("name"), ServiceType.fromElementName(n.getName()))).collect(Collectors.toList());		
	}
	
	public Set<V2<String, String>> getProperties(Service service, Archive archive) {
		V2<Service, NodeBuilder> srv = loadServicesStream(archive).filter(v -> v.getA().equals(service)).findFirst().get();
		switch (service.getServiceType()) {
			case Adapter:
				Optional<String> v = srv.getB().search("softwareTypeProperty").map(n -> n.getTextContent().toLowerCase()).findFirst();
				Optional<String> translateArchiveAdapterTypeToAdapterFolderName = translateArchiveAdapterTypeToAdapterFolderName(availableAdapters, v.get());
				if (!translateArchiveAdapterTypeToAdapterFolderName.isPresent()) return new HashSet<>();			
				Set<V2<String, String>> set = adaptersPropertiesNames.get(translateArchiveAdapterTypeToAdapterFolderName.get());
				if (set == null) set = new HashSet<>();
				return set;
			case BW:
				return bwPropertiesNames.values().stream().findFirst().get();
	
			default:
				break;
		}
		 return new HashSet<>();
//		new FileSearcher().searchFiles(Paths.get("t:/source/R171126/"), "glob:**/*.archive", true).stream().
//		map(p -> new XmlBuilderFactory().loadFromFile(p).search(true, "softwareTypeProperty").map(n -> n.getTextContent()).distinct()).
//		flatMap(s -> s).
//		distinct().
//		sorted().
//		map(s -> s.toLowerCase()).
//		map(s -> T.V(s, translateArchiveAdapterTypeToAdapterFolderName(collect, s))).
//		map(s -> new FileSearcher().searchFiles(Paths.get(tibcoAdaptersPath,s.getB()), "glob:**/*", false).stream().sorted((a,b) -> b.toString().compareTo(a.toString())).findFirst().get()).
//		map(adPath -> new FileSearcher().searchFiles(adPath.resolve("lib"), "glob:**/*.jar", true).
//				stream().
//				map(p -> new LoadZipFile().load(p).stream().filter(v -> v.getA().matches(".*com.tibco.deployment.*xml")).map(v -> p + "" + v.getA())
//						).
//				flatMap(s -> s)
//				//filter(s -> s.size() > 0)
//				.collect(Collectors.toList())).
//		forEach(s -> System.out.println(s));
//		
	}
	

	public List<Service> getServices(Archive archive) {
		
		return loadServicesStream(archive).
				map(v -> v.getA()).
				collect(Collectors.toList());
	}

	private Stream<V2<Service, NodeBuilder>> loadServicesStream(Archive archive) {
		return archive.getArchive().loadAsXml().
				search(true, n -> ARCHIVES_ELEMENT_NAMES.contains(n.getName())).
				map(n -> T.V(SERVICE_TYPES.get(n.getName()), n.getAttribute("name"), n)).
				map(v -> T.V(new Service(v.getB(), v.getA()), v.getC()));
	}

	public List<String> getInstances(Environment env, Archive archive, Service service) {
		Optional<NodeBuilder> configElement = getServiceConfig(env, archive, service);
		return configElement.get().search("bindings").map(n -> n.search("binding")).flatMap(n -> n).map(n -> n.getAttribute("name")).collect(Collectors.toList());		
	}
	public Optional<NodeBuilder> getInstanceConfig(Environment env, Archive archive, Service service, String instanceName) {
		Optional<NodeBuilder> configElement = getServiceConfig(env, archive, service);
		return configElement.get().search("bindings").map(n -> n.search("binding")).flatMap(n -> n).filter(n -> instanceName.equals(n.getAttribute("name"))).findAny();		
	}
	public Optional<NodeBuilder> getServiceInstanceVariablesElement(Environment env, Archive archive, Service service, String instanceName) {
		Optional<NodeBuilder> instanceConfig = getInstanceConfig(env, archive, service, instanceName);
		return !instanceConfig.isPresent() ? Optional.empty() : instanceConfig.get().search("NVPairs").filter(n -> INSTANCE_VARIABLES.contains(n.getAttribute("name"))).findAny();
	}
	public Optional<NodeBuilder> getServiceInstanceVariableElement(Environment env, Archive archive, Service service, GlobalVariable value, String instanceName) {
		String path = value.getPath();
		Optional<NodeBuilder> variables = getServiceInstanceVariablesElement(env, archive, service, instanceName);
		return !variables.isPresent() ? Optional.empty() : variables.get().search(n -> path.equals(n.searchFirstByName("name").getTextContent())).findAny();
	}
	public NodeBuilder createServiceInstanceVariableElement(Environment env, Archive archive, Service service, GlobalVariable value, String instanceName) {
		String path = value.getPath();
		Optional<NodeBuilder> variables = getServiceInstanceVariablesElement(env, archive, service, instanceName);
		if (!variables.isPresent()) {
			Optional<NodeBuilder> config = getInstanceConfig(env, archive, service, instanceName);
			variables = Optional.of(config.get().addChild("NVPairs").addAttribute("name", INSTANCE_RUNTIME_VARIABLES));
		}
		Optional<NodeBuilder> variableElement = variables.get().search(n -> path.equals(n.searchFirstByName("name").getTextContent())).findAny();
		if (!variableElement.isPresent()) {
			variableElement = Optional.of(createNVPair(value, variables.get()));
		}
		return variableElement.get();
	}
	public void removeServiceInstanceVariableElement(Environment env, Archive archive, Service service, String variablePath, String instanceName) {
		Optional<NodeBuilder> variables = getServiceInstanceVariablesElement(env, archive, service, instanceName);
		if (!variables.isPresent()) {
			return;
		}
		Optional<NodeBuilder> variableElement = variables.get().search(n -> variablePath.equals(n.searchFirstByName("name").getTextContent())).findAny();
		if (!variableElement.isPresent()) {
			return;
		}
		variableElement.get().getParent().removeChild(variableElement.get());
	}
	



	public Optional<NodeBuilder> getServiceConfig(Environment env, Archive archive, Service service) {
		NodeBuilder configElement = getConfig(env, archive);
		return configElement.search("services").map(n -> n.search(service.getServiceType().getElementName())).flatMap(n -> n).filter(n -> (service.getValue()+service.getServiceType().getArchiveExtension()).equals(n.getAttribute("name"))).findAny();		
	}
	public Optional<NodeBuilder> getServiceVariablesElement(Environment env, Archive archive, Service service) {
		Optional<NodeBuilder> serviceConfig = getServiceConfig(env, archive, service);
		List<String> names = Lists.asList(RUNTIME_VARIABLES);
		return !serviceConfig.isPresent() ? Optional.empty() : serviceConfig.get().search("NVPairs").filter(n -> names.contains(n.getAttribute("name"))).findAny();
	}
	public Optional<NodeBuilder> getServiceSDKVariablesElement(Environment env, Archive archive, Service service) {
		Optional<NodeBuilder> serviceConfig = getServiceConfig(env, archive, service);
		List<String> names = Lists.asList(SDK_VARIABLES);
		return !serviceConfig.isPresent() ? Optional.empty() : serviceConfig.get().search("NVPairs").filter(n -> names.contains(n.getAttribute("name"))).findAny();
	}
	public NodeBuilder createServiceSDKVariablesElement(Environment env, Archive archive, Service service) {
		Optional<NodeBuilder> serviceSDKVariablesElement = getServiceSDKVariablesElement(env, archive, service);
		if (!serviceSDKVariablesElement.isPresent()) {
			Optional<NodeBuilder> serviceConfig = getServiceConfig(env, archive, service);
			List<String> names = Lists.asList(SDK_VARIABLES);
			int childIndex = serviceConfig.get().searchFirstByName("failureCount").getChildIndex();
			serviceSDKVariablesElement = Optional.of(serviceConfig.get().addChild("NVPairs", childIndex).addAttribute("name", SDK_VARIABLES));
			
		}
		return serviceSDKVariablesElement.get();
	}
	public Optional<NodeBuilder> getServiceVariableElement(Environment env, Archive archive, Service service, GlobalVariable value) {
		String path = value.getPath();
		Optional<NodeBuilder> variables = getServiceVariablesElement(env, archive, service);
		return !variables.isPresent() ? Optional.empty() : variables.get().search(n -> path.equals(n.searchFirstByName("name").getTextContent())).findAny();
	}
	public NodeBuilder createServiceVariableElement(Environment env, Archive archive, Service service, GlobalVariable value) {
		String path = value.getPath();
		Optional<NodeBuilder> variables = getServiceVariablesElement(env, archive, service);
		if (!variables.isPresent()) {
			Optional<NodeBuilder> config = getServiceConfig(env, archive, service);
			int index = config.get().searchFirstByName("bindings").getChildIndex();
			variables = Optional.of(config.get().addChild("NVPairs", index + 1).addAttribute("name", RUNTIME_VARIABLES));
		}
		Optional<NodeBuilder> variableElement = variables.get().search(n -> path.equals(n.searchFirstByName("name").getTextContent())).findAny();
		if (!variableElement.isPresent()) {
			variableElement = Optional.of(createNVPair(value, variables.get()));
		}
		return variableElement.get();
	}
	public void removeServiceVariableElement(Environment env, Archive archive, Service service, String variablePath) {
		Optional<NodeBuilder> variables = getServiceVariablesElement(env, archive, service);
		if (!variables.isPresent()) {
			return;
		}
		Optional<NodeBuilder> variableElement = variables.get().search(n -> variablePath.equals(n.searchFirstByName("name").getTextContent())).findAny();
		if (!variableElement.isPresent()) {
			return;
		}
		variableElement.get().getParent().removeChild(variableElement.get());
	}

	public void removeVariableFromConfig(Environment env, Archive archive, String variablePath) {
		Optional<NodeBuilder> nb = getGlobalVariables(env, archive).search(n -> variablePath.equals(n.searchFirstByName("name").getTextContent())).findAny();
		if (nb.isPresent()) {
			NodeBuilder p = nb.get(); // NameValuePair
			p.getParent().removeChild(p);
		}
	}
	public void removeServiceVariableFromConfig(Environment env, Archive archive, String variablePath) {
		getConfig(env, archive).
			search("services").
			map(s -> s.getChildren().stream()).
			flatMap(n -> n).map(x -> x.
			search(true, "NVPairs")).
			flatMap(n -> n).
			map(c -> c.search(n -> variablePath.equals(n.searchFirstByName("name").getTextContent()))).
			flatMap(n -> n).
			collect(Collectors.toList()).
			forEach(nb -> {
				nb.getParent().removeChild(nb);
			});
	}
	public void removeVariablesFromConfig(Set<String> variables) {
		configs.keySet().forEach(v -> variables.forEach(path -> removeVariableFromConfig(v.getA(), v.getB(), path)));
	}
	public void removeServiceVariablesFromConfig(Set<String> variables) {
		configs.keySet().forEach(v -> variables.forEach(path -> removeServiceVariableFromConfig(v.getA(), v.getB(), path)));
	}
	public NodeBuilder createGlobalVariableElement(Environment env, Archive archive, GlobalVariable value) {
		NodeBuilder globalVariables = getGlobalVariables(env, archive);
		NodeBuilder configElement = createNVPair(value, globalVariables);
		return configElement;
	}

	private NodeBuilder createNVPair(GlobalVariable value, NodeBuilder globalVariables) {
		String path = value.getPath();
		String suffix = "";
		switch (value.getType()) {
			case "Password":				
			case "Integer":				
			case "Boolean":
				suffix = value.getType();
				break;
		}
		NodeBuilder configElement = globalVariables.addChild("NameValuePair" + suffix);
		configElement.addChild("name").setTextContent(path);
		configElement.addChild("value");
		return configElement;
	}
	
	public boolean isVariableDefinedInDeployment(GlobalVariables rootGlobalVariables, String variablePath) {
		return getVariable(rootGlobalVariables, variablePath).isPresent();		
	}

	private Optional<GlobalVariable> getVariable(GlobalVariables rootGlobalVariables, String variablePath) {
		Optional<GlobalVariable> globVar = rootGlobalVariables.resolve(Paths.get(variablePath));
		return globVar;
	}

	private boolean isVariableMissingInConfig(GlobalVariable variable, Optional<NodeBuilder> nvpairs) {
		String variablePath = getVarPathString(variable);
		Optional<NodeBuilder> variableNode = !nvpairs.isPresent() ? nvpairs : nvpairs.get().
			search(true, n -> "name".equals(n.getName()) && variablePath.equals(n.getTextContent())).findAny();
		return !variableNode.isPresent();
	}

	private String getVarPathString(GlobalVariable variable) {
		return variable.getPath();
	}
	
	public boolean isChanged() {
		for (T.V2<Environment, Archive> key : configs.keySet()) {
			NodeBuilder orig = origConfigs.get(key);
			NodeBuilder newConfig = configs.get(key);
			if (!orig.equalsTo(newConfig)) return true;
		}
		return false;
	}
	public void save() {
		for (T.V2<Environment, Archive> key : configs.keySet()) {
			NodeBuilder orig = origConfigs.get(key);
			NodeBuilder newConfig = configs.get(key);
			if (!orig.equalsTo(newConfig)) {
				new XmlBuilderFactory().renderNode(newConfig, getConfigPath(key.getB(), key.getA()));
			}
		}
		
	}
	
	
}
