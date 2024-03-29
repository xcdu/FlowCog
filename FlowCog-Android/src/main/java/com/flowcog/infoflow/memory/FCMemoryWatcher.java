package com.flowcog.infoflow.memory;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.collect.ConcurrentHashSet;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.memory.ISolverTerminationReason;
import soot.jimple.infoflow.memory.MemoryWarningSystem;
import soot.jimple.infoflow.memory.MemoryWarningSystem.OnMemoryThresholdReached;
import soot.jimple.infoflow.memory.reasons.OutOfMemoryReason;
import soot.jimple.infoflow.results.InfoflowResults;

/**
 * FlowDroid's implementation of a handler for the memory warning system
 *
 * @author Steven Arzt
 *
 */
public class FCMemoryWatcher {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final MemoryWarningSystem warningSystem = new MemoryWarningSystem();

  private final Set<IMemoryBoundedSolver> solvers = new ConcurrentHashSet<>();
  private final InfoflowResults results;

  /**
   * Creates a new instance of the {@link FCMemoryWatcher} class
   */
  public FCMemoryWatcher() {
    this(null);
  }

  /**
   * Creates a new instance of the {@link FCMemoryWatcher} class
   *
   * @param res
   *            The result object in which to register any abortions
   */
  public FCMemoryWatcher(InfoflowResults res) {
    // Register ourselves in the warning system
    warningSystem.addListener(new OnMemoryThresholdReached() {

      @Override
      public void onThresholdReached(long usedMemory, long maxMemory) {
        // Add the incident to the result object
        if (results != null)
          results.addException("Memory threshold reached");

        // We stop the data flow analysis
        forceTerminate();
        logger.warn("Running out of memory, solvers terminated");
      }

    });
    MemoryWarningSystem.setWarningThreshold(0.9d);
    this.results = res;
  }

  /**
   * Adds a solver that shall be terminated when the memory threshold is
   * reached
   *
   * @param solver
   *            A solver that shall be terminated when the memory threshold is
   *            reached
   */
  public void addSolver(IMemoryBoundedSolver solver) {
    this.solvers.add(solver);
  }

  /**
   * Removes the given solver from the watch list. The given solver will no
   * longer ne notified when the memory threshold is reached.
   *
   * @param solver
   *            The solver to remove from the watch list
   * @return True if the given solver was found in the watch list, otherwise
   *         false
   */
  public boolean removeSolver(IMemoryBoundedSolver solver) {
    return this.solvers.remove(solver);
  }

  /**
   * Clears the list of solvers registered with this memory watcher
   */
  public void clearSolvers() {
    this.solvers.clear();
  }

  /**
   * Shuts down the memory watcher and frees all resources associated with it
   */
  public void close() {
    clearSolvers();
    warningSystem.close();
  }

  /**
   * Forces the termination of all registered solvers
   */
  public void forceTerminate() {
    Runtime runtime = Runtime.getRuntime();
    long usedMem = runtime.totalMemory() - runtime.freeMemory();
    forceTerminate(new OutOfMemoryReason(usedMem));
  }

  /**
   * Forces the termination of all registered solvers
   */
  public void forceTerminate(ISolverTerminationReason reason) {
    for (IMemoryBoundedSolver solver : solvers) {
      solver.forceTerminate(reason);
    }
  }

}

