package com.meluzin.tibcobwutils.build.deploymentbuild;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.meluzin.functional.Log;
import com.meluzin.functional.T;
import com.meluzin.stream.StreamUtils;

public final class BinExecutor {
	private static Logger log = Log.get();
	private int returnCode;
	private String stdOutput;
	private String stdError;
	private String exec;
	private Date started;
	private Date finished;
	private BinExecutor(String exec, int returnCode, String stdOutput, String stdError, Date started, Date finished) {
		this.returnCode = returnCode;
		this.stdOutput = stdOutput;
		this.stdError = stdError;
		this.exec = exec;
		this.started = started;
		this.finished = finished;
	}
	public static BinExecutor exec(String exec) {
		return exec(exec, Optional.empty());
	}
	public static void main(String[] args) {
		System.out.println(BinExecutor.exec("cmd", Optional.of(Duration.ofMillis(1000))));
	}
	public static BinExecutor exec(String exec, Optional<Duration> maxDuration) {
		return exec(exec, maxDuration, null);
	}
	public static BinExecutor exec(String exec, Optional<Duration> maxDuration, File directory) {
		log.info(exec);
		if (maxDuration.isPresent()) log.info("maxDuration: " + maxDuration);
		Process p;
		Date started = new Date();
		Instant startedAt = Instant.now();
		T.V1<Boolean> finished = T.V(false);
		T.V2<String, Boolean> stdOutputHolder = T.V(null, false);
		T.V2<String, Boolean> stdErrorHolder = T.V(null, false);
		try {
			ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(2);
			p = Runtime.getRuntime().exec(exec, null, directory);
			Optional<ScheduledExecutorService> schedule = Optional.empty();
			if (maxDuration.isPresent()) {
				schedule = Optional.of(Executors.newScheduledThreadPool(1));
				schedule.get().schedule(() -> {
					if (!finished.getA()) {
						log.info("Destroying process after timeout of " + maxDuration + " ("+ exec + ")");
						p.destroy();
					}
				}, maxDuration.get().toMillis(), TimeUnit.MILLISECONDS);
			}
			InputStream input = p.getInputStream();
			InputStream error = p.getErrorStream();
			newFixedThreadPool.submit(getOutputReader(stdOutputHolder, input));
			newFixedThreadPool.submit(getOutputReader(stdErrorHolder, error));			
			int destroyCount = 0;
			
			boolean killNow = false;
			while (!p.waitFor(300, TimeUnit.MILLISECONDS)) {
				if (maxDuration.isPresent()) {
					Duration duration = Duration.between(startedAt, Instant.now());
					//log.info(startedAt + " " + duration + " " + duration.compareTo(maxDuration.get()));
					if (duration.compareTo(maxDuration.get()) > 0) {
						log.warning("Process took too long - " + duration);
						//Thread.currentThread().interrupt();
						killNow = true;
					}
				}
				if (Thread.interrupted() || killNow) {
					p.destroy();
					destroyCount ++;
					if (destroyCount > 5) {
						p.destroyForcibly().waitFor(300, TimeUnit.MILLISECONDS);
						if (p.isAlive()) {
							log.warning("Process is still alive, even after destroyForcibly " + exec);
							throw new InterruptedException("Thread has been interrupted");
						}
					}
				}
			}
			
			int returnCode = p.exitValue();
			finished.setA(true);
			if (schedule.isPresent()) schedule.get().shutdownNow();
			newFixedThreadPool.shutdown();
			newFixedThreadPool.awaitTermination(1000, TimeUnit.MILLISECONDS);
			
			return new BinExecutor(exec, returnCode, stdOutputHolder.getA(), stdErrorHolder.getA(), started, new Date());
		} catch (IOException | InterruptedException e) {
			//throw new RuntimeException("Cannot execute " + exec, e);
			if (e instanceof InterruptedException) {
				return new BinExecutor(exec, 255, null, "Thread has been interrupted", started, new Date());					
			}
			else {
				log.log(Level.INFO, "Unkown error when running "  + exec,  e);
				return new BinExecutor(exec, 254, null, "Unknown error: " + e.getMessage(), started, new Date());
			}
		}
		
	}
	public static Runnable getOutputReader(T.V2<String, Boolean> stdOutputHolder, InputStream input) {
		return () -> {
			try ( InputStreamReader inputReader = new InputStreamReader(input);
				  BufferedReader inputBufferedReader = new BufferedReader(inputReader)) {
				stdOutputHolder.setA(StreamUtils.convertStreamToString(inputBufferedReader));
				stdOutputHolder.setB(true);
			} catch (IOException e) {
				log.severe("Could not finish reading output: " + ExceptionUtils.getStackTrace(e) );
			}
		};
	}
	
	public int getReturnCode() {
		return returnCode;
	}
	public String getStdError() {
		return stdError;
	}
	public String getStdOutput() {
		return stdOutput;
	}
	
	public String getExec() {
		return exec;
	}
	
	@Override
	public String toString() {
		return exec + "\n" + returnCode + "\n" + stdOutput + "\n" + stdError;
	} 
	
	public Date getFinished() {
		return finished;
	}
	public Date getStarted() {
		return started;
	}
}