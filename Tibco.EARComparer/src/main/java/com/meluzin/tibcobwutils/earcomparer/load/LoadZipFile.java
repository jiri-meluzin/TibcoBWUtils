package com.meluzin.tibcobwutils.earcomparer.load;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import com.meluzin.functional.FileSearcher;
import com.meluzin.functional.T;
import com.meluzin.functional.T.V2;
import com.meluzin.functional.T.V3;

public class LoadZipFile {

	public List<T.V3<String, byte[], ZipEntry>> load(Path path) {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            List<T.V3<String, byte[], ZipEntry>> entries = Collections.list(zipFile.entries()).stream().map(z -> (ZipEntry)z).map(e -> T.V("" + /*path.getFileName() +*/ "!/" + e.getName(), retrieveFileData(path, zipFile, e), e)).sorted(fileListComparator()).collect(Collectors.toList());
            return entries;
        } catch (ZipException zip) {
        	try (InputStream is = new FileInputStream(path.toString())) {
        		ArrayList<T.V3<String, byte[], ZipEntry>> list = new ArrayList<>();
        		list.add(T.V("SingleFile", IOUtils.toByteArray(is), new ZipEntry("SingleFile")));
        		return list;
        	} catch (FileNotFoundException e) {
        		throw new RuntimeException("File not found ("+path+")", e);
			} catch (IOException e) {
        		throw new RuntimeException("File read failed ("+path+")", e);
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot read file ("+path+")", e);
		}        

	}
	public List<T.V3<String, byte[], ZipEntry>> load(String path, byte[] array) {
		return load(path, new ByteArrayInputStream(array));
	}
	public List<T.V3<String, byte[], ZipEntry>> load(String path, InputStream dataFileStream) {
        try (ZipInputStream zipFile = new ZipInputStream(dataFileStream)) {
            List<T.V3<String, byte[], ZipEntry>> entries = new ArrayList<>();// Collections.list(zipFile.entries()).stream().map(z -> (ZipEntry)z).map(e -> T.V(e.getName(), retrieveFileData(path, zipFile, e))).collect(Collectors.toList());
            ZipEntry entry;
            while((entry = zipFile.getNextEntry())!=null)
            {
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {                	
                    int len = 0;
                    byte[] buffer = new byte[4096];
                    while ((len = zipFile.read(buffer)) > 0)
                    {
                        output.write(buffer, 0, len);
                    }
                    entries.add(T.V(path + "!/" + entry.getName(), output.toByteArray(), entry));
                }
            }
            entries.sort(LoadZipFile.fileListComparator());
            return entries;
		} catch (IOException e) {
			throw new RuntimeException("Cannot read library file ("+path+")", e);
		}        
		
	}
	public void createZipArchiveFromFolder(Path outputPath, Path inputFolder) {
		updateFile(outputPath, new FileSearcher().searchFiles(inputFolder, "glob:**/*", true).stream().filter(p -> p.toFile().isFile()).map(p -> createZipEntry(inputFolder, p)));
	}
	protected V3<String, byte[], ZipEntry> createZipEntry(Path inputFolder, Path p) {
		try {
			return T.V(inputFolder.relativize(p).toString().replace("\\", "/"),Files.readAllBytes(p), new ZipEntry(inputFolder.relativize(p).toString().replace("\\", "/")));
		} catch (IOException e) {
			throw new RuntimeException("Could not read file "+p, e);
		}
	}
	public void updateFile(Path outputPath, List<T.V3<String, byte[], ZipEntry>> files) {
		updateFile(outputPath, files.stream());
	}
	public void updateFile(Path outputPath, Stream<T.V3<String, byte[], ZipEntry>> files) {
		try (ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(outputPath.toFile()))) {
			files.forEach(v -> {
				try {
					ZipEntry zipEntry = (ZipEntry)v.getC().clone();
					zipEntry.setCompressedSize(-1);
					zipEntry.setSize(v.getB().length);
					zipFile.putNextEntry(zipEntry);
					zipFile.write(v.getB());
				} catch (IOException e) {
					throw new RuntimeException("Cannot store zip entry file ("+v.getA()+")" + e.getMessage(), e);
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Cannot store zip file ("+outputPath+")" + e.getMessage(), e);
		}
	}
	public static Comparator<? super V2<String, byte[]>> fileListComparator() {
		return (a,b) -> a.getA().compareTo(b.getA());
	}
	private byte[] retrieveFileData(Path p, ZipFile zipFile, ZipEntry e) {
		try {
			return IOUtils.toByteArray( zipFile.getInputStream(e));
		} catch (IOException e1) {
			throw new RuntimeException("Cannot read entry " + e + " from zip file " + p, e1);
		}
	}
}
