package com.flowcog.infoflow.data.path.builder;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import soot.jimple.infoflow.InfoflowConfiguration.PathBuildingAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.PathConfiguration;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.pathBuilders.ContextInsensitivePathBuilder;
import soot.jimple.infoflow.data.pathBuilders.ContextInsensitiveSourceFinder;
import soot.jimple.infoflow.data.pathBuilders.EmptyPathBuilder;
import soot.jimple.infoflow.data.pathBuilders.IAbstractionPathBuilder;
import soot.jimple.infoflow.data.pathBuilders.IPathBuilderFactory;
import soot.jimple.infoflow.data.pathBuilders.RecursivePathBuilder;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;

/**
 * Default factory class for abstraction path builders
 *
 * @author Steven Arzt
 */
public class FCPathBuilderFactory implements IPathBuilderFactory {

  private final PathConfiguration pathConfiguration;

  /**
   * Creates a new instance of the {@link FCPathBuilderFactory} class
   *
   * @param config
   *            The configuration for reconstructing data flow paths
   */
  public FCPathBuilderFactory(PathConfiguration config) {
    this.pathConfiguration = config;
  }

  /**
   * Creates a new executor object for spawning worker threads
   *
   * @param maxThreadNum
   *            The number of threads to use
   * @return The generated executor
   */
  private InterruptableExecutor createExecutor(int maxThreadNum) {
    int numThreads = Runtime.getRuntime().availableProcessors();
    return new InterruptableExecutor(maxThreadNum == -1 ? numThreads : Math.min(maxThreadNum, numThreads),
        Integer.MAX_VALUE, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
  }

  @Override
  public IAbstractionPathBuilder createPathBuilder(InfoflowManager manager, int maxThreadNum) {
    return createPathBuilder(manager, createExecutor(maxThreadNum));
  }

  @Override
  public IAbstractionPathBuilder createPathBuilder(InfoflowManager manager, InterruptableExecutor executor) {
    switch (pathConfiguration.getPathBuildingAlgorithm()) {
      case Recursive:
        return new RecursivePathBuilder(manager, executor);
      case ContextSensitive:
        return new FCContextSensitivePathBuilder(manager, executor);
      case ContextInsensitive:
        return new ContextInsensitivePathBuilder(manager, executor);
      case ContextInsensitiveSourceFinder:
        return new ContextInsensitiveSourceFinder(manager, executor);
      case None:
        return new EmptyPathBuilder();
    }
    throw new RuntimeException("Unsupported path building algorithm");
  }

  @Override
  public boolean supportsPathReconstruction() {
    switch (pathConfiguration.getPathBuildingAlgorithm()) {
      case Recursive:
      case ContextSensitive:
      case ContextInsensitive:
        return true;
      case ContextInsensitiveSourceFinder:
      case None:
        return false;
    }
    throw new RuntimeException("Unsupported path building algorithm");
  }

  @Override
  public boolean isContextSensitive() {
    return pathConfiguration.getPathBuildingAlgorithm() == PathBuildingAlgorithm.ContextSensitive;
  }

}

