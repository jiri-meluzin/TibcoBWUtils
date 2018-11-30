package com.meluzin.tibcobwutils.earcomparer;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;

import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V2;
import com.meluzin.functional.T.V3;
import com.meluzin.tibcobwutils.earcomparer.load.LoadZipFile;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class EARVersionUpdater {
	
	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("EAR Version Updater", true, "-")
				.description("Updates version in ear files in given directory.");
		argParser.addArgument("-directory").type(String.class).required(true).help("Path to directory with ears");
		argParser.addArgument("-version").type(String.class).required(true).help("Version that will be written in Tibco.xml");
		
		Namespace res = argParser.parseArgsOrFail(args);
		String directory = res.get("directory");
		String version = res.getString("version");
		new FileSearcher().searchFiles(Paths.get(directory), "glob:**/*.ear", false).
			stream().parallel().
			map(p -> T.V(p, new LoadZipFile().load(p))).
			map(v -> T.V(v.getA(), getVersion(v), v.getB())).
			filter(v -> v.getB().isPresent()).
			map(v -> T.V(v.getA(), new XmlBuilderFactory().parseDocument(new ByteArrayInputStream(v.getB().get().getB())), v.getB(), v.getC())).
			forEach(v -> {
				v.getB().search("version").forEach(vnode -> {
					System.out.println(v.getA() + " " + vnode.getTextContent() + " -> " + version);
					vnode.setTextContent(version);	
				});
				byte[] bytes = v.getB().toString().getBytes();
				V3<String, byte[], ZipEntry> zipFileEntry = v.getC().get();
				zipFileEntry.setB(bytes);
				new LoadZipFile().updateFile(v.getA(), v.getD());
			});
		
	}

	private static Optional<T.V3<String, byte[], ZipEntry>> getVersion(V2<Path, List<V3<String, byte[], ZipEntry>>> v) {
		return v.getB().stream().filter(vv -> "!/TIBCO.xml".equals(vv.getA())).findFirst();
	}

}
