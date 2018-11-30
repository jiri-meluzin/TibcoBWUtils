package com.meluzin.tibcobwutils.earcomparer;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V2;
import com.meluzin.functional.T.V3;
import com.meluzin.tibcobwutils.earcomparer.load.LoadZipFile;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class EARVersionExtractor {
	
	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("EAR Version Extractor", true, "-")
				.description("Extracts version from ear files in given directory.");
		argParser.addArgument("-directory").type(String.class).required(true).help("Path to directory with ears");
		argParser.addArgument("-out").choices("LIST", "FILES").required(true).help("Output type - LIST or FILES");
		
		Namespace res = argParser.parseArgsOrFail(args);
		String directory = res.get("directory");
		String out = res.getString("out");
		List<T.V2<Path, String>> output = new FileSearcher().searchFiles(Paths.get(directory), "glob:**/*.ear", false).
			stream().parallel().
			map(p -> getVersionFromEAR(p)).
			collect(Collectors.toList());
		if ("LIST".equals(out)) {
			output.forEach(v -> System.out.println(v.getA().getFileName() + "\t" + v.getB()));
		}
		else {
			output.forEach(v -> {
				Path versionPath = Paths.get(v.getA().toString().replace(".ear", ".version"));
				try(FileWriter ww = new FileWriter(versionPath.toFile()); BufferedWriter w = new BufferedWriter(ww)) {
				  w.write(v.getB());
				} catch(IOException e) {
					throw new RuntimeException("Could not write version to file: " + v.getA(), e);
				}
			});
		}
	}

	public static V2<Path, String> getVersionFromEAR(Path p) {
		Optional<V3<String, byte[], ZipEntry>> v = getVersion(T.V(p, new LoadZipFile().load(p)));
		NodeBuilder node = new XmlBuilderFactory().parseDocument(new ByteArrayInputStream(v.get().getB()));
		String version = node.searchFirst(true, n -> "version".equals(n.getName())).getTextContent();
		return T.V(p, version);
	}

	private static Optional<T.V3<String, byte[], ZipEntry>> getVersion(V2<Path, List<V3<String, byte[], ZipEntry>>> v) {
		return v.getB().stream().filter(vv -> "!/TIBCO.xml".equals(vv.getA())).findFirst();
	}

}
