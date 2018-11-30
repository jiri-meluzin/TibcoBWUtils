package com.meluzin.tibcobwutils.earcomparer.expressionresolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.functional.T;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariable;
import com.meluzin.tibcobwutils.deploymentrepository.structure.GlobalVariables;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemType;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.ConfigImpl;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.PasswordDecrypter;
import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.RepositoryImpl;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.FullConfigsModel;
import com.meluzin.tibcobwutils.earcomparer.fullconfig.model.SDKPropertiesLoader;

public class JDBCUrlConnectionUpdater {
	public static void main(String[] args) {
		RepositoryImpl repositoryImpl = new RepositoryImpl(Paths.get("t:/source/R181123_klement/SBLCRM_WS_IO"), new ConfigImpl(Paths.get("C:/Users/Jirka/.TIBCO/Designer5.prefs.R181123_klement")));
		FullConfigsModel fullConfigsModel = new FullConfigsModel(repositoryImpl, new SDKPropertiesLoader(Paths.get("T:/tib/app/tibco")));
		
	
		repositoryImpl.findAll(i -> i.getItemType() == ItemType.SharedConnectionJDBC).forEach(i -> {
			NodeBuilder jdbcConnectionLocationElement = i.loadAsXml().searchFirstByName(true, "location");
			String textContent = jdbcConnectionLocationElement.getTextContent();
			System.out.println(i.getPath() + " " + textContent);			
			Set<GlobalVariable> oldVariables = new HashSet<>();
			T.V1<GlobalVariable> jdbcVar = T.V(null);
			fullConfigsModel.getAvailableArchives().forEach(archive -> fullConfigsModel.getAvailableEnvironemnts().stream().filter(env -> fullConfigsModel.isConfigAvailable(env, archive)).forEach(env -> {
				T.V1<GlobalVariable> var = T.V(null);
				String resolveExpression = resolveExpression(textContent, relativePath -> repositoryImpl.getRootGlobalVariables().resolve(relativePath), variable -> {
					Optional<NodeBuilder> globalVariableElement = fullConfigsModel.getGlobalVariableElement(env, archive, variable);
					var.setA(variable);
					oldVariables.add(variable);
					fullConfigsModel.removeVariableFromConfig(env, archive, variable.getPath().toString());
					return globalVariableElement.get().searchFirstByName("value").getTextContent();
				});
				
				System.out.println(archive + "\t" + env + "\t" + resolveExpression);
				GlobalVariables parent = var.getA().getParent();
				Optional<GlobalVariable> resolve = parent.resolve("jdbcURL");
				
				GlobalVariable addVariable = resolve.isPresent() ? resolve.get() : parent.addVariable("jdbcURL");
				jdbcVar.setA(addVariable);
				NodeBuilder createGlobalVariableElement = fullConfigsModel.createGlobalVariableElement(env, archive, addVariable);
				NodeBuilder valueElement = createGlobalVariableElement.searchFirstByName("value");
				valueElement.setTextContent(resolveExpression);
				addVariable.setValue(repositoryImpl.getRootGlobalVariables().resolveExpression(textContent));				
				
			}));
			jdbcConnectionLocationElement.setTextContent("%%"+jdbcVar.getA().getPath()+"%%");
			oldVariables.forEach(v -> v.getParent().removeVariable(v.getName()));
			
			i.updateContent(jdbcConnectionLocationElement.getRoot());
			
		});
		Set<String> deploymentSettableVariablesNotDefinedInDeploymentAllConfigs = fullConfigsModel.getDeploymentSettableVariablesNotDefinedInDeploymentAllConfigs(repositoryImpl.getRootGlobalVariables());
		fullConfigsModel.removeVariablesFromConfig(deploymentSettableVariablesNotDefinedInDeploymentAllConfigs);
		repositoryImpl.save();
		fullConfigsModel.save();
		
	}

	public static String resolveExpression(String expression, GlobalVariableResolver resolver, GlobalVariableValueResolver valueResolver) {
		Pattern p = Pattern.compile("%%([^%]*)%%");
		Matcher m = p.matcher(expression);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String group = m.group(1);
			Path varRelativePath = Paths.get(group);
			Optional<GlobalVariable> resolve =  resolver.resolve(varRelativePath);
			if (!resolve.isPresent()) throw new RuntimeException("Could not resolver variable: " + varRelativePath);
			String resolvedValue = valueResolver.resolve(resolve.get());
			if ("Password".equals(resolve.get().getType())) {
				resolvedValue = new PasswordDecrypter().decrypt(resolvedValue);
			}
			m.appendReplacement(sb, resolvedValue.replace("{", "\\{").replace("}", "\\}").replace("$", "\\$"));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	public interface GlobalVariableResolver {
		public Optional<GlobalVariable> resolve(Path relativePath) ;
	}
	public interface GlobalVariableValueResolver {
		public String resolve(GlobalVariable variable) ;
	}
	
}
