package com.meluzin.tibcobwutils.deploymentrepository.structure;

public enum ItemType {
	Archive(".archive"),
	SharedConnectionHTTP(".sharedhttp"),
	SharedConnectionJDBC(".sharedjdbc"),
	Process(".process"),
	GlobalVariable(".substvar"),
	Folder(".folder"),
	RVTransport(".rvtransport"),
	ServiceAgent(".serviceagent"),
	WSDL(".wsdl"),
	AESchema(".aeschema"),
	XSD(".xsd"),
	Unknown("*");
	
	private String extension;
	private ItemType(String extension) {
		this.extension = extension;
	}
	public String getExtension() {
		return extension;
	}

	public static ItemType fromItem(Item item) {
		String name = item.getName();
		if (item.isFolder()) return Folder;
		for (ItemType type: values()) {
			if (type != Unknown && name.endsWith(type.getExtension())) return type; 
		}
		return Unknown;
	}
	public static ItemType fromName(String name, boolean isFolder) {
		if (isFolder) return Folder;
		for (ItemType type: values()) {
			if (type != Unknown && name.endsWith(type.getExtension())) return type; 
		}
		return Unknown;
	}
}
