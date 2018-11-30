package com.meluzin.tibcobwutils.build.deploymentbuild.tasks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.meluzin.functional.Log;

public class TaskRunner {

	private static Logger log = Log.get();
	private OnErrorAction onErrorAction = (a,b) -> {
		log.severe("Action " + a + " finished with exception: " +b.getClass().getName() + "  " +b.getMessage());
	};
	private OnFinishAction onFinishAction = (a) -> {
		log.info("Tasks "+a+" finished");		
	};
	public OnErrorAction getOnErrorAction() {
		return onErrorAction;
	}
	public OnFinishAction getOnFinishAction() {
		return onFinishAction;
	}
	public TaskRunner setOnErrorAction(OnErrorAction onErrorAction) {
		if (onErrorAction == null) throw new IllegalArgumentException("onErrorAction must not be a null");
		this.onErrorAction = onErrorAction;
		return this;
	}
	public TaskRunner setOnFinishAction(OnFinishAction onFinishAction) {
		if (onFinishAction == null) throw new IllegalArgumentException("onFinishAction must not be a null");
		this.onFinishAction = onFinishAction;
		return this;
	}
	public void exec(ForkJoinPool pool, List<Task> actions) {
		exec(pool, actions.stream());
	}
	public void exec(ForkJoinPool pool, Stream<Task> actions) {
		Set<Task> finished = new HashSet<>();
		Set<Task> running = new HashSet<>();
		actions.forEach(a -> {
			running.add(a);
			pool.execute(() -> {
				try {
					a.run();
				} catch (Throwable t) {
					getOnErrorAction().onException(a, t);
				}  
				
				synchronized (TaskRunner.this) {
					finished.add(a);
					if (finished.size() == running.size()) {
						pool.execute(() -> {
							getOnFinishAction().onFinish(finished);
						});						
					}
				}
			});
		});
	}
}
