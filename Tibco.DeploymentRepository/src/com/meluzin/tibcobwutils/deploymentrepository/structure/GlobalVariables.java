package com.meluzin.tibcobwutils.deploymentrepository.structure;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GlobalVariables {
	public List<GlobalVariable> getLocalVariables();
	public Map<String, List<GlobalVariable>> getAllVariables();
	public GlobalVariable addVariable(String name);
	public GlobalVariables addVariables(String name);
	public GlobalVariables removeVariable(String name);
	public List<GlobalVariables> getChildVariables(); 
	public boolean isChanged();
	public void save();	
	public String getPath();
	public String getName();
	public Optional<GlobalVariable> resolve(String varRelativePath);
	public Optional<GlobalVariable> resolve(Path varRelativePath);
	public GlobalVariable resolveOrCreate(String varRelativePath);
	public GlobalVariable resolveOrCreate(Path varRelativePath);
	public Optional<GlobalVariables> resolveVars(String varRelativePath);
	public Optional<GlobalVariables> resolveVars(Path varRelativePath);
	public String resolveExpression(String expression);
	public GlobalVariable makeVariableLocal(GlobalVariable variable);
}
