package com.meluzin.tibcobwutils.build.deploymentbuild.tasks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meluzin.functional.Log;

public class TaskRunner {
	// Used as helper task, when there are no other tasks
	private static final Task NO_OP_TASK = new Task() {
		
		@Override
		public void run() {
			
		}
		@Override
		public String toString() {
			return "NO_OP Task";
		}
	};
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
		Stream<Task> emptyTask = Stream.of(NO_OP_TASK); // emptyTask is used for case, when there are no actions, otherwise getOnFinisherAction would not be fired
		List<Task> actionsList = Stream.concat(actions, emptyTask).collect(Collectors.toList());
		running.addAll(actionsList);
		actionsList.forEach(a -> {
			pool.execute(() -> {
				try {
					a.run();
				} catch (Throwable t) {
					log.log(Level.SEVERE, "Tasks "+a+" failed", t);		
					getOnErrorAction().onException(a, t);
				}  
				
				synchronized (TaskRunner.this) {
					finished.add(a);
					if (finished.size() == running.size()) {
						pool.execute(() -> {
							getOnFinishAction().onFinish(finished);
						});						
					} else {
						HashSet<Task> copy = new HashSet<>(running);
						copy.removeAll(finished);
						log.info("Remaining tasks: "+copy);
					}
				}
			});
		});
	}
}
