package com.meluzin.tibcobwutils.earcomparer.fullconfig.model;

import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemType;

public class Archive extends Value<String>{
	private Item archive;
	public Archive(Item archive) {
		super(archive.getName().replace(ItemType.Archive.getExtension(), ""));
		this.archive = archive;
	}

	public Item getArchive() {
		return archive;
	}	
	
}
