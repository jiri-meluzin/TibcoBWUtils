package com.meluzin.tibcobwutils.earcomparer.fullconfig.model;

public class Service extends Value<String>{
	private ServiceType serviceType;
	private String archiveAdapterName;
	public Service(String name, ServiceType serviceType) {
		super(name.replace(serviceType.getArchiveExtension(), ""));
		this.serviceType = serviceType;
		this.archiveAdapterName = getValue();
	}

	public String getArchiveAdapterName() {
		return archiveAdapterName;
	}
	public ServiceType getServiceType() {
		return serviceType;
	}	
	public static enum ServiceType {
		Adapter("adapter", ".aar"),
		Service("service", ""),
		BW("bw", ".par");

		private String elementName;
		private String archiveExtension;
		public static ServiceType fromElementName(String name) {
			ServiceType[] values = values();
			for (int i = 0; i < values.length; i++) {
				if (values[i].getElementName().equals(name)) return values[i];
			}
			throw new IllegalArgumentException("Unknown service type element name: " + name);
		}
		ServiceType(String elementName, String archiveExtension) {
			this.elementName = elementName;
			this.archiveExtension = archiveExtension;
		}
		
		public String getArchiveExtension() {
			return archiveExtension;
		}
		
		public String getElementName() {
			return elementName;
		}
	}
}
