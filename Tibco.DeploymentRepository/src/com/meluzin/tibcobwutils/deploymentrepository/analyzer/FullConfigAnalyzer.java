package com.meluzin.tibcobwutils.deploymentrepository.analyzer;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Repository;

public class FullConfigAnalyzer {
	private Repository repo;
	
	public FullConfigAnalyzer(Repository repo) {
		this.repo = repo;
	}
	
	public Set<String> findBWProcessStarters(Item archiveItem) {
		List<Item> processes = archiveItem.loadAsXml().search(true, "processProperty").
					map(n -> n.getTextContent()).
					filter(s -> s != null).
					map(s -> Arrays.asList(s.split(",")).stream()).
					flatMap(s -> s).
					filter(s -> s.length() > 0).
					map(s -> s.charAt(0) == '/' ? s.substring(1) : s).
					map(s -> repo.findItem(s)).
					filter(m -> m.isPresent()).
					map(m -> m.get()).
					collect(Collectors.toList());
		Set<Item> subProcesses = loadSubProcessesForFolders(processes);
		Set<Item> found = new HashSet<>();
		Queue<Item> toSearch = new LinkedBlockingQueue<>(subProcesses);
		while (!toSearch.isEmpty()) {
			Item item = toSearch.poll();
			if (!found.contains(item)) {
				List<Item> foundSubProcesses = findSubProcesses(item);
				found.add(item);
				toSearch.addAll(foundSubProcesses);
			}
		}
		
		
		return found.stream().filter(i -> isItemStarter(i)).map(i -> i.getDeploymentReference().substring(1).replace("\\", "/")).collect(Collectors.toSet());
	}

	private Set<Item> loadSubProcessesForFolders(Collection<Item> processes) {
		Stream<Item> flatMap = processes.stream().filter(i -> i.isFolder()).map(i -> Stream.concat(Stream.of(i),loadSubProcessesForFolders(i.getChildren()).stream())).flatMap(i -> i);
		Stream<Item> processesStream = processes.stream();
		return Stream.concat(flatMap, processesStream).collect(Collectors.toSet());
	}

	private boolean isItemStarter(Item item) {
		switch (item.getItemType()) {
		case ServiceAgent:
			return true;
		case Process:
			return item.loadAsXml().search("starter").count() == 1;
		default:
			break;
		}
		return false;
	}
	public Stream<NodeBuilder> getActivities(NodeBuilder processXml, String activityType) {
		return processXml.search(true, n -> "activity".equals(n.getName()) && "http://xmlns.tibco.com/bw/process/2003".equals(n.getNamespace()) && n.searchFirstByName("type")!=null && activityType.equals(n.searchFirstByName("type").getTextContent()));
	}

	public Stream<NodeBuilder> getSubprocessCalls(NodeBuilder processXml) {
		return getActivities(processXml, "com.tibco.pe.core.CallProcessActivity");
	}
	public Stream<NodeBuilder> getJavaMethods(NodeBuilder processXml) {
		return getActivities(processXml, "com.tibco.plugin.java.JavaMethodActivity");
	}

	public String getSubprocessCallPath(NodeBuilder activity) {
		NodeBuilder searchFirst = activity.searchFirst(true, n -> "processName".equals(n.getName()) && null == n.getNamespace());
		return searchFirst == null ? null : searchFirst.getTextContent();
	}
	public String getJavaGlobalInstance(NodeBuilder activity) {
		NodeBuilder javaGlobalInstanceNode = activity.searchFirst(true, n -> "JavaGlobalInstance".equals(n.getName()) && null == n.getNamespace());
		return javaGlobalInstanceNode == null ? null : javaGlobalInstanceNode.getTextContent();
	}
	
	private List<Item> findSubProcesses(Item m) {
		Stream<String> list = Stream.empty();
		switch (m.getItemType()) {
		 	case ServiceAgent:
				NodeBuilder itemXml = m.loadAsXml();
		 		list = itemXml.search(true, "row").map(n -> n.getAttribute("opImpl")).filter(n -> n != null);
		 		break;
		 	case Process:
				NodeBuilder itemXmlProcess = m.loadAsXml();
		 		list = getSubprocessCalls(itemXmlProcess).map(n -> getSubprocessCallPath(n)).filter(n -> n != null);
		 		list = Stream.concat(list, getJavaMethods(itemXmlProcess).map(n -> getJavaGlobalInstance(n)).filter(n -> n != null));
		 		break;
			default:
				break;
		}
		return list.map(p -> repo.findItem(p)).filter(i -> i.isPresent()).map(i -> i.get()).collect(Collectors.toList());
	}
}
