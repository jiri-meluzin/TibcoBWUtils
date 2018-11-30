package com.meluzin.tibcobwutils.build;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.meluzin.functional.Lists;
import com.meluzin.functional.Log;
import com.meluzin.functional.T;
import com.meluzin.tibcobwutils.build.deploymentbuild.BinExecutor;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class BuildLibraryParallel {
    Set<String> finished = new HashSet<String>();
    Set<String> failed = new HashSet<String>();
    Set<String> toProcess = new HashSet<String>();
    Set<String> processing = new HashSet<String>();
    HashMap<String, T.V4<String, String, String, String>> compute;
    private Logger logger = Log.get();

	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("BuildLibraryParallel", true, "-")
				.description("Build libraries in parallel.");
		argParser.addArgument("-librariespath").type(String.class).required(true).help("Path to list of libraries to build: ex: /tib/app/jenkins2/home/workspace/R161121-test/projlib/build.txt");
		argParser.addArgument("-parallelism").type(Integer.class).required(false).help("Number of threads");
		Namespace res = argParser.parseArgsOrFail(args);
		String librariespath = res.getString("librariespath");
		Integer parallelism = res.getInt("parallelism");
		new BuildLibraryParallel().exec(librariespath, parallelism == null ? 3 : parallelism);
	}
	public void exec(String path, int parallelism) {
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
		    String line;
		    List<String> lines = new ArrayList<String>();
		    while ((line = br.readLine()) != null) {
		    	lines.add(line);
		    }
		    compute = lines.stream().
			    	map(l ->  l.split(";")).
			    	map(parts -> T.V(parts[0], parts[1], parts[2], parts[3])).
			    	reduce(new HashMap<String, T.V4<String, String, String, String>>(), (a,b) -> {a.put(b.getA().substring(1), b); return a;}, (a,b) -> {a.putAll(b); return a;});
		    Map<String, Optional<List<String>>> tree = lines.stream().
			    	map(l ->  l.split(";")).
			    	map(parts -> T.V(parts[0].substring(1), "[]".equals(parts[4]) ? new ArrayList<String>() : Lists.asList(parts[4].substring(1, parts[4].length() - 1).split(", ")))).
			    	collect(Collectors.groupingBy(v -> v.getA(), Collectors.mapping((T.V2<String, List<String>> v) -> v.getB(), Collectors.reducing((a,b) -> {
			    		Set<String> s = new HashSet<String>(a);
			    		s.addAll(b);
			    		return new ArrayList<>(s);
			    	}))));
		    toProcess = new HashSet<String>(tree.keySet());
		    ExecutorService service = Executors.newFixedThreadPool(parallelism); 
		    while (toProcess.size() > 0) {
		    	List<String> toDo = toProcess.stream().filter(s -> hasFinishedDependencies(s, tree, finished)).collect(Collectors.toList());
		    	if (toDo.size() > 0) {
		    		toDo.forEach(lib -> {
		    			synchronized (toProcess) {
		    				toProcess.remove(lib);
		    				processing.add(lib);				    			
						}
		    			service.submit(() -> {			    			
		    				logger.info("starting " + lib);
			    			try {
								//Thread.sleep(1000);
			    				String exec = (String.format("buildlibrary -lib %s -p %s -o %s -x -s -a %s", compute.get(lib).getA(), compute.get(lib).getB(), compute.get(lib).getC(), compute.get(lib).getD()));
			    				BinExecutor be  = BinExecutor.exec(exec);
			    				
			    				int returnCode = be.getReturnCode();
			    				synchronized (toProcess) {
			    					if (returnCode != 0) failed.add(lib);
			    					logger.info(String.format("Building library (%s) finished with return code %s", lib, returnCode));
				    				if (be.getStdOutput() != null && be.getStdOutput().length() > 0) logger.info(be.getStdOutput());
				    				if (be.getStdError() != null && be.getStdError().length() > 0) logger.info(be.getStdError());									
								}			    				
							} catch (Exception e) {
			    				synchronized (toProcess) {
			    					failed.add(lib);
			    					logger.info(String.format("Building library (%s) failed", lib));
			    					e.printStackTrace();
								}
							}
			    			synchronized (toProcess) {
			    				finished.add(lib);
				    			processing.remove(lib);
				    		}
			    		
			    			//log("finished " + lib);			    			
			    		});
		    		});
		    		//log("status: toProcess="+toProcess.size() +" processing="+processing.size()+" finished" + finished.size());
		    	}
		    	else {
		    		try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		    	}
		    	
		    }
		    while (processing.size() > 0) {
		    	//log("remaing " + processing.size());
		    	try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
		    logger.info("finished");
		    if (failed.size() > 0) {
		    	logger.info("Failed libraries: " + failed);
		    }
		    System.exit(0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	
	public static boolean hasFinishedDependencies(String lib, Map<String, Optional<List<String>>> tree , Set<String> finished) {
		Optional<List<String>> dependencies = tree.get(lib);
		if (dependencies == null || !dependencies.isPresent() || dependencies.get().size() == 0) return true;
		for (String dependency : dependencies.get()) {
			if (!finished.contains(dependency)) return false;
		}
		return true;
	}
}
