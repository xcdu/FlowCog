package com.flowcog.infoflow;

import com.flowcog.infoflow.data.path.builder.FCBatchPathBuilder;
import com.flowcog.infoflow.data.path.builder.FCConcurrentAbstractionPathBuilder;
import com.flowcog.infoflow.data.path.builder.FCContextSensitivePathBuilder;
import com.flowcog.infoflow.data.path.builder.FCPathBuilderFactory;
import com.flowcog.infoflow.memory.FCMemoryWatcher;
import com.flowcog.infoflow.memory.FCTimeoutWatcher;
import com.flowcog.infoflow.solver.cfg.FCInfoflowCFG;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.FastHierarchy;
import soot.G;
import soot.MethodOrMethodContext;
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode;
import soot.jimple.infoflow.InfoflowConfiguration.DataFlowSolver;
import soot.jimple.infoflow.InfoflowConfiguration.PathConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.SolverConfiguration;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.aliasing.FlowSensitiveAliasStrategy;
import soot.jimple.infoflow.aliasing.IAliasingStrategy;
import soot.jimple.infoflow.aliasing.LazyAliasingStrategy;
import soot.jimple.infoflow.aliasing.NullAliasStrategy;
import soot.jimple.infoflow.aliasing.PtsBasedAliasStrategy;
import soot.jimple.infoflow.cfg.BiDirICFGFactory;
import soot.jimple.infoflow.codeOptimization.DeadCodeEliminator;
import soot.jimple.infoflow.codeOptimization.ICodeOptimizer;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AbstractionAtSink;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.data.FlowDroidMemoryManager.PathDataErasureMode;
import soot.jimple.infoflow.data.pathBuilders.IAbstractionPathBuilder;
import soot.jimple.infoflow.data.pathBuilders.IAbstractionPathBuilder.OnPathBuilderResultAvailable;
import soot.jimple.infoflow.entryPointCreators.IEntryPointCreator;
import soot.jimple.infoflow.handlers.PostAnalysisHandler;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler2;
import soot.jimple.infoflow.handlers.TaintPropagationHandler;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.memory.ISolverTerminationReason;
import soot.jimple.infoflow.memory.reasons.AbortRequestedReason;
import soot.jimple.infoflow.memory.reasons.OutOfMemoryReason;
import soot.jimple.infoflow.memory.reasons.TimeoutReason;
import soot.jimple.infoflow.problems.BackwardsInfoflowProblem;
import soot.jimple.infoflow.problems.InfoflowProblem;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.problems.TaintPropagationResults.OnTaintPropagationResultAdded;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.PredecessorShorteningMode;
import soot.jimple.infoflow.solver.cfg.BackwardsInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;
import soot.jimple.infoflow.solver.executors.SetPoolExecutor;
import soot.jimple.infoflow.solver.memory.DefaultMemoryManagerFactory;
import soot.jimple.infoflow.solver.memory.IMemoryManager;
import soot.jimple.infoflow.solver.memory.IMemoryManagerFactory;
import soot.jimple.infoflow.sourcesSinks.manager.IOneSourceAtATimeManager;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.util.SootMethodRepresentationParser;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.options.Options;

/**
 * main infoflow class which triggers the analysis and offers method to customize it.
 */
public class FCInfoflow extends FCAbstractInfoflow {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private InfoflowResults results = null;
  private InfoflowManager manager;

  private Set<ResultsAvailableHandler> onResultsAvailable = new HashSet<>();
  private TaintPropagationHandler taintPropagationHandler = null;
  private TaintPropagationHandler backwardsPropagationHandler = null;
  private IMemoryManagerFactory memoryManagerFactory = new DefaultMemoryManagerFactory();

  private long maxMemoryConsumption = -1;
  private FCMemoryWatcher memoryWatcher = null;

  private Set<Stmt> collectedSources = null;
  private Set<Stmt> collectedSinks = null;

  protected SootMethod dummyMainMethod = null;

  /**
   * Creates a new instance of the InfoFlow class for analyzing plain Java code without any
   * references to APKs or the Android SDK.
   */
  public FCInfoflow() {
    super();
  }

  /**
   * Creates a new instance of the FCInfoflow class for analyzing Android APK files.
   *
   * @param androidPath If forceAndroidJar is false, this is the base directory of the platform
   * files in the Android SDK. If forceAndroidJar is true, this is the full path of a single
   * android.jar file.
   * @param forceAndroidJar True if a single platform JAR file shall be forced, false if Soot shall
   * pick the appropriate platform version
   */
  public FCInfoflow(String androidPath, boolean forceAndroidJar) {
    super(null, androidPath, forceAndroidJar);
  }

  /**
   * Creates a new instance of the FCInfoflow class for analyzing Android APK files.
   *
   * @param androidPath If forceAndroidJar is false, this is the base directory of the platform
   * files in the Android SDK. If forceAndroidJar is true, this is the full path of a single
   * android.jar file.
   * @param forceAndroidJar True if a single platform JAR file shall be forced, false if Soot shall
   * pick the appropriate platform version
   * @param icfgFactory The interprocedural CFG to be used by the InfoFlowProblem
   */
  public FCInfoflow(String androidPath, boolean forceAndroidJar, BiDirICFGFactory icfgFactory) {
    super(icfgFactory, androidPath, forceAndroidJar);
  }

  @Override
  public void computeInfoflow(String appPath, String libPath, IEntryPointCreator entryPointCreator,
      ISourceSinkManager sourcesSinks) {
    if (sourcesSinks == null) {
      logger.error("Sources are empty!");
      return;
    }

    initializeSoot(appPath, libPath, entryPointCreator.getRequiredClasses());

    // entryPoints are the entryPoints required by Soot to calculate Graph -
    // if there is no main method, we have to create a new main method and
    // use it as entryPoint and store our real entryPoints
    this.dummyMainMethod = entryPointCreator.createDummyMain();
    Scene.v().setEntryPoints(Collections.singletonList(dummyMainMethod));

    // Run the analysis
    runAnalysis(sourcesSinks, null);
  }

  @Override
  public void computeInfoflow(String appPath, String libPath, String entryPoint,
      ISourceSinkManager sourcesSinks) {
    if (sourcesSinks == null) {
      logger.error("Sources are empty!");
      return;
    }

    initializeSoot(appPath, libPath, SootMethodRepresentationParser.v()
        .parseClassNames(Collections.singletonList(entryPoint), false).keySet(), entryPoint);

    if (!Scene.v().containsMethod(entryPoint)) {
      logger.error("Entry point not found: " + entryPoint);
      return;
    }
    SootMethod ep = Scene.v().getMethod(entryPoint);
    if (ep.isConcrete()) {
      ep.retrieveActiveBody();
    } else {
      logger.debug("Skipping non-concrete method " + ep);
      return;
    }
    this.dummyMainMethod = null;
    Scene.v().setEntryPoints(Collections.singletonList(ep));
    Options.v().set_main_class(ep.getDeclaringClass().getName());

    // Compute the additional seeds if they are specified
    Set<String> seeds = Collections.emptySet();
    if (entryPoint != null && !entryPoint.isEmpty()) {
      seeds = Collections.singleton(entryPoint);
    }
    ipcManager.updateJimpleForICC();

    // Run the analysis
    runAnalysis(sourcesSinks, seeds);
  }

  /**
   * Releases the callgraph and all intermediate objects associated with it
   */
  private void releaseCallgraph() {
    Scene.v().releaseCallGraph();
    Scene.v().releasePointsToAnalysis();
    Scene.v().releaseReachableMethods();
    G.v().resetSpark();
  }

  /**
   * Conducts a taint analysis on an already initialized callgraph
   *
   * @param sourcesSinks The sources and sinks to be used
   */
  protected void runAnalysis(final ISourceSinkManager sourcesSinks) {
    runAnalysis(sourcesSinks, null);
  }

  /**
   * Conducts a taint analysis on an already initialized callgraph
   *
   * @param sourcesSinks The sources and sinks to be used
   * @param additionalSeeds Additional seeds at which to create A ZERO fact even if they are not
   * sources
   */
  private void runAnalysis(final ISourceSinkManager sourcesSinks,
      final Set<String> additionalSeeds) {
    try {
      // Clear the data from previous runs
      maxMemoryConsumption = -1;
      results = new InfoflowResults();

      // Print and check our configuration
      checkAndFixConfiguration();
      config.printSummary();

      // Register a memory watcher
      if (memoryWatcher != null) {
        memoryWatcher.clearSolvers();
        memoryWatcher = null;
      }
      memoryWatcher = new FCMemoryWatcher(results);

      // Build the callgraph
      long beforeCallgraph = System.nanoTime();
      constructCallgraph();
      logger.info("Callgraph construction took " + (System.nanoTime() - beforeCallgraph) / 1E9
          + " seconds");

      // Initialize the source sink manager
      if (sourcesSinks != null) {
        sourcesSinks.initialize();
      }

      // Perform constant propagation and remove dead code
      if (config.getCodeEliminationMode() != CodeEliminationMode.NoCodeElimination) {
        long currentMillis = System.nanoTime();
        eliminateDeadCode(sourcesSinks);
        logger.info(
            "Dead code elimination took " + (System.nanoTime() - currentMillis) / 1E9 + " seconds");
      }

      // After constant value propagation, we might find more call edges
      // for reflective method calls
      if (config.getEnableReflection()) {
        releaseCallgraph();
        constructCallgraph();
      }

      if (config.getCallgraphAlgorithm() != CallgraphAlgorithm.OnDemand) {
        logger.info("Callgraph has {} edges", Scene.v().getCallGraph().size());
      }

      if (!config.isTaintAnalysisEnabled()) {
        return;
      }

      // Make sure that we have a path builder factory
      if (pathBuilderFactory == null) {
        pathBuilderFactory = new FCPathBuilderFactory(config.getPathConfiguration());
      }

      logger.info("Starting Taint Analysis");
      IInfoflowCFG iCfg = icfgFactory.buildBiDirICFG(config.getCallgraphAlgorithm(),
          config.getEnableExceptionTracking());

      // Check whether we need to run with one source at a time
      IOneSourceAtATimeManager oneSourceAtATime =
          config.getOneSourceAtATime() && sourcesSinks != null
              && sourcesSinks instanceof IOneSourceAtATimeManager
              ? (IOneSourceAtATimeManager) sourcesSinks
              : null;

      // Reset the current source
      if (oneSourceAtATime != null) {
        oneSourceAtATime.resetCurrentSource();
      }
      boolean hasMoreSources = oneSourceAtATime == null || oneSourceAtATime.hasNextSource();

      while (hasMoreSources) {
        // Fetch the next source
        if (oneSourceAtATime != null) {
          oneSourceAtATime.nextSource();
        }

        // Create the executor that takes care of the workers
        int numThreads = Runtime.getRuntime().availableProcessors();
        InterruptableExecutor executor = createExecutor(numThreads, true);

        // Initialize the memory manager
        IMemoryManager<Abstraction, Unit> memoryManager = createMemoryManager();

        // Initialize the data flow manager
        manager = createInfoflowManager(config, null, iCfg, sourcesSinks, taintWrapper, hierarchy,
            new AccessPathFactory(config));

        // Initialize the alias analysis
        IAliasingStrategy aliasingStrategy = createAliasAnalysis(sourcesSinks, iCfg, executor,
            memoryManager);

        // Get the zero fact
        Abstraction zeroValue = aliasingStrategy.getSolver() != null
            ? aliasingStrategy.getSolver().getTabulationProblem().createZeroValue() : null;

        // Initialize the aliasing infrastructure
        Aliasing aliasing = new Aliasing(aliasingStrategy, manager);
        if (dummyMainMethod != null) {
          aliasing.excludeMethodFromMustAlias(dummyMainMethod);
        }

        // Initialize the data flow problem
        InfoflowProblem forwardProblem = new InfoflowProblem(manager, aliasingStrategy, aliasing,
            zeroValue);

        // We need to create the right data flow solver
        IInfoflowSolver forwardSolver = createForwardSolver(executor, forwardProblem);

        // Set the options
        manager.setForwardSolver(forwardSolver);
        if (aliasingStrategy.getSolver() != null) {
          aliasingStrategy.getSolver().getTabulationProblem().getManager()
              .setForwardSolver(forwardSolver);
        }

        memoryWatcher.addSolver((IMemoryBoundedSolver) forwardSolver);

        forwardSolver.setMemoryManager(memoryManager);
        // forwardSolver.setEnableMergePointChecking(true);

        forwardProblem.setTaintPropagationHandler(taintPropagationHandler);
        forwardProblem.setTaintWrapper(taintWrapper);
        if (nativeCallHandler != null) {
          forwardProblem.setNativeCallHandler(nativeCallHandler);
        }

        if (aliasingStrategy.getSolver() != null) {
          aliasingStrategy.getSolver().getTabulationProblem()
              .setActivationUnitsToCallSites(forwardProblem);
        }

        // Start a thread for enforcing the timeout
        FCTimeoutWatcher timeoutWatcher = null;
        FCTimeoutWatcher pathTimeoutWatcher = null;
        if (config.getDataFlowTimeout() > 0) {
          timeoutWatcher = new FCTimeoutWatcher(config.getDataFlowTimeout(), results);
          timeoutWatcher.addSolver((IMemoryBoundedSolver) forwardSolver);
          if (aliasingStrategy.getSolver() != null) {
            timeoutWatcher.addSolver((IMemoryBoundedSolver) aliasingStrategy.getSolver());
          }
          timeoutWatcher.start();
        }

        InterruptableExecutor resultExecutor = null;
        try {
          // Print our configuration
          if (config.getFlowSensitiveAliasing() && !aliasingStrategy.isFlowSensitive()) {
            logger.warn("Trying to use a flow-sensitive aliasing with an "
                + "aliasing strategy that does not support this feature");
          }
          if (config.getFlowSensitiveAliasing()
              && config.getSolverConfiguration().getMaxJoinPointAbstractions() > 0) {
            logger.warn("Running with limited join point abstractions can break context-"
                + "sensitive path builders");
          }

          // We have to look through the complete program to find
          // sources which are then taken as seeds.
          int sinkCount = 0;
          logger.info("Looking for sources and sinks...");

          for (SootMethod sm : getMethodsForSeeds(iCfg)) {
            sinkCount += scanMethodForSourcesSinks(sourcesSinks, forwardProblem, sm);
          }

          // We optionally also allow additional seeds to be specified
          if (additionalSeeds != null) {
            for (String meth : additionalSeeds) {
              SootMethod m = Scene.v().getMethod(meth);
              if (!m.hasActiveBody()) {
                logger.warn("Seed method {} has no active body", m);
                continue;
              }
              forwardProblem.addInitialSeeds(m.getActiveBody().getUnits().getFirst(),
                  Collections.singleton(forwardProblem.zeroValue()));
            }
          }

          // Report on the sources and sinks we have found
          if (!forwardProblem.hasInitialSeeds()) {
            logger.error("No sources found, aborting analysis");
            continue;
          }
          if (sinkCount == 0) {
            logger.error("No sinks found, aborting analysis");
            continue;
          }
          logger.info("Source lookup done, found {} sources and {} sinks.",
              forwardProblem.getInitialSeeds().size(), sinkCount);

          // Initialize the taint wrapper if we have one
          if (taintWrapper != null) {
            taintWrapper.initialize(manager);
          }
          if (nativeCallHandler != null) {
            nativeCallHandler.initialize(manager);
          }

          // Register the handler for interim results
          TaintPropagationResults propagationResults = forwardProblem.getResults();
          resultExecutor = createExecutor(numThreads, false);

          // Create the path builder
          IAbstractionPathBuilder innerBuilder = pathBuilderFactory
              .createPathBuilder(manager, resultExecutor);

          if (innerBuilder instanceof FCConcurrentAbstractionPathBuilder) {
            ((FCConcurrentAbstractionPathBuilder) innerBuilder).setDisableExecutorReset(true);
          }

          final IAbstractionPathBuilder builder = new FCBatchPathBuilder(manager, innerBuilder);

          // disable the inner builder reset to avoid kill flag reset
          if (innerBuilder instanceof FCContextSensitivePathBuilder
              && builder instanceof FCBatchPathBuilder) {
            ((FCBatchPathBuilder) builder).setDisableInnerBuilderReset(true);
          }

          // If we want incremental result reporting, we have to
          // initialize it before we start the taint tracking
          if (config.getIncrementalResultReporting()) {
            initializeIncrementalResultReporting(propagationResults, builder);
          }

          forwardSolver.solve();
          maxMemoryConsumption = Math.max(maxMemoryConsumption, getUsedMemory());

          // Not really nice, but sometimes Heros returns before all
          // executor tasks are actually done. This way, we give it a
          // chance to terminate gracefully before moving on.
          int terminateTries = 0;
          while (terminateTries < 10) {
            if (executor.getActiveCount() != 0 || !executor.isTerminated()) {
              terminateTries++;
              try {
                Thread.sleep(500);
              } catch (InterruptedException e) {
                logger.error("Could not wait for executor termination", e);
              }
            } else {
              break;
            }
          }
          if (executor.getActiveCount() != 0 || !executor.isTerminated()) {
            logger.error("Executor did not terminate gracefully");
          }

          // Print taint wrapper statistics
          if (taintWrapper != null) {
            logger.info("Taint wrapper hits: " + taintWrapper.getWrapperHits());
            logger.info("Taint wrapper misses: " + taintWrapper.getWrapperMisses());
          }

          // Get the result abstractions
          Set<AbstractionAtSink> res = propagationResults.getResults();
          propagationResults = null;

          // We need to prune access paths that are entailed by
          // another one
          removeEntailedAbstractions(res);

          // Shut down the native call handler
          if (nativeCallHandler != null) {
            nativeCallHandler.shutdown();
          }

          logger.info(
              "IFDS problem with {} forward and {} backward edges solved, "
                  + "processing {} results...",
              forwardSolver.getPropagationCount(), aliasingStrategy.getSolver() == null ? 0
                  : aliasingStrategy.getSolver().getPropagationCount(),
              res == null ? 0 : res.size());

          // Update the statistics
          {
            ISolverTerminationReason reason = ((IMemoryBoundedSolver) forwardSolver)
                .getTerminationReason();
            if (reason != null) {
              if (reason instanceof OutOfMemoryReason) {
                results.setTerminationState(
                    results.getTerminationState() | InfoflowResults.TERMINATION_DATA_FLOW_OOM);
              } else if (reason instanceof TimeoutReason) {
                results.setTerminationState(
                    results.getTerminationState() | InfoflowResults.TERMINATION_DATA_FLOW_TIMEOUT);
              }
            }
          }

          // Force a cleanup. Everything we need is reachable through
          // the results set, the other abstractions can be killed
          // now.
          maxMemoryConsumption = Math.max(maxMemoryConsumption, getUsedMemory());
          logger.info("Current memory consumption: " + (getUsedMemory() / 1000 / 1000) + " MB");

          if (timeoutWatcher != null) {
            timeoutWatcher.stop();
          }
          memoryWatcher.removeSolver((IMemoryBoundedSolver) forwardSolver);
          forwardSolver.cleanup();
          forwardSolver = null;
          forwardProblem = null;

          // Remove the alias analysis from memory
          aliasing = null;
          if (aliasingStrategy.getSolver() != null) {
            memoryWatcher.removeSolver((IMemoryBoundedSolver) aliasingStrategy.getSolver());
          }
          aliasingStrategy.cleanup();
          aliasingStrategy = null;

          if (config.getIncrementalResultReporting()) {
            res = null;
          }
          iCfg.purge();

          // Clean up the manager. Make sure to free objects, even if
          // the manager is still held by other objects
          if (manager != null) {
            manager.cleanup();
          }
          manager = null;

          Runtime.getRuntime().gc();
          logger
              .info("Memory consumption after cleanup: " + (getUsedMemory() / 1000 / 1000) + " MB");

//          // Debug the Abstractions
//          for(AbstractionAtSink abs: res) {
//            System.out.println("abs = " + abs);
//          }

          // Apply the timeout to path reconstruction
          if (config.getPathConfiguration().getPathReconstructionTimeout() > 0) {
            pathTimeoutWatcher = new FCTimeoutWatcher(
                config.getPathConfiguration().getPathReconstructionTimeout(), results);
            pathTimeoutWatcher.addSolver(builder);
            pathTimeoutWatcher.start();
          }

          // Do the normal result computation in the end unless we
          // have used incremental path building
          if (config.getIncrementalResultReporting()) {
            // After the last intermediate result has been computed,
            // we need to re-process those abstractions that
            // received new neighbors in the meantime
            builder.runIncrementalPathCompuation();

            try {
              resultExecutor.awaitCompletion();
            } catch (InterruptedException e) {
              logger.error("Could not wait for executor termination", e);
            }
          } else {
            memoryWatcher.addSolver(builder);
            builder.computeTaintPaths(res);
            res = null;

            // Update the statistics
            {
              ISolverTerminationReason reason = builder.getTerminationReason();
              if (reason != null) {
                if (reason instanceof OutOfMemoryReason) {
                  results.setTerminationState(results.getTerminationState()
                      | InfoflowResults.TERMINATION_PATH_RECONSTRUCTION_OOM);
                } else if (reason instanceof TimeoutReason) {
                  results.setTerminationState(results.getTerminationState()
                      | InfoflowResults.TERMINATION_PATH_RECONSTRUCTION_TIMEOUT);
                }
              }
            }

            // Wait for the path builders to terminate
            try {
              resultExecutor.awaitCompletion();
            } catch (InterruptedException e) {
              logger.error("Could not wait for executor termination", e);
            }

            // Get the results once the path builder is done
            if (this.results == null) {
              this.results = builder.getResults();
            } else {
              this.results.addAll(builder.getResults());
            }
          }
          resultExecutor.shutdown();

          // If the path builder was aborted, we warn the user
          if (builder.isKilled()) {
            logger.warn("Path reconstruction aborted. The reported results may be incomplete. "
                + "You might want to try again with sequential path processing enabled.");
          }
        } finally {
          // Terminate the executor
          if (resultExecutor != null) {
            resultExecutor.shutdown();
          }

          // Make sure to stop the watcher thread
          if (timeoutWatcher != null) {
            timeoutWatcher.stop();
          }
          if (pathTimeoutWatcher != null) {
            pathTimeoutWatcher.stop();
          }

          // Do we have any more sources?
          hasMoreSources = oneSourceAtATime != null && oneSourceAtATime.hasNextSource();

          // Shut down the memory watcher
          memoryWatcher.close();

          // Get rid of all the stuff that's still floating around in
          // memory
          forwardProblem = null;
          forwardSolver = null;
          if (manager != null) {
            manager.cleanup();
          }
          manager = null;
        }

        // Make sure that we are in a sensible state even if we ran out
        // of memory before
        Runtime.getRuntime().gc();
        logger.info(
            "Memory consumption after path building: " + (getUsedMemory() / 1000 / 1000) + " MB");
      }

      // Execute the post-processors
      for (PostAnalysisHandler handler : this.postProcessors) {
        results = handler.onResultsAvailable(results, iCfg);
      }

//      if (results == null || results.isEmpty())
//        logger.warn("No results found.");
//      else if (logger.isInfoEnabled()) {
//        for (ResultSinkInfo sink : results.getResults().keySet()) {
//          logger.info("The sink {} in method {} was called with values from the following sources:", sink,
//              iCfg.getMethodOf(sink.getStmt()).getSignature());
//          for (ResultSourceInfo source : results.getResults().get(sink)) {
//            logger.info("- {} in method {}", source, iCfg.getMethodOf(source.getStmt()).getSignature());
//            if (source.getPath() != null) {
//              logger.info("\ton Path: ");
//              for (Unit p : source.getPath()) {
//                logger.info("\t -> " + iCfg.getMethodOf(p));
//                logger.info("\t\t -> " + p);
//              }
//            }
//          }
//        }
//      }

      // Provide the handler with the final results
      for (ResultsAvailableHandler handler : onResultsAvailable) {
        handler.onResultsAvailable(iCfg, results);
      }

      // Write the Jimple files to disk if requested
      if (config.getWriteOutputFiles()) {
        PackManager.v().writeOutput();
      }

      maxMemoryConsumption = Math.max(maxMemoryConsumption, getUsedMemory());
      logger.info("Maximum memory consumption: " + maxMemoryConsumption / 1E6 + " MB");
    } catch (Exception ex) {
      StringWriter stacktrace = new StringWriter();
      PrintWriter pw = new PrintWriter(stacktrace);
      ex.printStackTrace(pw);
      results.addException(
          ex.getClass().getName() + ": " + ex.getMessage() + "\n" + stacktrace.toString());
      logger.error("Excception during data flow analysis", ex);
    }
  }

  /**
   * Initializes the mechanism for incremental result reporting
   *
   * @param propagationResults A reference to the result object of the forward data flow solver
   * @param builder The path builder to use for reconstructing the taint propagation paths
   */
  private void initializeIncrementalResultReporting(TaintPropagationResults propagationResults,
      final IAbstractionPathBuilder builder) {
    // Create the path builder
    memoryWatcher.addSolver(builder);
    this.results = new InfoflowResults();
    propagationResults.addResultAvailableHandler(new OnTaintPropagationResultAdded() {

      @Override
      public boolean onResultAvailable(AbstractionAtSink abs) {
        builder.addResultAvailableHandler(new OnPathBuilderResultAvailable() {

          @Override
          public void onResultAvailable(ResultSourceInfo source, ResultSinkInfo sink) {
            // Notify our external handlers
            for (ResultsAvailableHandler handler : onResultsAvailable) {
              if (handler instanceof ResultsAvailableHandler2) {
                ResultsAvailableHandler2 handler2 = (ResultsAvailableHandler2) handler;
                handler2.onSingleResultAvailable(source, sink);
              }
            }
            results.addResult(sink, source);
          }

        });

        // Compute the result paths
        builder.computeTaintPaths(Collections.singleton(abs));
        return true;
      }

    });
  }

  /**
   * Checks the configuration of the data flow solver for errors and automatically fixes some common
   * issues
   */
  private void checkAndFixConfiguration() {
    if (config.getEnableStaticFieldTracking() && config.getAccessPathLength() == 0) {
      throw new RuntimeException(
          "Static field tracking must be disabled " + "if the access path length is zero");
    }
    if (config.getAccessPathLength() < 0) {
      throw new RuntimeException("The access path length may not be negative");
    }
    if (config.getSolverConfiguration().getDataFlowSolver() == DataFlowSolver.FlowInsensitive) {
      config.setFlowSensitiveAliasing(false);
      config.setEnableTypeChecking(false);
      logger.warn("Disabled flow-sensitive aliasing because we are running with "
          + "a flow-insensitive data flow solver");
    }
  }

  /**
   * Removes all abstractions from the given set that arrive at the same sink statement as another
   * abstraction, but cover less tainted variables. If, e.g., a.b.* and a.* arrive at the same sink,
   * a.b.* is already covered by a.* and can thus safely be removed.
   *
   * @param res The result set from which to remove all entailed abstractions
   */
  private void removeEntailedAbstractions(Set<AbstractionAtSink> res) {
    for (Iterator<AbstractionAtSink> absAtSinkIt = res.iterator(); absAtSinkIt.hasNext(); ) {
      AbstractionAtSink curAbs = absAtSinkIt.next();
      for (AbstractionAtSink checkAbs : res) {
        if (checkAbs != curAbs && checkAbs.getSinkStmt() == curAbs.getSinkStmt()
            && checkAbs.getAbstraction().isImplicit() == curAbs.getAbstraction().isImplicit()
            && checkAbs.getAbstraction().getSourceContext() == curAbs.getAbstraction()
            .getSourceContext()) {
          if (checkAbs.getAbstraction().getAccessPath()
              .entails(curAbs.getAbstraction().getAccessPath())) {
            absAtSinkIt.remove();
            break;
          }
        }
      }
    }
  }

  /**
   * Initializes the alias analysis
   *
   * @param sourcesSinks The set of sources and sinks
   * @param iCfg The interprocedural control flow graph
   * @param executor The executor in which to run concurrent tasks
   * @param memoryManager The memory manager for rducing the memory load during IFDS propagation
   * @return The alias analysis implementation to use for the data flow analysis
   */
  private IAliasingStrategy createAliasAnalysis(final ISourceSinkManager sourcesSinks,
      IInfoflowCFG iCfg,
      InterruptableExecutor executor, IMemoryManager<Abstraction, Unit> memoryManager) {
    IAliasingStrategy aliasingStrategy;
    IInfoflowSolver backSolver = null;
    BackwardsInfoflowProblem backProblem = null;
    InfoflowManager backwardsManager = null;
    switch (getConfig().getAliasingAlgorithm()) {
      case FlowSensitive:
        backwardsManager = createInfoflowManager(config, null, new BackwardsInfoflowCFG(iCfg),
            sourcesSinks, taintWrapper, hierarchy, manager.getAccessPathFactory());
        backProblem = new BackwardsInfoflowProblem(backwardsManager);

        // We need to create the right data flow solver
        SolverConfiguration solverConfig = config.getSolverConfiguration();
        switch (solverConfig.getDataFlowSolver()) {
          case ContextFlowSensitive:
            backSolver = new soot.jimple.infoflow.solver.fastSolver.InfoflowSolver(backProblem,
                executor);
            break;
          case FlowInsensitive:
            backSolver = new soot.jimple.infoflow.solver.fastSolver.flowInsensitive.InfoflowSolver(
                backProblem,
                executor);
            break;
          default:
            throw new RuntimeException("Unsupported data flow solver");
        }

        backSolver.setMemoryManager(memoryManager);
        backSolver.setPredecessorShorteningMode(
            pathConfigToShorteningMode(manager.getConfig().getPathConfiguration()));
        // backSolver.setEnableMergePointChecking(true);
        backSolver.setMaxJoinPointAbstractions(solverConfig.getMaxJoinPointAbstractions());
        backSolver.setMaxCalleesPerCallSite(solverConfig.getMaxCalleesPerCallSite());
        backSolver.setSolverId(false);
        backProblem.setTaintPropagationHandler(backwardsPropagationHandler);
        backProblem.setTaintWrapper(taintWrapper);
        if (nativeCallHandler != null) {
          backProblem.setNativeCallHandler(nativeCallHandler);
        }

        memoryWatcher.addSolver((IMemoryBoundedSolver) backSolver);

        aliasingStrategy = new FlowSensitiveAliasStrategy(manager, backSolver);
        break;
      case PtsBased:
        backProblem = null;
        backSolver = null;
        aliasingStrategy = new PtsBasedAliasStrategy(manager);
        break;
      case None:
        backProblem = null;
        backSolver = null;
        aliasingStrategy = new NullAliasStrategy();
        break;
      case Lazy:
        backProblem = null;
        backSolver = null;
        aliasingStrategy = new LazyAliasingStrategy(manager);
        break;
      default:
        throw new RuntimeException("Unsupported aliasing algorithm");
    }
    return aliasingStrategy;
  }

  /**
   * Gets the path shortening mode that shall be applied given a certain path reconstruction
   * configuration. This method computes the most aggressive path shortening that is possible
   * without eliminating data that is necessary for the requested path reconstruction.
   *
   * @param pathConfiguration The path reconstruction configuration
   * @return The computed path shortening mode
   */
  private PredecessorShorteningMode pathConfigToShorteningMode(
      PathConfiguration pathConfiguration) {
    if (pathBuilderFactory.supportsPathReconstruction()) {
      switch (pathConfiguration.getPathReconstructionMode()) {
        case Fast:
          return PredecessorShorteningMode.ShortenIfEqual;
        case NoPaths:
          return PredecessorShorteningMode.AlwaysShorten;
        case Precise:
          return PredecessorShorteningMode.NeverShorten;
        default:
          throw new RuntimeException("Unknown path reconstruction mode");
      }
    } else {
      return PredecessorShorteningMode.AlwaysShorten;
    }
  }

  /**
   * Creates the memory manager that helps reduce the memory consumption of the data flow analysis
   *
   * @return The memory manager object
   */
  private IMemoryManager<Abstraction, Unit> createMemoryManager() {
    PathDataErasureMode erasureMode = PathDataErasureMode.EraseAll;
    if (pathBuilderFactory.isContextSensitive()) {
      erasureMode = PathDataErasureMode.KeepOnlyContextData;
    }
    if (pathBuilderFactory.supportsPathReconstruction()) {
      erasureMode = PathDataErasureMode.EraseNothing;
    }
    IMemoryManager<Abstraction, Unit> memoryManager = memoryManagerFactory
        .getMemoryManager(false, erasureMode);
    return memoryManager;
  }

  /**
   * Creates the IFDS solver for the forward data flow problem
   *
   * @param executor The executor in which to run the tasks or propagating IFDS edges
   * @param forwardProblem The implementation of the forward problem
   * @return The solver that solves the forward taint analysis problem
   */
  private IInfoflowSolver createForwardSolver(InterruptableExecutor executor,
      InfoflowProblem forwardProblem) {
    // Depending on the configured solver algorithm, we have to create a
    // different solver object
    IInfoflowSolver forwardSolver;
    SolverConfiguration solverConfig = config.getSolverConfiguration();
    switch (solverConfig.getDataFlowSolver()) {
      case ContextFlowSensitive:
        logger.info("Using context- and flow-sensitive solver");
        forwardSolver = new soot.jimple.infoflow.solver.fastSolver.InfoflowSolver(forwardProblem,
            executor);
        break;
      case FlowInsensitive:
        logger.info("Using context-sensitive, but flow-insensitive solver");
        forwardSolver = new soot.jimple.infoflow.solver.fastSolver.flowInsensitive.InfoflowSolver(
            forwardProblem,
            executor);
        break;
      default:
        throw new RuntimeException("Unsupported data flow solver");
    }

    // Configure the solver
    forwardSolver.setSolverId(true);
    forwardSolver
        .setPredecessorShorteningMode(
            pathConfigToShorteningMode(manager.getConfig().getPathConfiguration()));
    forwardSolver.setMaxJoinPointAbstractions(solverConfig.getMaxJoinPointAbstractions());
    forwardSolver.setMaxCalleesPerCallSite(solverConfig.getMaxCalleesPerCallSite());

    return forwardSolver;
  }

  /**
   * Gets the memory used by FlowDroid at the moment
   *
   * @return FlowDroid's current memory consumption in bytes
   */
  private long getUsedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  /**
   * Runs all code optimizers
   *
   * @param sourcesSinks The SourceSinkManager
   */
  private void eliminateDeadCode(ISourceSinkManager sourcesSinks) {
    InfoflowManager dceManager = createInfoflowManager(config, null, new FCInfoflowCFG(), null, null,
        null,
        new AccessPathFactory(config));
//    InfoflowManager dceManager = new InfoflowManager(config, null, new FCInfoflowCFG(), null, null, null,
//        new AccessPathFactory(config));

    ICodeOptimizer dce = new DeadCodeEliminator();
    dce.initialize(config);
    dce.run(dceManager, Scene.v().getEntryPoints(), sourcesSinks, taintWrapper);
  }

  /**
   * Creates a new executor object for spawning worker threads
   *
   * @param numThreads The number of threads to use
   * @param allowSetSemantics True if the executor shall have thread semantics, i.e., never schedule
   * the same task twice
   * @return The generated executor
   */
  private InterruptableExecutor createExecutor(int numThreads, boolean allowSetSemantics) {
    if (allowSetSemantics) {
      return new SetPoolExecutor(
          config.getMaxThreadNum() == -1 ? numThreads
              : Math.min(config.getMaxThreadNum(), numThreads),
          Integer.MAX_VALUE, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    } else {
      return new InterruptableExecutor(
          config.getMaxThreadNum() == -1 ? numThreads
              : Math.min(config.getMaxThreadNum(), numThreads),
          Integer.MAX_VALUE, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }
  }

  protected Collection<SootMethod> getMethodsForSeeds(IInfoflowCFG icfg) {
    List<SootMethod> seeds = new LinkedList<SootMethod>();
    // If we have a callgraph, we retrieve the reachable methods. Otherwise,
    // we have no choice but take all application methods as an
    // approximation
    if (Scene.v().hasCallGraph()) {
      ReachableMethods reachableMethods = Scene.v().getReachableMethods();
      reachableMethods.update();
      for (Iterator<MethodOrMethodContext> iter = reachableMethods.listener(); iter.hasNext(); ) {
        SootMethod sm = iter.next().method();
        if (isValidSeedMethod(sm)) {
          seeds.add(sm);
        }
      }
    } else {
      long beforeSeedMethods = System.nanoTime();
      Set<SootMethod> doneSet = new HashSet<SootMethod>();
      for (SootMethod sm : Scene.v().getEntryPoints()) {
        getMethodsForSeedsIncremental(sm, doneSet, seeds, icfg);
      }
      logger.info("Collecting seed methods took {} seconds",
          (System.nanoTime() - beforeSeedMethods) / 1E9);
    }
    return seeds;
  }

  private void getMethodsForSeedsIncremental(SootMethod sm, Set<SootMethod> doneSet,
      List<SootMethod> seeds,
      IInfoflowCFG icfg) {
    assert Scene.v().hasFastHierarchy();
    if (!sm.isConcrete() || !sm.getDeclaringClass().isApplicationClass() || !doneSet.add(sm)) {
      return;
    }
    seeds.add(sm);
    for (Unit u : sm.retrieveActiveBody().getUnits()) {
      Stmt stmt = (Stmt) u;
      if (stmt.containsInvokeExpr()) {
        for (SootMethod callee : icfg.getCalleesOfCallAt(stmt)) {
          if (isValidSeedMethod(callee)) {
            getMethodsForSeedsIncremental(callee, doneSet, seeds, icfg);
          }
        }
      }
    }
  }

  /**
   * Gets whether the given method is a valid seen when scanning for sources and sinks. A method is
   * a valid seed it it (or one of its transitive callees) can contain calls to source or sink
   * methods.
   *
   * @param sm The method to check
   * @return True if this method or one of its transitive callees can contain sources or sinks,
   * otherwise false
   */
  protected boolean isValidSeedMethod(SootMethod sm) {
    if (sm == dummyMainMethod) {
      return false;
    }

    // Exclude system classes
    if (config.getIgnoreFlowsInSystemPackages()
        && SystemClassHandler.isClassInSystemPackage(sm.getDeclaringClass().getName())) {
      return false;
    }

    // Exclude library classes
    if (config.getExcludeSootLibraryClasses() && sm.getDeclaringClass().isLibraryClass()) {
      return false;
    }

    return true;
  }

  /**
   * Scans the given method for sources and sinks contained in it. Sinks are just counted, sources
   * are added to the InfoflowProblem as seeds.
   *
   * @param sourcesSinks The SourceSinkManager to be used for identifying sources and sinks
   * @param forwardProblem The InfoflowProblem in which to register the sources as seeds
   * @param m The method to scan for sources and sinks
   * @return The number of sinks found in this method
   */
  private int scanMethodForSourcesSinks(final ISourceSinkManager sourcesSinks,
      InfoflowProblem forwardProblem,
      SootMethod m) {
    if (getConfig().getLogSourcesAndSinks() && collectedSources == null) {
      collectedSources = new HashSet<>();
      collectedSinks = new HashSet<>();
    }

    int sinkCount = 0;
    if (m.hasActiveBody()) {
      // Check whether this is a system class we need to ignore
      if (!isValidSeedMethod(m)) {
        return sinkCount;
      }

      // Look for a source in the method. Also look for sinks. If we
      // have no sink in the program, we don't need to perform any
      // analysis
      PatchingChain<Unit> units = m.getActiveBody().getUnits();
      for (Unit u : units) {
        Stmt s = (Stmt) u;
        if (sourcesSinks.getSourceInfo(s, manager) != null) {
          forwardProblem.addInitialSeeds(u, Collections.singleton(forwardProblem.zeroValue()));
          if (getConfig().getLogSourcesAndSinks()) {
            collectedSources.add(s);
          }
          logger.debug("Source found: {}", u);
        }
        if (sourcesSinks.getSinkInfo(s, manager, null) != null) {
          sinkCount++;
          if (getConfig().getLogSourcesAndSinks()) {
            collectedSinks.add(s);
          }
          logger.debug("Sink found: {}", u);
        }
      }

    }
    return sinkCount;
  }

  @Override
  public InfoflowResults getResults() {
    return results;
  }

  @Override
  public boolean isResultAvailable() {
    if (results == null) {
      return false;
    }
    return true;
  }

  /**
   * Adds a handler that is called when information flow results are available
   *
   * @param handler The handler to add
   */
  public void addResultsAvailableHandler(ResultsAvailableHandler handler) {
    this.onResultsAvailable.add(handler);
  }

  /**
   * Sets a handler which is invoked whenever a taint is propagated
   *
   * @param handler The handler to be invoked when propagating taints
   */
  public void setTaintPropagationHandler(TaintPropagationHandler handler) {
    this.taintPropagationHandler = handler;
  }

  /**
   * Sets a handler which is invoked whenever an alias is propagated backwards
   *
   * @param handler The handler to be invoked when propagating aliases
   */
  public void setBackwardsPropagationHandler(TaintPropagationHandler handler) {
    this.backwardsPropagationHandler = handler;
  }

  /**
   * Removes a handler that is called when information flow results are available
   *
   * @param handler The handler to remove
   */
  public void removeResultsAvailableHandler(ResultsAvailableHandler handler) {
    onResultsAvailable.remove(handler);
  }

  /**
   * Gets the maximum memory consumption during the last analysis run
   *
   * @return The maximum memory consumption during the last analysis run if available, otherwise -1
   */
  public long getMaxMemoryConsumption() {
    return this.maxMemoryConsumption;
  }

  /**
   * Gets the concrete set of sources that have been collected in preparation for the taint
   * analysis. This method will return null if source and sink logging has not been enabled (see
   * InfoflowConfiguration. setLogSourcesAndSinks()),
   *
   * @return The set of sources collected for taint analysis
   */
  public Set<Stmt> getCollectedSources() {
    return this.collectedSources;
  }

  /**
   * Gets the concrete set of sinks that have been collected in preparation for the taint analysis.
   * This method will return null if source and sink logging has not been enabled (see
   * InfoflowConfiguration. setLogSourcesAndSinks()),
   *
   * @return The set of sinks collected for taint analysis
   */
  public Set<Stmt> getCollectedSinks() {
    return this.collectedSinks;
  }

  /**
   * Sets the factory to be used for creating memory managers
   *
   * @param factory The memory manager factory to use
   */
  public void setMemoryManagerFactory(IMemoryManagerFactory factory) {
    this.memoryManagerFactory = factory;
  }

  /**
   * Aborts the data flow analysis. This is useful when the analysis controller is running in a
   * different thread and the main thread (e.g., a GUI) wants to abort the analysis
   */
  public void abortAnalysis() {
    ISolverTerminationReason reason = new AbortRequestedReason();

    if (manager != null) {
      // Stop the forward taint analysis
      if (manager.getForwardSolver() instanceof IMemoryBoundedSolver) {
        IMemoryBoundedSolver boundedSolver = (IMemoryBoundedSolver) manager.getForwardSolver();
        boundedSolver.forceTerminate(reason);
      }
    }

    if (memoryWatcher != null) {
      // Stop all registered solvers
      memoryWatcher.forceTerminate(reason);
    }
  }

  public InfoflowManager createInfoflowManager(InfoflowConfiguration config,
      IInfoflowSolver forwardSolver, IInfoflowCFG icfg, ISourceSinkManager sourceSinkManager,
      ITaintPropagationWrapper taintWrapper, FastHierarchy hierarchy,
      AccessPathFactory accessPathFactory) {
    InfoflowManager instance = null;
    try {
      Class<?> cls = Class.forName(InfoflowManager.class.getName());
      Constructor<?> constructor = cls.getDeclaredConstructor(InfoflowConfiguration.class,
          IInfoflowSolver.class, IInfoflowCFG.class, ISourceSinkManager.class,
          ITaintPropagationWrapper.class, FastHierarchy.class, AccessPathFactory.class);
      constructor.setAccessible(true);
      instance = (InfoflowManager) constructor.newInstance(config, forwardSolver, icfg,
          sourceSinkManager, taintWrapper, hierarchy, accessPathFactory);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    return instance;
  }

}

