package com.meluzin.tibcobwutils.earcomparer;

import java.nio.file.Paths;
import java.util.List;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class CompareEARStarter {
	public static void main(String[] args) {
		try {
			ArgumentParser argParser = ArgumentParsers.newArgumentParser("EAR Comparer", true, "-")
					.description("Compares two EAR files . Returns 0 when ear are equal, 1 when ear are different, on error 2");
			argParser.addArgument("-old").type(String.class).required(true).help("Path to old ear. Ex: T:/Source/R160729/AP_API_IO.ear");
			argParser.addArgument("-new").type(String.class).required(true).help("Path to new ear. Ex: T:/Source/R160924/AP_API_IO.ear");
			argParser.addArgument("-verbose").type(String.class).required(false).help("Quiet output");
			argParser.addArgument("-compareAdapterSDKProperties").type(Boolean.class).required(false).setDefault(false).help("Compare SDK Properties in TIBCO.xml, default is false");
			
			Namespace res = argParser.parseArgsOrFail(args);
			String oldPath = res.get("old");
			String newPath = res.get("new");
			String verbose = res.get("verbose");
			boolean compSDK = res.getBoolean("compareAdapterSDKProperties");
			EARComparer earComparer = new EARComparer();
			earComparer.setRemoveAdapterSDKPropertiesFromTIBCOXML(!compSDK);
			List<CompareResult> result = earComparer.compare(Paths.get(oldPath), Paths.get(newPath));
	
			result.forEach(r -> System.out.println(r));
			if (result.size() == 0) {
				if (!"quiet".equals(verbose)) { 
					System.out.println("No difference between: " + oldPath + " and " + newPath);
				}
				System.exit(0);
			}
			else {
				System.exit(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
	}
}
