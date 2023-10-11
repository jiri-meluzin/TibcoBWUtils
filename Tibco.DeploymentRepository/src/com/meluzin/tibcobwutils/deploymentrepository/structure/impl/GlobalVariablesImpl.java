package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.fluentxml.xml.builder.XmlBuilderSAXFactory;
import com.meluzin.fluentxml.xml.builder.XmlBuilderSAXFactory.Settings;
import com.meluzin.functional.Lists;
import com.meluzin.functional.T;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariable;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariables;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSourceType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Repository;

public class GlobalVariablesImpl implements GlobalVariables {
	private Repository repository;
	private List<GlobalVariable> localVariables;
	private List<GlobalVariables> childVariables;
	private Item originalItem;
	private Item parentItem;
	boolean isChanged = false;
	private String path;
	private GlobalVariables parent;
	private Map<String, List<GlobalVariable>> alternativesVariables = null;
	public GlobalVariablesImpl(Item originalItem, Item parentItem, Repository repository) {
		this.originalItem = originalItem;
		this.parentItem = parentItem;
		if (originalItem != null && originalItem.getItemType() != ItemType.GlobalVariable) throw new IllegalArgumentException("item is not a global variable, it is (" + originalItem.getItemType() + ")");
		this.repository = repository;
		if (parentItem.getParent().isRoot()) this.path = "";
		else this.path = parentItem.getName();
	}
	public GlobalVariablesImpl(GlobalVariables parent, Item originalItem, Item parentItem, Repository repository) {
		this(originalItem, parentItem, repository);
		this.parent = parent;
	}
	@Override
	public String getPath() {
		return parent == null ? path : (parent.getPath().length() == 0 ? "" : parent.getPath() + "/") + path;
	}
	@Override
	public List<GlobalVariable> getLocalVariables() {
		if (localVariables == null) {
			if (originalItem == null || originalItem.getItemSource().getType() == ItemSourceType.Library) localVariables = new ArrayList<>();
			else localVariables = originalItem.hasContent() ? load(originalItem.loadAsXml(), originalItem) : new ArrayList<>();
		}
		return localVariables; 
	}
	@Override
	public GlobalVariable makeVariableLocal(GlobalVariable variable) {
		if (!getLocalVariables().stream().anyMatch(i -> i.getName().equals(variable.getName()))) {
			return addVariable(variable.getName()).
					setDeploymentSettable(variable.isDeploymentSettable()).
					setDescription(variable.getDescription()).
					setModTime(variable.getModTime()).
					setServiceSettable(variable.isServiceSettable()).
					setType(variable.getType()).
					setValue(variable.getValue());
		}
		else return variable;
	}
	@Override
	public synchronized Map<String, List<GlobalVariable>> getAllVariables() {	
		if (originalItem == null) return new HashMap<>();
		else {
			List<Item> alternatives2 = repository.getAlternatives(originalItem);
			if (alternativesVariables == null) {
				Stream<GlobalVariable> map = alternatives2.stream().map(item -> load(item.loadAsXml(), item)).flatMap(l -> l.stream());
				map = Stream.concat(getLocalVariables().stream(), map);
				alternativesVariables = map.collect(Collectors.groupingBy(i -> i.getName()));
			}
			return alternativesVariables;
		}
	}
	@Override
	public boolean isChanged() {
		return isChanged;
	}
	private void modify() {
		this.isChanged = true;
		alternativesVariables = null;
	}
	@Override
	public GlobalVariable addVariable(String name) {
		if (originalItem == null) {
			originalItem = repository.createItem(parentItem, "defaultVars.substvar");
		}
		GlobalVariable var = new GlobalVariableImpl(originalItem).setName(name);
		getLocalVariables().add(var);
		modify();
		return var;
	}

	@Override
	public List<GlobalVariables> getChildVariables() {
		if (childVariables == null) {
			childVariables = parentItem.
				getChildren().
				stream().
				filter(i -> ItemType.Folder == i.getItemType()).
				map(parent -> T.V(parent, parent.getChildren().stream().filter(c -> ItemType.GlobalVariable == c.getItemType()).findAny().orElse(null))).
				//filter(i -> i != null).
				map(tupple -> new GlobalVariablesImpl(this, tupple.getB(), tupple.getA(), repository)).
				collect(Collectors.toList());
		}
		return childVariables;
	}
	
	private List<GlobalVariable> load(NodeBuilder content, Item sourceItem) {
		boolean equals = sourceItem.getDeploymentReference().equals(originalItem.getDeploymentReference()) && sourceItem.getItemSource().getType() == ItemSourceType.Deployment;
		if (equals && localVariables != null) {
			return localVariables;
		}
		List<GlobalVariable> globalVariables = content.search(true, n -> "globalVariable".equals(n.getName())).map(c -> new GlobalVariableImpl(c, sourceItem)).collect(Collectors.toList());
		if (equals && localVariables == null) {
			this.localVariables = globalVariables;
		} 
		return globalVariables;
	}
	private NodeBuilder render(List<GlobalVariable> variables) {
		XmlBuilderFactory fac = new XmlBuilderFactory();
		NodeBuilder n = fac.createRootElement("repository").addNamespace("http://www.tibco.com/xmlns/repo/types/2002");
		n.addChild("globalVariables").addChildren(variables, (var, parent) -> 
			parent.
				addChild("globalVariable").
					addChild("name").setTextContent(var.getName()).getParent().
					addChild("value").setTextContent(var.getValue()).getParent().
					addChild((GlobalVariable)var, (v, p) -> {
							if (v.getDescription() != null) p.addChild("description").setTextContent(v.getDescription());
						}).
					addChild("deploymentSettable").setTextContent(var.isDeploymentSettable()).getParent().
					addChild("serviceSettable").setTextContent(var.isServiceSettable()).getParent().
					addChild("type").setTextContent(var.getType()).getParent().
					addChild("modTime").setTextContent(var.getModTime()).getParent()
			);
		return n;
	}
	@Override
	public GlobalVariables removeVariable(String name) {
		getLocalVariables().removeIf(g -> name != null && name.equals(g.getName()));
		modify();
		return this;
	}
	@Override
	public void save() {
		if (isChanged()) {
			NodeBuilder xml = render(getLocalVariables());
			if (originalItem.getItemSource().getType() == ItemSourceType.Library) {
				Item createItem = repository.createItem(originalItem.getPath());
				originalItem = renderGVarsXML(xml, createItem);
			}
			else {
				renderGVarsXML(xml, originalItem);
			}
		}
		isChanged = false;
		if (childVariables != null) childVariables.forEach(c -> c.save());
		//super.save();
	}
	private Item renderGVarsXML(NodeBuilder xml, Item createItem) {

			try (OutputStream stream = createItem.setContent()) {
				XmlBuilderSAXFactory.getSingleton().renderNode(xml,Settings.builder().padding("\t").build(), true,  stream);
			} catch (IOException e) {
				throw new RuntimeException("Could not update content (" + getPath() + ")", e);
			}
			return createItem;
	}
	
	@Override
	public String getName() {
		String[] split = getPath().split("/");
		return split[split.length - 1];
	}

	@Override
	public Optional<GlobalVariable> resolve(String varRelativePath) {
		if (varRelativePath.length() == 0) return Optional.empty();
		String[] parts = varRelativePath.split("/");
		String name = parts[0];
		if (parts.length > 1) {
			Predicate<? super GlobalVariables> predicate = v -> v.getName().equals(name);
			GlobalVariables vars = getChildVariables().stream().filter(predicate).findAny().orElse(null);
			if (vars == null) return Optional.empty(); 			
			else return vars.resolve(String.join("/", Arrays.copyOfRange(parts, 1, parts.length)));
		}
		else {
			if (getAllVariables().get(name) == null) return Optional.empty();
			else return Optional.of(getAllVariables().get(name).get(0));
		}
	}
	@Override
	public Optional<GlobalVariable> resolve(Path varRelativePath) {
		return resolve(normalizePath(varRelativePath));
	}
	
	@Override
	public GlobalVariable resolveOrCreate(String varRelativePath) {
		String[] parts = varRelativePath.split("/");
		String name = parts[0];
		if (parts.length > 1) {
			GlobalVariables vars = getChildVariables().stream().filter(v -> name.equals(v.getName())).findAny().orElse(null);
			//GlobalVariables vars = getChildVariables().stream().filter(v -> v.getPath().getFileName().equals(name)).findAny().orElse(null);
			if (vars == null) {
				vars = addVariables(name);
			}
			return vars.resolveOrCreate(String.join("/", Arrays.copyOfRange(parts, 1, parts.length)));
		}
		else {
			if (getAllVariables().get(name) == null) return addVariable(name);
			else return getAllVariables().get(name).get(0);
		}	
	}
	@Override
	public GlobalVariable resolveOrCreate(Path varRelativePath) {
		return resolveOrCreate(normalizePath(varRelativePath));
	}
	
	@Override
	public Optional<GlobalVariables> resolveVars(String varRelativePath) {
		if (varRelativePath.length() == 0) return Optional.empty();
		String[] parts = varRelativePath.split("/");
		String name = parts[0];
		GlobalVariables vars = getChildVariables().stream().filter(v -> v.getName().equals(name)).findAny().orElse(null);
		if (vars == null) {
			return Optional.of(this);
		}
		else if (parts.length > 1) {
			return vars.resolveVars(String.join("/", Arrays.copyOfRange(parts, 2, parts.length)));
		}		
		return Optional.of(vars);
	}
	@Override
	public Optional<GlobalVariables> resolveVars(Path varRelativePath) {
		return resolveVars(normalizePath(varRelativePath));
	}
	private String normalizePath(Path varRelativePath) {
		return varRelativePath.toString().replace("\\", "/");
	}

	@Override
	public GlobalVariables addVariables(String name) {
		GlobalVariables child = getChildVariables().stream().filter(v -> name.equals(v.getName())).findAny().orElse(null);
		if (child == null) {
			Path path2 = originalItem == null ? parentItem.getPath() : originalItem.getPath().getParent();
			Item newItem = repository.createItem(path2.resolve(Paths.get(name, "defaultVars.substvar")));
			newItem.updateContent(render(new ArrayList<>()));
			child = new GlobalVariablesImpl(this, newItem, newItem.getParent(), repository);
			getChildVariables().add(child);
		}
		return child;
	}
	
	@Override
	public String resolveExpression(String expression) {
		Pattern p = Pattern.compile("%%([^%]*)%%");
		Matcher m = p.matcher(expression);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String group = m.group(1);
			Path varRelativePath = Paths.get(group);
			Optional<GlobalVariable> resolve = resolve(varRelativePath);
			if (!resolve.isPresent()) {
				throw new InvalidParameterException("Cannot resolve "+varRelativePath + " variable");
			}
			String resolvedValue = resolve.get().getValue();
			if ("Password".equals(resolve.get().getType())) {
				resolvedValue = new PasswordDecrypter().decrypt(resolvedValue);
			}
			m.appendReplacement(sb, resolvedValue);
		}
		m.appendTail(sb);
		return sb.toString();
	}
	
	public class GlobalVariableImpl implements GlobalVariable {
		private String name;
		private String value;
		private String description;
		private boolean deploymentSettable = true;
		private boolean serviceSettable = false;
		private String type = "String";
		private long modTime;
		
		private Item sourceItem;
		public GlobalVariableImpl(Item sourceItem) {
			this.sourceItem = sourceItem;
			this.modTime = new Date().getTime();			
		}
		public GlobalVariableImpl(NodeBuilder content, Item sourceItem) {
			this.sourceItem = sourceItem;
			this.name = content.searchFirstByName("name").getTextContent();
			this.value = content.searchFirstByName("value").getTextContent();
			this.description = content.searchFirstByName("description") == null ? null : content.searchFirstByName("description").getTextContent();
			this.deploymentSettable = "true".equals(content.searchFirstByName("deploymentSettable").getTextContent());
			this.serviceSettable = "true".equals(content.searchFirstByName("serviceSettable").getTextContent());
			this.type = content.searchFirstByName("type").getTextContent();
			this.modTime = Long.parseLong(content.searchFirstByName("modTime").getTextContent());
		}
		
		@Override
		public Item getSourceItem() {
			return sourceItem;
		}
		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getDescription() {
			return description;
		}
		
		@Override
		public String getValue() {
			return value;
		}

		@Override
		public boolean isDeploymentSettable() {
			return deploymentSettable;
		}

		@Override
		public boolean isServiceSettable() {
			return serviceSettable;
		}

		@Override
		public String getType() {
			return type;
		}

		@Override
		public long getModTime() {
			return modTime;
		}
		
		@Override
		public GlobalVariable setDescription(String description) {
			this.description = description;
			modify();
			return this;
		}

		@Override
		public GlobalVariable setName(String name) {
			this.name = name;
			modify();
			return this;
		}

		@Override
		public GlobalVariable setValue(String value) {
			this.value = value;
			modify();
			return this;
		}

		@Override
		public GlobalVariable setDeploymentSettable(boolean deploymentSettable) {
			this.deploymentSettable = deploymentSettable;
			modify();
			return this;
		}

		@Override
		public GlobalVariable setServiceSettable(boolean serviceSettable) {
			this.serviceSettable = serviceSettable;
			modify();
			return this;
		}

		@Override
		public GlobalVariable setType(String type) {
			this.type = type;
			modify();
			return this;
		}

		@Override
		public GlobalVariable setModTime(long modTime) {
			this.modTime = modTime;
			modify();
			return this;
		}
		@Override
		public String toString() {
			return name + "=" + value + " (" + sourceItem.getItemSource().getName()  /*+ " " + getPath() */+ ")";
		}
		
		@Override
		public GlobalVariables getParent() {
			return GlobalVariablesImpl.this;
		}
		@Override
		public String getPath() {
			String path = getParent().getPath();
			return (path.length() == 0 ? "" : path + "/") + getName();
		}
		@Override
		public boolean isInternalVariable() {
			return INTERNALL_VARIABLES.contains(getPath());
		}
	}

	private static final List<String> INTERNALL_VARIABLES = Lists.asList("Deployment", "Domain");
	

}
