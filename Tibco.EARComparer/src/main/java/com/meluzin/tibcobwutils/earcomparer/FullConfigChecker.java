package com.meluzin.tibcobwutils.earcomparer;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.Lists;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V2;
import com.meluzin.tibcobwutils.deploymentrepository.analyzer.FullConfigAnalyzer;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariable;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariables;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Repository;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.ConfigImpl;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.RepositoryImpl;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.SDKPropertiesLoader;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class FullConfigChecker {
	private static final String INSTANCE_RUNTIME_VARIABLES = "INSTANCE_RUNTIME_VARIABLES";
	private static final String RUNTIME_VARIABLES = "Runtime Variables";
	
	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("Full config checker", true, "-")
				.description("Checks full config global variables against tibco bw code.");
		argParser.addArgument("-prefs").type(String.class).required(true).help("Path to .TIBCO prefs file. Ex: C:\\Users\\Jirka\\.TIBCO\\Designer5.prefs.R170903");
		argParser.addArgument("-source").type(String.class).required(true).help("Path to directory with code. Ex: T:\\source\\R170903 or T:\\source\\R170903\\CRM");
		argParser.addArgument("-export").type(String.class).required(true).help("Path to directory with exported full configs. Ex: T:\\temp\\compare\\_deployed");
		argParser.addArgument("-config").type(String.class).required(true).help("Path to directory with full configs. Ex: T:\\source\\R170903\\_config");
		argParser.addArgument("-tibcohome").type(String.class).required(false).help("Path to tibco home. Ex: T:/tib/app/tibco");
		
		Namespace res = argParser.parseArgsOrFail(args);
		try {
			Path prefFilePath = Paths.get((String)res.get("prefs"));
			Path sourcePath = Paths.get((String)res.get("source"));
			Path configPath = Paths.get((String)res.get("config"));
			Path exportPath = Paths.get((String)res.get("export"));
			Path branchPath = sourcePath;
			Path tibcoHome = Paths.get(res.getString("tibcohome") == null ? "t:/tib/app/tibco" : res.getString("tibcohome"));
			SDKPropertiesLoader sdkLoader = new SDKPropertiesLoader(tibcoHome);
			Path deploymentListPath = configPath.resolve("deploymentList.xml");
			NodeBuilder deploymentList = new XmlBuilderFactory().loadFromFile(deploymentListPath);
			new FileSearcher().searchFiles(branchPath, "glob:**/vcrepo.dat", true).stream().
				parallel().
				map(p -> checkVariables(sdkLoader, p.getParent(), prefFilePath, branchPath, configPath, exportPath, deploymentList)).
				flatMap(s -> s).
				forEach(s -> System.err.println(s));
				
				; 
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	private static Stream<String> checkVariables(SDKPropertiesLoader loader, Path projectPath, Path prefFilePath, Path branchPath, Path configPath, Path exportPath, NodeBuilder deploymentList) {
		List<String> messages = new ArrayList<>();
		Repository r = new RepositoryImpl(projectPath, new ConfigImpl(prefFilePath));
		NormalizeConfig normalizeConfig = new NormalizeConfig(loader);

		r.findAll(i -> i.getItemType() == ItemType.Archive).forEach(archive -> {
			NodeBuilder archiveXML = archive.loadAsXml();
			NodeBuilder archiveNameElement = archiveXML.searchFirst(true, n -> "name".equals(n.getName()));
			String archiveName = archiveNameElement == null ?  archive.getName().replace(ItemType.Archive.getExtension(), "") : archiveNameElement.getTextContent();
			
			deploymentList.search(n -> projectPath.getFileName().toString().equals(n.getAttribute("project")) && archiveName.equals(n.getAttribute("name"))).forEach(deployment -> {
				deployment.search(true, en -> "environment".equals(en.getName())).forEach(env -> {
					Path fullConfigsPath = configPath.resolve(env.getAttribute("domain"));
					Path fullConfigPath = fullConfigsPath.resolve(archiveName + ".xml");
					Path fullConfigsExportPath = exportPath.resolve(env.getAttribute("domain"));
					Path fullConfigExportPath = fullConfigsExportPath.resolve(archiveName + ".xml");
					
					if (!fullConfigPath.toFile().exists()) {
						messages.add(fullConfigPath + " does not exists! " + projectPath);				
					} else {
						NodeBuilder fullConfig = normalizeConfig.removeRedundants(new XmlBuilderFactory().loadFromFile(fullConfigPath));
						Optional<String> validationResult = validateFullConfig(fullConfig);
						if (validationResult.isPresent()) {
							messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ "fullconfig XML validation error" + "\t" + validationResult.get());									
						}
						List<String> validateFullConfigSemantic = validateFullConfigSemanticDuplicatedServiceNVPairs(fullConfig);
						validateFullConfigSemantic.forEach(v -> {
							messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ "fullconfig contains duplicate service NVPairs" + "\t" + v);									
							
						});
						List<String> validateFullConfigSemanticInstance = validateFullConfigSemanticDuplicatedServiceInstanceNVPairs(fullConfig);
						validateFullConfigSemanticInstance.forEach(v -> {
							messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ "fullconfig contains duplicate service NVPairs" + "\t" + v);									
							
						});
						Optional<NodeBuilder> fullConfigExport = fullConfigExportPath.toFile().exists() ? Optional.of(normalizeConfig.removeRedundants(new XmlBuilderFactory().loadFromFile(fullConfigExportPath))) : Optional.empty();
//						System.out.println(archive.getDeploymentReference());
						GlobalVariables rootGlobalVariables = r.getRootGlobalVariables();
						Set<String> starters = new FullConfigAnalyzer(r).findBWProcessStarters(archive);
						synchronized (FullConfigChecker.class) {
							//fullConfig.search(true, n -> "NVPairs".equals(n.getName())).forEach(n -> System.out.println(n.getAttribute("name") + "\t" + n.getXPath()));
							//fullConfigExport.search(true, n -> "NVPairs".equals(n.getName())).forEach(n -> System.out.println(n.getAttribute("name") + "\t" + n.getXPath()));

							fullConfig.searchFirstByName("NVPairs").search(true, "name").
								map(n -> n.getTextContent()).
								collect(Collectors.groupingBy(Function.identity())).values().stream().
								filter(l -> l.size() > 1).
								forEach(x -> {
									messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ "duplicate global variable in fullconfig" + "\t" + x.get(0));									
								});
							
							checkVariables(fullConfig, rootGlobalVariables, r, env.getAttribute("domain"), archiveName, messages);
							fullConfig.searchFirstByName("NVPairs").search(true, n -> "name".equals(n.getName())).forEach(checkAvailableDeploymentSettableVariables(r, archiveName, env, rootGlobalVariables, messages));	
							
							//System.out.println(archive.getDeploymentReference());
							checkServiceVariables(fullConfig, fullConfigExport, rootGlobalVariables, r, env.getAttribute("domain"), archiveName, messages);
							NodeBuilder bw = fullConfig.searchFirst(true, n -> "bw".equals(n.getName()));
							if (bw != null) {
								bw.search(true, n -> "NVPairs".equals(n.getName()) &&  RUNTIME_VARIABLES.equals(n.getAttribute("name"))).forEach(nvPairs -> {
									nvPairs.search(true, n -> "name".equals(n.getName())).forEach(checkAvailableServiceSettableVariables(r, archiveName, env, rootGlobalVariables, messages));
								});
							}
						/*NodeBuilder processesProperty = archiveXML.searchFirstByName(true, "processProperty");
						if (processesProperty != null) {
							String[] processes = processesProperty.getTextContent().split(",");
							List<String> processesList = Arrays.asList(processes).stream().map(p -> p.charAt(0) == '/' ? p.substring(1) : p).collect(Collectors.toList());*/
							starters.stream().
								//map(p -> p.charAt(0) == '/' ? p.substring(1) : p).
								filter(p -> !fullConfig.hasChild(true, n -> "bwprocess".equals(n.getName()) && p.equals(n.getAttribute("name")))).
								map(p -> r.findItem(p).get()).
								filter(i -> i.getItemType() != ItemType.Process || i.loadAsXml().searchFirstByName("starter") != null).
								forEach(p -> {
									messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ "missing bwprocess config in fullconfig" + "\t" + p.getDeploymentReference().replace("\\", "/").substring(1));
								});

							fullConfig.search(true, "bwprocess").
								map(n -> n.getAttribute("name")).
								map(p -> p.charAt(0) == '/' ? p.substring(1) : p).
								collect(Collectors.groupingBy(Function.identity())).values().stream().
								filter(l -> l.size() > 1).
								forEach(x -> {
									messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ "duplicate bwprocess config in fullconfig" + "\t" + x.get(0));									
								});

							fullConfig.search(true, "bwprocess").
								map(n -> n.getAttribute("name")).
								map(p -> p.charAt(0) == '/' ? p.substring(1) : p).
								filter(p -> !starters.contains(p)).
								forEach(x -> {
									//System.out.println(processesList);
									messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ "bwprocess config should be removed from fullconfig" + "\t" + x);									
								});
							
							starters.stream().
								//map(p -> p.charAt(0) == '/' ? p.substring(1) : p).
								map(p -> T.V(p, fullConfig.search(true, n -> "bwprocess".equals(n.getName()) && p.equals(n.getAttribute("name"))).findAny())).
								filter(v -> v.getB().isPresent()).
								forEach(p -> {
									checkMaxJob(r, archiveName, env, p, "maxJob", messages);
									checkMaxJob(r, archiveName, env, p, "flowLimit", messages);
								});
						}
						//}
					}		
				});

			});
			
		});
		return messages.stream().sorted();
	}

	private static Optional<String> validateFullConfig(NodeBuilder fullConfig) {

		SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		URL resource = FullConfigChecker.class.getResource("/ApplicationManagement.xsd");
		try {
			Schema schema = schemaFactory.newSchema(resource);
			Validator  validator = schema.newValidator();
			validator.validate(new StreamSource(new StringReader(fullConfig.toString())));			
			return Optional.empty();
		} catch (SAXException | IOException e) {
			return Optional.of(e.toString());
		}
	}

	private static List<String> validateFullConfigSemanticDuplicatedServiceNVPairs(NodeBuilder fullConfig) {
		return fullConfig.search("services").map(n -> n.getChildren().stream()).flatMap(s -> s).filter(n -> n.search("NVPairs").filter(nn ->  RUNTIME_VARIABLES.contains(nn.getAttribute("name"))).count() > 1).map(n -> n.getXPath()).collect(Collectors.toList());
	}
	private static List<String> validateFullConfigSemanticDuplicatedServiceInstanceNVPairs(NodeBuilder fullConfig) {
		return fullConfig.search(true, "binding").filter(n -> n.search("NVPairs").filter(nn ->  RUNTIME_VARIABLES.contains(nn.getAttribute("name"))).count() > 1).map(n -> n.getXPath()).collect(Collectors.toList());
	}

	private static void checkMaxJob(Repository r, String archiveName, NodeBuilder env,
			V2<String, Optional<NodeBuilder>> p, String property, List<String> messages) {
		NodeBuilder maxJob = p.getB().get().searchFirstByName(property);
		if (maxJob == null || maxJob.getTextContent() == null) {
			messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ "missing or missing value of "+property+" in bwprocess config in fullconfig" + "\t" + p.getA());										
		} else {
			Optional<Integer> tryParse = tryParse(maxJob.getTextContent());
			if (!tryParse.isPresent()) {		
				messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ property + " in bwprocess config in fullconfig is not a number (" + maxJob.getTextContent() + ")" + "\t" + p.getA());										
			} else if (tryParse.get() < 0) {
				messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t"+ property + " in bwprocess config in fullconfig is not a non-negative number (" + maxJob.getTextContent() + ")" + "\t" + p.getA());				
			}
		}
	}
	
	private static Optional<Integer> tryParse(String num) {
		  try {
			  return Optional.of(Integer.parseInt(num));
		  } catch (NumberFormatException nfe) {
			  return Optional.empty();
		  }
	}

	private static Consumer<? super NodeBuilder> checkAvailableDeploymentSettableVariables(Repository r, String archiveName,
			NodeBuilder env, GlobalVariables rootGlobalVariables, List<String> messages) {
		return n -> {
			Optional<GlobalVariable> x = rootGlobalVariables.resolve(n.getTextContent());
			if (!x.isPresent() || !x.get().isDeploymentSettable()) {
				messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t" + "global variable should be removed from fullconfig" + "\t" + n.getTextContent() + "\t" + n.getXPath());								
			}
		};
	}
	private static Consumer<? super NodeBuilder> checkAvailableServiceSettableVariables(Repository r, String archiveName,
			NodeBuilder env, GlobalVariables rootGlobalVariables, List<String> messages) {
		return n -> {
			Optional<GlobalVariable> x = rootGlobalVariables.resolve(n.getTextContent());
			if (!x.isPresent() || !x.get().isServiceSettable()) {
				messages.add(r.getDeployment().getName() + "\t" + archiveName + "\t" + env.getAttribute("domain") + "\t" + "service variable should be removed from fullconfig" + "\t" + n.getTextContent() + "\t" + n.getXPath());								
			}
		};
	}

	private static void checkVariables(NodeBuilder fullConfig, GlobalVariables rootGlobalVariables, Repository repo, String env, String archiveName, List<String> messages) {
		rootGlobalVariables.getAllVariables().entrySet().stream().filter(v -> v.getValue().size() > 0)
				.filter(v -> v.getValue().get(0).isDeploymentSettable()).forEach(e -> {
					String key = parseKey(rootGlobalVariables, e.getKey());
					/*boolean hasVariable = */
					Optional<NodeBuilder> xmlVariable = fullConfig
							.searchFirst(true, c -> "NVPairs".equals(c.getName()) && "Global Variables".equals(c.getAttribute("name")))
							.search(true, "name").filter(name -> key.equals(name.getTextContent())).findFirst();
					if (!xmlVariable.isPresent() && !Lists.asList("Deployment", "Domain").contains(key))
						messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "missing global variable in fullconfig" + "\t" + key);
					if (xmlVariable.isPresent()) {
						GlobalVariable childVariable = getChildVariable(rootGlobalVariables, e.getKey());
						String name = xmlVariable.get().getParent().getName();
						String xmlVariableType = name.replace("NameValuePair", "");
						if ("".equals(xmlVariableType)) {
							xmlVariableType = "String";
						}
						if (!xmlVariableType.equals(childVariable.getType())) {
							messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "variable has wrong type (fullconfig contains " +xmlVariableType + " but " + childVariable.getType() + " is defined in deployment)"+ "\t" + key);
						}

					}

				});
		rootGlobalVariables.getChildVariables().forEach(c -> checkVariables(fullConfig, c, repo, env, archiveName, messages));
	}
	private static void checkServiceVariables(NodeBuilder fullConfig, Optional<NodeBuilder> fullConfigExport, GlobalVariables rootGlobalVariables, Repository repo, String env, String archiveName, List<String> messages) {
		rootGlobalVariables.getAllVariables().entrySet().stream().filter(v -> v.getValue().size() > 0)
				.filter(v -> v.getValue().get(0).isServiceSettable()).forEach(e -> {
					String key = parseKey(rootGlobalVariables, e.getKey());
					fullConfig.search(true, n -> (n.hasParent() && "services".equals(n.getParent().getName())) || "binding".equals(n.getName())).forEach(parent -> {
						NodeBuilder nvPairs = parent.searchFirst(c -> "NVPairs".equals(c.getName()) && Lists.asList(RUNTIME_VARIABLES,INSTANCE_RUNTIME_VARIABLES).contains(c.getAttribute("name")));
						if (nvPairs != null) {
							boolean hasVariable = nvPairs != null && nvPairs.hasChild(c -> c.hasChild(name -> "name".equals(name.getName()) && key.equals(name.getTextContent())));
							if (!hasVariable && !Lists.asList("Deployment", "Domain").contains(key)) {
								if (fullConfigExport.isPresent()) {
									if ("services".equals(parent.getParent().getName())) {
										Optional<NodeBuilder> serviceVariableFullConEx = fullConfigExport.get().search(true, "bw").map(n -> n.search("NVPairs")).flatMap(n -> n).map(n -> n.search(true, c -> "name".equals(c.getName()) && key.equals(c.getTextContent()))).flatMap(c -> c).findAny();
										if (serviceVariableFullConEx.isPresent()) {
											messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "missing service variable in fullconfig which is defined in exported fullconfig" + "\t" + key + "\t" + serviceVariableFullConEx.get().getXPath() + " exported value is: " + serviceVariableFullConEx.get().getParent().searchFirstByName("value").getTextContent());
										}
									} else if ("binding".equals(parent.getName())) {
										String bindingMachineName = nvPairs.getParent().getAttribute("name");
										Optional<NodeBuilder> serviceInstanceVariableFullConEx = fullConfigExport.get().search(true, "binding").filter(binding -> bindingMachineName.equals(binding.getAttribute("name"))).map(n -> n.search("NVPairs")).flatMap(n -> n).map(n -> n.search(true, c -> "name".equals(c.getName()) && key.equals(c.getTextContent()))).flatMap(c -> c).findAny();
										if (serviceInstanceVariableFullConEx.isPresent()) {
											messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "missing service instance variable in fullconfig which is defined in exported fullconfig" + "\t" + key + "\t" + bindingMachineName + "\t" + serviceInstanceVariableFullConEx.get().getXPath() + " exported value is: " + serviceInstanceVariableFullConEx.get().getParent().searchFirstByName("value").getTextContent());
										}
									} 
								} else {
									if ("services".equals(parent.getParent().getName())) {
										messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "missing service variable in fullconfig" + "\t" + key + "\t" + nvPairs.getXPath());
									} else if ("binding".equals(parent.getName())) {
										String bindingMachineName = nvPairs.getParent().getAttribute("name");
										messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "missing service instance variable in fullconfig" + "\t" + key + "\t" + bindingMachineName + "\t" + nvPairs.getXPath());								
									}									
								}
								//messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "missing service variable" + "\t" + key + "\t" + parent.getXPath());
							}
						} else {
							if ("services".equals(parent.getParent().getName())) {
								messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "missing service variable in fullconfig, whole NVPairs with name = ("+RUNTIME_VARIABLES+") element missing" + "\t" + key + "\t" + parent.getXPath());
							} else if ("binding".equals(parent.getName())) {
								String bindingMachineName = parent.getAttribute("name");
								messages.add(repo.getDeployment().getName() + "\t" + archiveName + "\t" + env + "\t" + "missing service instance variable in fullconfig, whole NVPairs with name = ("+ RUNTIME_VARIABLES + " or " + INSTANCE_RUNTIME_VARIABLES +") element missing" + "\t" + key + "\t" + bindingMachineName + "\t" + parent.getXPath());								
							}
						}
					});

				});
		rootGlobalVariables.getChildVariables().forEach(c -> checkServiceVariables(fullConfig, fullConfigExport, c, repo, env, archiveName, messages));
	}

	private static String parseKey(GlobalVariables parentGlobalVariables, String childVariableName) {
		return getChildVariable(parentGlobalVariables, childVariableName).getPath();
	}

	public static GlobalVariable getChildVariable(GlobalVariables parentGlobalVariables, String childVariableName) {
		return parentGlobalVariables.resolve(childVariableName).get();
	}
}
