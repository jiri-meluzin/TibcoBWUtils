package com.meluzin.tibcobwutils.earcomparer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.T;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.SDKPropertiesLoader;

public class CompareInstanceVariables {
	public static void main(String[] args) {
		Path tibcoHome = Paths.get("t:/tib/app/tibco");
		SDKPropertiesLoader loader = new SDKPropertiesLoader(tibcoHome);
		XmlBuilderFactory fac = new XmlBuilderFactory();
		NormalizeConfig norm = new NormalizeConfig(loader);
		new FileSearcher().searchFiles(Paths.get("T:/source/R170624/_config/ENV_EAI_TST/"), "glob:**/*.xml", true).
			stream().map(p -> T.V(p, p.getParent().getParent().getParent().getParent().resolve("R170903/_config/ENV_EAI_TST2/").resolve(p.getFileName()))).
					filter(v -> v.getB().toFile().exists()).
					map(v -> T.V(v.getA(), norm.loadFullConfig(v.getA(), false), v.getB(), norm.loadFullConfig(v.getB(), false))).
					forEach(v -> 
						{
							/*System.out.println(
						
								v.getA() + " " +
								v.getB().search(true, n -> "binding".equals(n.getName())).count() + " " +
								v.getD().search(true, n -> "binding".equals(n.getName())).count()
								
							);*/
							
							if (v.getB().search(true, n -> "binding".equals(n.getName())).count() < v.getD().search(true, n -> "binding".equals(n.getName())).count()) {
								
								NodeBuilder config = fac.loadFromFile(v.getA());
								NodeBuilder binding = v.getD().search(true, n -> "binding".equals(n.getName())).filter(n -> n.hasChild(ch -> "machine".equals(ch.getName()) && "hkvnode316.cz.tmo".equals(ch.getTextContent()))).findAny().get();
								binding.searchFirstByName("machine").setTextContent("hkvnode310.cz.tmo");
								config.search(true, n -> "bindings".equals(n.getName())).findAny().get().appendChild(
										binding.copy()
										);
								fac.renderNode(config, v.getA());
								
							}

							NodeBuilder bindings1 = v.getB().search(true, n -> "bindings".equals(n.getName())).findAny().get();
							NodeBuilder bindings2 = v.getD().search(true, n -> "bindings".equals(n.getName())).findAny().get();
							bindings1.search(true, n -> "machine".equals(n.getName())).forEach(n -> n.setTextContent(null));;
							bindings2.search(true, n -> "machine".equals(n.getName())).forEach(n -> n.setTextContent(null));;
							if (!bindings1.equalsTo(bindings2)) {
								
								List<NodeBuilder> nvPairs1 = bindings1.search(true, n -> "NVPairs".equals(n.getName())).collect(Collectors.toList());
								List<NodeBuilder> nvPairs2 = bindings2.search(true, n -> "NVPairs".equals(n.getName())).collect(Collectors.toList());
								
								for (int i = 0; i < nvPairs1.size(); i++) {
									try {
										final int index = i;
										if (!nvPairs1.get(i).equals(nvPairs2.get(i))) {
											System.out.println(v.getA() + " " + i  + " " + nvPairs1.get(i).getFirstDiff(nvPairs2.get(i)));
											NodeBuilder config = fac.loadFromFile(v.getA());
											NodeBuilder binding = 
													config.
													search(true, n -> "binding".equals(n.getName())).
													filter(n -> n.hasChild(
															ch -> 
															"machine".equals(ch.getName()) && 
															(index == 0 ? "hkvnode309.cz.tmo" : "hkvnode310.cz.tmo").
															equals(ch.getTextContent()))).
													findAny().
													get();
											NodeBuilder nvPairs = binding.searchFirstByName("NVPairs");
											binding.removeChild(nvPairs);
											binding.appendChild(nvPairs2.get(i));
											fac.renderNode(config, v.getA());
										}	
									} catch (Exception exception) {
										System.out.println(v.getA());
										exception.printStackTrace();
									}
								}
								
							}
							
						});
			;
	}
}
