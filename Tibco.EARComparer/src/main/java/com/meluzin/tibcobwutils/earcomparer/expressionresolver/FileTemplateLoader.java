package com.meluzin.tibcobwutils.earcomparer.expressionresolver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.io.FileUtils;

import freemarker.cache.TemplateLoader;

public class FileTemplateLoader implements TemplateLoader {
	@Override
	public Reader getReader(Object templateSource, String encoding) throws IOException {
		File file = new File((String)templateSource);
		byte[] fileData = FileUtils.readFileToByteArray(file);
		return  new InputStreamReader(new ByteArrayInputStream(fileData));
	}

	@Override
	public long getLastModified(Object templateSource) {
		File file = new File((String) templateSource);
		return file.exists() ? file.lastModified() : -1;
	}

	@Override
	public Object findTemplateSource(String name) throws IOException {
		return name;
	}

	@Override
	public void closeTemplateSource(Object templateSource) throws IOException {

	}
}