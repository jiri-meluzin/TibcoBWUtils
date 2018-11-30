package com.meluzin.tibcobwutils.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.meluzin.functional.Lists;
import com.meluzin.stream.StreamUtils;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class RunParallel {
	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("RunParallel", true, "-")
				.description("Run commands from standard input in parallel.");
		argParser.addArgument("-utilization").type(Double.class).required(false).help("Max utilization in fraction: 0.5 = 50% = half of available processors are used");
		argParser.addArgument("-threads").type(Integer.class).required(false).help("Max number of threads");
		Namespace res = argParser.parseArgsOrFail(args);
		Double utilization = res.getDouble("utilization");
		Integer threads = res.getInt("threads");

		String commandsInput = StreamUtils.convertStreamToString(System.in);
		String[] commands = commandsInput.split("\r\n|\n");
		commandsList = Arrays.asList(commands);
		List<ActionResult> result = Lists.asList();
		try {
			result = executeCommands(commandsList, utilization, threads);
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			log("Execution failed: " + e.getMessage());
			System.exit(-2);
		}
		for (ActionResult ar : result) {
			log("Action: " + ar.getAction());
			log("Took: " + ar.getDuration().toMillis() / 1000.0 + "[s]");
			log("Result: " + ar.getReturnCode());
			if (ar.getStdOutput() != null)
				log("Output: " + ar.getStdOutput());
			if (ar.getStdError() != null)
				log("Error: " + ar.getStdError());
			if (ar.getException() != null) {
				log("Exception: ");
				ar.getException().printStackTrace();
			}
		}
		System.exit(result.stream().filter(ar -> !new Integer(0).equals(ar.getReturnCode())).count() > 0 ? -1 : 0);
	}

	private static List<ActionResult> executeCommands(List<String> commandsList, Double utilization, Integer threads)
			throws InterruptedException, ExecutionException {
		ForkJoinPool f = new ForkJoinPool( threads != null ? threads : (int) (Runtime.getRuntime().availableProcessors() * (utilization == null ? 0.5 : utilization)));
		return f.submit(() -> commandsList.stream().filter(s -> s != null && s.length() > 0).parallel().map(c -> run(c))
				.collect(Collectors.toList())).get();
	}

	private static Set<Long> runningCommands = new HashSet<>();
	private static Set<Long> finishedCommands = new HashSet<>();
	private static List<String> commandsList = Lists.asList();
	private static boolean shuttingDown = false;
	static {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutdown hook ran!");
				shuttingDown = true;
				new ArrayList<>(runningCommands).forEach(pid -> {
					try {
						System.out.println("Killing pid=" + pid);
						Runtime.getRuntime().exec("kill -9 " + pid);
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
				try {
					System.out.println("Waiting for finish!");
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("Shutdown done!");
			}
		});

	}

	public static long getPidOfProcess(Process p) {
		long pid = -1;
		try {
			if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
				Field f = p.getClass().getDeclaredField("pid");
				f.setAccessible(true);
				pid = f.getLong(p);
				f.setAccessible(false);
			}
		} catch (Exception e) {
			pid = -1;
		}
		return pid;
	}
	public synchronized static void addPID(long pid) {
		if (pid != -1) {
			runningCommands.add(pid);
		}
	}
	public synchronized static void finishPID(long pid) {
		if (pid != -1) {
			runningCommands.remove(pid);
			finishedCommands.add(pid);
		}
	}
	public static ActionResult run(String args) {
		Instant start = Instant.now();
		try {
			if (shuttingDown) {
				throw new InterruptedException("Shutdown of RunParallel has occured");
			}
			log("starting action: " + args);
			Process p = Runtime.getRuntime().exec(args);
			long pid = getPidOfProcess(p);
			addPID(pid);
			InputStream error = p.getErrorStream();
			InputStream input = p.getInputStream();
			String stdOutput = "";
			String stdError = "";
			try (InputStreamReader errorReader = new InputStreamReader(error);
			BufferedReader errorBufferedReader = new BufferedReader(errorReader);
			InputStreamReader inputReader = new InputStreamReader(input);
			BufferedReader inputBufferedReader = new BufferedReader(inputReader)) {

				log("reading errOutput: ");
				stdError = StreamUtils.convertStreamToString(errorBufferedReader);
				log("reading stdOutput: ");
				stdOutput = StreamUtils.convertStreamToString(inputBufferedReader);
				log("done");
			}
			while (!p.waitFor(300, TimeUnit.MILLISECONDS)) {}
			int returnCode = p.exitValue();
			finishPID(pid);
			log("action finished: " + args);
			log("finished total: " + finishedCommands.size() + "/" + commandsList.size());
			return new ActionResult(args, stdOutput, stdError, null, returnCode,
					Duration.between(start, Instant.now()));
		} catch (IOException | InterruptedException e) {
			log("action failed: " + args);
			return new ActionResult(args, null, null, e, null, Duration.between(start, Instant.now()));
		}
	}

	public static void log(String arg) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Calendar cal = Calendar.getInstance();
		System.out.println(dateFormat.format(cal.getTime()) + " " + arg);
	}

	public static class ActionResult {
		private String action;
		private String stdOutput;
		private String stdError;
		private Throwable exception;
		private Integer returnCode;
		private Duration duration;

		public ActionResult(String action, String stdOutput, String stdError, Throwable exception, Integer returnCode,
				Duration duration) {
			super();
			this.action = action;
			this.stdOutput = stdOutput;
			this.stdError = stdError;
			this.exception = exception;
			this.returnCode = returnCode;
			this.duration = duration;
		}

		public String getAction() {
			return action;
		}

		public Throwable getException() {
			return exception;
		}

		public Integer getReturnCode() {
			return returnCode;
		}

		public String getStdError() {
			return stdError;
		}

		public String getStdOutput() {
			return stdOutput;
		}

		public Duration getDuration() {
			return duration;
		}
	}
}
