package com.meluzin.tibcobwutils.earcomparer;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.Lists;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V3;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.PasswordDecrypter;
import com.meluzin.tibcobwutils.earcomparer.load.LoadZipFile;


public class EARComparer {
	private boolean replaceNewLines = false;
	private boolean removeAdapterSDKPropertiesFromTIBCOXML = true;

	public EARComparer() {
	}
	public EARComparer(boolean replaceNewLines) {
		this.replaceNewLines = replaceNewLines;
	}
	
	public boolean isRemoveAdapterSDKPropertiesFromTIBCOXML() {
		return removeAdapterSDKPropertiesFromTIBCOXML;
	}

	public void setRemoveAdapterSDKPropertiesFromTIBCOXML(boolean removeAdapterSDKPropertiesFromTIBCOXML) {
		this.removeAdapterSDKPropertiesFromTIBCOXML = removeAdapterSDKPropertiesFromTIBCOXML;
	}
	
	public boolean isReplaceNewLines() {
		return replaceNewLines;
	}
	
	public static void main(String[] args) {
		Path p1 = Paths.get("!/AMDOCS_HLAPIv8_IO.aar!/Adapters/AMDOCS_HLAPIv8_IO.adapter");
		Path p2 = Paths.get("!/AMDOCS_HLAPIv8_IO.aar!//Adapters/AMDOCS_HLAPIv8_IO.adapter");
		System.out.println(p1 +" "+ p2+" " + p1.equals(p2));
		FileSearcher s = new FileSearcher();
//		s.searchFiles(Paths.get("T:/temp/compare/ears"), "glob:**/ERMS_API_IO.ear", false).forEach(p -> {
//			Path p1 =  p.getParent().getParent().resolve("init-build").resolve("FILENET_API_IO.ear");
//			System.out.println(p + " " + p1);
//			new EARComparer().compare(p,p1).forEach(r -> System.out.println(r));;
//		});;
		new EARComparer().compare(Paths.get("T:/temp/EPC_PISA_CORE.old.ear"), Paths.get("T:/temp/EPC_PISA_CORE.new.ear")).forEach(r -> System.out.println(r));;
		Date started = new Date();
		//-old t:\temp\compare\_deployed\ENV_EAI_PRD\AP_TIBOR_IN.ear -new t:\temp\compare\build\R171126\tibor\AP_TIBOR_IN.ear
//		s.searchFiles(Paths.get("t:\\temp\\compare\\build\\R171126\\adb\\"), "glob:**/*.ear", true).forEach(p -> {
//			new EARComparer().compare(Paths.get("t:\\temp\\compare\\_deployed\\ENV_EAI_PRD").resolve(p.getFileName()), p).forEach(r -> System.out.println(r));;
//				
//		});
		System.out.println(new Date().getTime() - started.getTime());
	}	
	private List<String> ignoreList = Lists.asList("library.manifest");	
	public List<CompareResult> compare(Path oldPath, Path newPath) {
		XmlBuilderFactory fac = new XmlBuilderFactory().setPreserveWhitespace(false);
		List<T.V3<String, byte[], ZipEntry>> oldFiles = oldPath.toFile().exists() ? new LoadZipFile().load(oldPath) : Arrays.asList();
		List<T.V3<String, byte[], ZipEntry>> newFiles = newPath.toFile().exists() ? new LoadZipFile().load(newPath) : Arrays.asList();
		return compareArchives(oldPath, fac, oldFiles, newFiles);
	}
	private List<CompareResult> compareArchives(Path oldArchive, XmlBuilderFactory fac, List<T.V3<String, byte[], ZipEntry>> oldFiles,
			List<T.V3<String, byte[], ZipEntry>> newFiles) {
		List<Path> oldEarFilesString = oldFiles.stream().map(t -> t.getA()).sorted().map(p -> Paths.get(p)).collect(Collectors.toList());;
		List<Path> newEarFilesString = newFiles.stream().map(t -> t.getA()).sorted().map(p -> Paths.get(p)).collect(Collectors.toList());
		if (oldEarFilesString.equals(newEarFilesString)) {
			return compareTwoLists(oldArchive, fac, oldFiles, newFiles);
		}
		else {
			List<Path> deletedFiles = new ArrayList<>(oldEarFilesString);
			deletedFiles.removeAll(newEarFilesString);
			List<Path> addedFiles = new ArrayList<>(newEarFilesString);
			addedFiles.removeAll(oldEarFilesString);
			List<CompareResult> results = new ArrayList<>();
			if (deletedFiles.size() > 0) {
				deletedFiles.forEach(f -> {
					results.add(new CompareResult(f, CompareResultStatus.DifferentParsSarsAarsDeletedFile, "Deleted file: " + f));
					if (isArchiveFile(f.toString())) {
						List<V3<String, byte[], ZipEntry>> oldArchiveFiles = new LoadZipFile().load(f.toString(), oldFiles.stream().filter(v -> v.getA().equals(f.toString().replace("\\", "/"))).map(v -> v.getB()).findAny().get());
						results.addAll(compareArchives(f, fac, oldArchiveFiles, Arrays.asList()));
					}
				});
				
			}
			if (addedFiles.size() > 0) {
				addedFiles.forEach(f -> {					
					results.add(new CompareResult(f, CompareResultStatus.DifferentParsSarsAarsAddedFile, "Added file: " + f));
					if (isArchiveFile(f.toString())) {
						List<V3<String, byte[], ZipEntry>> newArchiveFiles = new LoadZipFile().load(f.toString(), newFiles.stream().filter(v -> v.getA().equals(f.toString().replace("\\", "/"))).map(v -> v.getB()).findAny().get());
						results.addAll(compareArchives(f, fac, Arrays.asList(), newArchiveFiles));
					}
				});
			}

			List<T.V3<String, byte[], ZipEntry>> oldF = oldFiles.stream().filter(f -> newFiles.stream().anyMatch(v -> v.getA().equals(f.getA())) ).collect(Collectors.toList());
			List<T.V3<String, byte[], ZipEntry>> newF = newFiles.stream().filter(f -> oldFiles.stream().anyMatch(v -> v.getA().equals(f.getA())) ).collect(Collectors.toList());

			List<CompareResult> subResult = compareTwoLists(oldArchive, fac, oldF, newF);
			results.addAll(subResult);
			return results;
		}
	}
	private List<CompareResult> compareTwoLists(Path archive, XmlBuilderFactory fac,
			List<T.V3<String, byte[], ZipEntry>> oldFiles, List<T.V3<String, byte[], ZipEntry>> newFiles) {
		List<CompareResult> results = new ArrayList<>();
		for (int i = 0; i < oldFiles.size(); i++) {
			T.V2<String, byte[]> oldFile = oldFiles.get(i);
			T.V2<String, byte[]> newFile = newFiles.get(i);
			
			Path oldArchive = Paths.get(oldFile.getA());
			CompareResult result = compareTwoFiles(fac, new CompareResult(oldArchive, CompareResultStatus.Equals, "Non"), oldFile, newFile);
			if (result.getStatus() != CompareResultStatus.Equals)
			{
				if (isArchiveFile(oldFile.getA())) {
					List<V3<String, byte[], ZipEntry>> loadOldFiles = new LoadZipFile().load(oldFile.getA(), oldFile.getB());
					List<V3<String, byte[], ZipEntry>> loadNewFiles = new LoadZipFile().load(newFile.getA(), newFile.getB());
					List<CompareResult> subResult = compareArchives(oldArchive, fac, loadOldFiles, loadNewFiles);
					if (subResult.size() > 0 && subResult.get(0).getStatus() != CompareResultStatus.Equals) {
						result = new CompareResult(oldArchive, CompareResultStatus.DifferentContent, result.getMessage() + ": " + subResult.get(0).getMessage());
						results.addAll(subResult);							
					}
				}
				else {
					results.add(result);
				}
			}
		}
		return results;
	}
	private boolean isXmlFile(String fileName) {
		return 
				fileName.endsWith(".process") || 
				fileName.endsWith(".xml") || 
				fileName.endsWith(".xsd") || 
				fileName.endsWith(".wsdl") || 
				fileName.endsWith(".aeschema") || 
				fileName.endsWith(".serviceagent") || 
				fileName.endsWith(".rvtransport") || 
				fileName.endsWith(".adfiles") || 
				fileName.endsWith(".javaxpath") ||
				fileName.endsWith(".adr3") ||
				fileName.endsWith(".adb") ||
				fileName.endsWith(".sharedparse") ||
				fileName.endsWith(".httpProxy") ||
				fileName.endsWith(".adapter") ||
				fileName.endsWith(".adr3Connections");
	}
	private boolean isArchiveFile(String oldFile) {
		return 
				oldFile.endsWith(".aar") ||
				oldFile.endsWith(".par") ||
				oldFile.endsWith(".sar") ||
				oldFile.endsWith(".zip");
	}
	private CompareResult compareTwoFiles(XmlBuilderFactory fac, CompareResult result,
			T.V2<String, byte[]> oldFile, T.V2<String, byte[]> newFile) {
		Path oldFilePath = Paths.get(oldFile.getA());
		Path newFilePath = Paths.get(newFile.getA());
		if (!oldFilePath.equals(newFilePath)) {
			return new CompareResult(oldFilePath, CompareResultStatus.DifferentFileNames, oldFile.getA() + " != " + newFile.getA());
		}
		String fileName = oldFilePath.getFileName().toString();
		if (ignoreList.contains(fileName)) {
		    return new CompareResult(oldFilePath, CompareResultStatus.Equals, "Non");
		}
		if (!new String(oldFile.getB()).equals(new String(newFile.getB()))) {
			if (isXmlFile(fileName)) {
				NodeBuilder oldFileXml = fac.parseDocument(new ByteArrayInputStream(oldFile.getB()));
				NodeBuilder newFileXml = fac.parseDocument(new ByteArrayInputStream(newFile.getB()));
				Optional<String> adapterName = oldFileXml.search("adapter").map(n -> n.getAttribute("name")).findFirst();
				if (fileName.equals("TIBCO.xml")) {
					oldFileXml = normalizeTibcoXML(oldFileXml);
					newFileXml = normalizeTibcoXML(newFileXml);					
				} else if (fileName.endsWith(".adapter") || fileName.endsWith(".adfiles") || fileName.endsWith(".adr3")) {
					//if (adapterName.isPresent() && adapterName.get().toLowerCase().contains("tibor")) {						
						oldFileXml = normalizeTiborAdapterXML(oldFileXml);
						newFileXml = normalizeTiborAdapterXML(newFileXml);		
					//}
				} else if (fileName.endsWith(".adb")) {
						oldFileXml = normalizeAdbAdapterXML(oldFileXml);
						newFileXml = normalizeAdbAdapterXML(newFileXml);
				}
				
				if (!oldFileXml.equalsTo(newFileXml)) {
					Optional<String> firstDiff = oldFileXml.getFirstDiff(newFileXml);
					if (replaceNewLines && firstDiff.isPresent()) {
						firstDiff = Optional.of(firstDiff.get().replace("\r\n", " ").replace("\n", ""));
					}
					return new CompareResult(oldFilePath, CompareResultStatus.DifferentContent, "Content of: "  + oldFile.getA() + " != " + newFile.getA() + " - " + firstDiff);
				}
			} 
			else {
				return new CompareResult(oldFilePath, CompareResultStatus.DifferentContent, "Content of: "  + oldFile.getA() + " != " + newFile.getA());
			}
		}
		return result;
	}
	private NodeBuilder normalizeAdbAdapterXML(NodeBuilder oldFileXml) {
		//oldFileXml.search(true, n -> true).forEach(n -> n.sortChildren((a,b) -> a.getName().compareTo(b.getName())));
		oldFileXml.search(true, "operations").filter(n -> n.getChildren().size() == 0).collect(Collectors.toList()).forEach(n -> n.getParent().removeChild(n));;
		oldFileXml.search(true, "properties").filter(n -> n.getTextContent() != null).forEach(n -> n.setTextContent(Lists.asList(n.getTextContent().split(",")).stream().sorted().collect(Collectors.joining(","))));
		return oldFileXml;
	}
	private NodeBuilder normalizeTiborAdapterXML(NodeBuilder oldFileXml) {
		oldFileXml.search(true, n -> true).forEach(n -> n.sortChildren((a,b) -> a.getName().compareTo(b.getName())));
		oldFileXml.search(true, "properties").filter(n -> n.getTextContent() != null).forEach(n -> n.setTextContent(Lists.asList(n.getTextContent().split(",")).stream().sorted().collect(Collectors.joining(","))));
		return oldFileXml;
	}
	private NodeBuilder normalizeTibcoXML(NodeBuilder oldFileXml) {
		sortNameValuePairs(oldFileXml);
		clearNotImportantValues(oldFileXml);
		sortExternalDependencies(oldFileXml);
		sortBwBPConfigurations(oldFileXml);
		removeAliasesDefinition(oldFileXml);
		removeWhiteSpaces(oldFileXml);
		if (isRemoveAdapterSDKPropertiesFromTIBCOXML()) {
			removeSDKVariables(oldFileXml);
		}
		removeSettableVariables(oldFileXml);
		
		//System.out.println(oldFileXml);
		return oldFileXml;
	}
	public void removeSettableVariables(NodeBuilder oldFileXml) {
		oldFileXml.
			search("NameValuePairs").
				filter(n -> n.hasChild(c -> "name".equals(c.getName()) && Lists.asList("Global Variables", "Runtime Variables").contains(c.getTextContent()))).
				map(s -> s.search("NameValuePair").filter(n -> n.hasChild(c -> "requiresConfiguration".equals(c.getName()) && "true".equals(c.getTextContent())))).flatMap(n -> n).
				collect(Collectors.toList()).
				forEach(n -> n.getParent().removeChild(n));
	}
	public void removeSDKVariables(NodeBuilder oldFileXml) {
		oldFileXml.search("NameValuePairs").filter(n -> n.hasChild(c -> "name".equals(c.getName()) && "Adapter SDK Properties".equals(c.getTextContent()))).
		collect(Collectors.toList()).
		forEach(n -> n.getParent().removeChild(n));
	}
	private void removeWhiteSpaces(NodeBuilder oldFileXml) {
		oldFileXml.
			search(true, n -> n.isTextNode() && n.getTextContent().trim().length() == 0).
			collect(Collectors.toList()).
			forEach(n -> n.getParent().removeChild(n));
	}
	private void removeAliasesDefinition(NodeBuilder oldFileXml) {
		oldFileXml.search(true, 
				n -> 
					"NameValuePairs".equals(n.getName()) && 
					n.hasChild(name -> "name".equals(name.getName()) && "FileAliases".equals(name.getTextContent()))
			).
			collect(Collectors.toList()).
			forEach(n -> n.getParent().removeChild(n));
		
	}
	private void sortBwBPConfigurations(NodeBuilder oldFileXml) {
		NodeBuilder bwConfigs = oldFileXml.searchFirst(true, n -> "BwBPConfigurations".equals(n.getName()));
		if (bwConfigs != null) {
			bwConfigs.sortChildren((a,b) -> {
				int c = a.getName().compareTo(b.getName());
				if (c == 0 && "BwBPConfiguration".equals(a.getName())) { 
					NodeBuilder bProcess = b.searchFirstByName("processDefinitionName");
					NodeBuilder aProcess = a.searchFirstByName("processDefinitionName");
					return aProcess.getTextContent().compareTo(bProcess.getTextContent());
				}
				return c;
			});
		}
	}
	private void sortExternalDependencies(NodeBuilder oldFileXml) {
		oldFileXml.
			search(true, n -> "NameValuePair".equals(n.getName()) && "EXTERNAL_RESOURCE_DEPENDENCY".equals(n.searchFirstByName("name").getTextContent())).
			forEach(n ->{
				NodeBuilder value = n.searchFirstByName("value");
				String dependencies = value.getTextContent();
				if (dependencies != null) {
					String[] deps = dependencies.split(",");
					List<String> depsList = new ArrayList<>(Lists.asList(deps));
					depsList.sort(String.CASE_INSENSITIVE_ORDER);
					String d = Lists.join(depsList, ",");
					value.setTextContent(d);
				}
			});
	}
	private NodeBuilder clearNotImportantValues(NodeBuilder xml) {
		xml.search(n -> "description".equals(n.getName())).forEach(n -> n.setTextContent(null));
		xml.search(n -> "version".equals(n.getName())).forEach(n -> n.setTextContent(null));
		xml.search(n -> "owner".equals(n.getName())).forEach(n -> n.setTextContent(null));
		xml.search(n -> "creationDate".equals(n.getName())).forEach(n -> n.setTextContent(null));
		decryptNVPasswords(xml);
		return xml;		
	}
	protected static void decryptNVPasswords(NodeBuilder xml) {
		xml.search(true, n -> "NameValuePairPassword".equals(n.getName())).forEach(n -> {
			NodeBuilder val = n.searchFirstByName("value");
			if (val != null && val.getTextContent() != null) {
				val.setTextContent(new PasswordDecrypter().decrypt(val.getTextContent()));
			}
		});
	}
	private void sortNameValuePairs(NodeBuilder oldFileXml) {
		List<NodeBuilder> oldNameValuePairs = oldFileXml.search(true, c -> "NameValuePairs".equals(c.getName())).collect(Collectors.toList());
		oldNameValuePairs.forEach(n -> n.sortChildren((a,b) -> {
			if (a.getName().equals(b.getName())) {
				if (a.getName().contains("NameValuePair")) {
					int c = compareElementText(a, b, "name");
					if (c == 0) {
						return compareElementText(a, b, "value");
					}
					else return c;
				}
				else return a.toString().compareTo(b.toString());
			}
			else return a.getName().compareTo(b.getName());
		}));
	}
	protected static int compareElementText(NodeBuilder a, NodeBuilder b, String elName) {
		NodeBuilder firstValue = a.searchFirstByName(elName);
		NodeBuilder secondValue = b.searchFirstByName(elName);
		if (firstValue != secondValue) {
			if (firstValue == null) {
				return -1;
			}
			else if (secondValue == null) {
				return 1;
			}
			else {
				String firstContent = firstValue.getTextContent();
				String secondContent = secondValue.getTextContent();
				if (firstContent != secondContent) {
					if (firstContent == null) {
						return -1;
					}
					else if (secondContent == null) {
						return 1;
					}
					else {
						return firstContent.compareTo(secondContent);
					}
				}
				else {
					return 0;
				}
			}
		}
		return 0;
	}
	protected static int compareAttributeValue(NodeBuilder a, NodeBuilder b, String attrName) {
		String firstValue = a.getAttribute(attrName);
		String secondValue = b.getAttribute(attrName);
		if (firstValue != secondValue) {
			if (firstValue == null) {
				return -1;
			}
			else if (secondValue == null) {
				return 1;
			}
			else {
				return firstValue.compareTo(secondValue);
			}
		}
		return 0;
	}
}
