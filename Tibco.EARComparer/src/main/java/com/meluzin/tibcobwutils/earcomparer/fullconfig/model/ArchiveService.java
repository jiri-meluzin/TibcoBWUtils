package com.meluzin.tibcobwutils.earcomparer.fullconfig.model;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;

public class ArchiveService extends Value<String>{
	private Archive archive;
	private ArchiveServiceType archiveServiceType;
	public ArchiveService(String name, Archive archive) {
		super(name);
		this.archive = archive;
		this.archiveServiceType = ArchiveServiceType.fromElementName(loadArchiveElement(archive).getName());
	}

	private NodeBuilder loadArchiveElement(Archive archive) {
		return archive.getArchive().loadAsXml().search(true, n -> getValue().equals(n.getAttribute("name"))).findFirst().get();
	}
	
	public ArchiveServiceType getArchiveServiceType() {
		return archiveServiceType;
	}
	
	
	
	
	public Archive getArchive() {
		return archive;
	}
	public static enum ArchiveServiceType {
		Adapter("adapterArchive"),
		BW("processArchive");
		
		private String elementName;
		public static ArchiveServiceType fromElementName(String name) {
			ArchiveServiceType[] values = values();
			for (int i = 0; i < values.length; i++) {
				if (values[i].getElementName().equals(name)) return values[i];
			}
			throw new IllegalArgumentException("Unknown service type element name: " + name);
		}
		ArchiveServiceType(String elementName) {
			this.elementName = elementName;			
		}
		
		public String getElementName() {
			return elementName;
		}
	}
}
