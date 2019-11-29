package com.meluzin.tibcobwutils.deploymentrepository.structure;

public interface GlobalVariable {
	public GlobalVariables getParent();
	
	public String getName();
	public String getValue();
	public boolean isDeploymentSettable();
	public boolean isServiceSettable();
	public String getType();
	public long getModTime();
	public String getPath();
	public String getDescription();
	/**
	 * Ex. Deployment or Domain , they are not settable.
	 * @return check whether variable is settable
	 */
	public boolean isInternalVariable();
	
	public GlobalVariable setName(String name);
	public GlobalVariable setValue(String value);
	public GlobalVariable setDeploymentSettable(boolean deploymentSettable);
	public GlobalVariable setServiceSettable(boolean serviceSettable);
	public GlobalVariable setType(String type);
	public GlobalVariable setModTime(long modTime);
	public GlobalVariable setDescription(String description);
	
	public Item getSourceItem();
	
}
