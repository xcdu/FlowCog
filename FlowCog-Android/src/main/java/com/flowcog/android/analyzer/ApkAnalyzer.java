package com.flowcog.android.analyzer;

import ch.qos.logback.classic.Level;
import com.flowcog.android.data.ui.view.UIViewRelatedSourceSinkManager;
import com.flowcog.display.ApkAnalyzerLoggerToggle;
import com.flowcog.infoflow.FCInfoflow;
import com.flowcog.reflection.ConfigGetSootIntegrationMode;
import com.flowcog.result.FCSemanticExtractionResults;
import com.flowcog.result.TaintedResultsRecorder;
import com.flowcog.result.statistics.FCResultsStatistics;
import heros.solver.Pair;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.activation.UnsupportedDataTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParserException;
import soot.G;
import soot.Main;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.InfoflowConfiguration.ImplicitFlowMode;
import soot.jimple.infoflow.InfoflowConfiguration.PathBuildingAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.PathReconstructionMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.IccConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.SootIntegrationMode;
import soot.jimple.infoflow.android.callbacks.AbstractCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.CallbackDefinition;
import soot.jimple.infoflow.android.callbacks.CallbackDefinition.CallbackType;
import soot.jimple.infoflow.android.callbacks.DefaultCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.FastCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.filters.AlienFragmentFilter;
import soot.jimple.infoflow.android.callbacks.filters.AlienHostComponentFilter;
import soot.jimple.infoflow.android.callbacks.filters.ApplicationCallbackFilter;
import soot.jimple.infoflow.android.config.SootConfigForAndroid;
import soot.jimple.infoflow.android.data.AndroidMemoryManager;
import soot.jimple.infoflow.android.data.parsers.PermissionMethodParser;
import soot.jimple.infoflow.android.iccta.IccInstrumenter;
import soot.jimple.infoflow.android.iccta.IccResults;
import soot.jimple.infoflow.android.iccta.MessengerInstrumenter;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.ARSCFileParser.AbstractResource;
import soot.jimple.infoflow.android.resources.ARSCFileParser.StringResource;
import soot.jimple.infoflow.android.resources.LayoutFileParser;
import soot.jimple.infoflow.android.resources.controls.LayoutControl;
import soot.jimple.infoflow.android.source.AccessPathBasedSourceSinkManager;
import soot.jimple.infoflow.android.source.ConfigurationBasedCategoryFilter;
import soot.jimple.infoflow.android.source.parsers.xml.XMLSourceSinkParser;
import soot.jimple.infoflow.cfg.BiDirICFGFactory;
import soot.jimple.infoflow.cfg.LibraryClassPatcher;
import soot.jimple.infoflow.config.IInfoflowConfig;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.FlowDroidMemoryManager.PathDataErasureMode;
import soot.jimple.infoflow.entryPointCreators.AndroidEntryPointCreator;
import soot.jimple.infoflow.handlers.PostAnalysisHandler;
import soot.jimple.infoflow.handlers.PreAnalysisHandler;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.handlers.TaintPropagationHandler;
import soot.jimple.infoflow.ipc.IIPCManager;
import soot.jimple.infoflow.memory.FlowDroidMemoryWatcher;
import soot.jimple.infoflow.memory.FlowDroidTimeoutWatcher;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.rifl.RIFLSourceSinkDefinitionProvider;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.memory.IMemoryManager;
import soot.jimple.infoflow.solver.memory.IMemoryManagerFactory;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinitionProvider;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.infoflow.values.IValueProvider;
import soot.options.Options;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class ApkAnalyzer {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ISourceSinkDefinitionProvider taintTrackingSourceSinkProvider;
  private ISourceSinkDefinitionProvider viewTrackingSourceSinkProvider;
  private MultiMap<SootClass, CallbackDefinition> callbackMethods = new HashMultiMap<>();
  private MultiMap<SootClass, SootClass> fragmentClasses = new HashMultiMap<>();

  private InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();

  private Set<SootClass> entrypoints = null;
  private Set<String> callbackClasses = null;
  private SootMethod dummyMainMethod = null;
  private UIViewRelatedSourceSinkManager uiViewRelatedSourceSinkManager = null;

  private ARSCFileParser resources = null;
  private ProcessManifest manifest = null;
  private IValueProvider valueProvider = null;

  private final boolean forceAndroidJar;
  private ITaintPropagationWrapper taintWrapper;

  private ISourceSinkManager taintTrackingSourceSinkManager = null;
  private ISourceSinkManager viewTrackingSourceSinkManager = null;

  private IInfoflowConfig sootConfig = new SootConfigForAndroid();
  private BiDirICFGFactory cfgFactory = null;

  private IIPCManager ipcManager = null;

  private long maxMemoryConsumption = -1;

  private Set<Stmt> collectedSources = null;
  private Set<Stmt> collectedSinks = null;

  private String callbackFile = "AndroidCallbacks.txt";
  private SootClass scView = null;

  private Set<PreAnalysisHandler> preprocessors = new HashSet<>();
  private TaintPropagationHandler taintPropagationHandler = null;
  private Set<ResultsAvailableHandler> resultsAvailableHandlers = new HashSet<>();

  private InternalInfoflowInst taintTrackingInfoflow = null;
  private InternalInfoflowInst viewTrackingInfoflow = null;

  private TaintedResultsRecorder taintedResultsRecorder = new TaintedResultsRecorder();

  public ApkAnalyzer(InfoflowAndroidConfiguration config) {
    this(config, null);
  }

  public ApkAnalyzer(InfoflowAndroidConfiguration config, IIPCManager ipcManager) {
    this.config = config;
    this.ipcManager = ipcManager;
    String platformDir = config.getAnalysisFileConfig().getAndroidPlatformDir();
    if (platformDir == null || platformDir.isEmpty()) {
      throw new RuntimeException("Android platform directory not specified");
    }
    File f = new File(platformDir);
    this.forceAndroidJar = f.isFile();
  }

  public void setTaintWrapper(ITaintPropagationWrapper taintWrapper) {
    this.taintWrapper = taintWrapper;
  }


  public FCSemanticExtractionResults run() throws IOException {
    logger.info("Start taint tracking taint analysis");
    long taintTrackingStartTime = System.currentTimeMillis();
    // define local variables
    String apkFileLocation = config.getAnalysisFileConfig().getTargetAPKFile();

    // Set app name
    FCResultsStatistics.v().setAppName((new File(apkFileLocation)).getName());

    // disabled logger in taint tracing phase to avoid confusing when logging
    ApkAnalyzerLoggerToggle.v().getTaintTrackingPhaseToggle().set(Level.WARN);

    // Initialize necessary fields for FRTA
    this.dummyMainMethod = null;
    this.taintTrackingInfoflow = null;

    // Initialize source/sink file parser for first round taint analysis (FRTA)
    this.taintTrackingSourceSinkProvider = initializeSourceSinkProvider(
        config.getAnalysisFileConfig().getSourceSinkFile());

    // Set configurations for FRTA
    // force to enable merge dex files
    config.setMergeDexFiles(true);
    // set to precise to found the potential path
    config.getPathConfiguration().setPathReconstructionMode(PathReconstructionMode.Fast);
    // force to set oneComponentAtATime to false to avoid unexpected processing inside ApkAnalyzer
    config.setOneComponentAtATime(false);

    // Start a new soot instance, always reset the soot (ignore the sootIntegrationMode in config)
    initializeSoot(true);

    // Start to build the call graph
    // Perform basic app parsing
    try {
      parseAppResources();
    } catch (IOException | XmlPullParserException e) {
      logger.error("Callgraph construction failed: " + e.getMessage());
      e.printStackTrace();
      throw new RuntimeException("Callgraph construction failed", e);
    }

    // Create the data flow tracker for FRTA
    taintTrackingInfoflow = createInfoflow();

    // Add result available handler to aggregate the results
    InfoflowResultsHandler taintInfoflowResultsHandler = new InfoflowResultsHandler();
    taintTrackingInfoflow.addResultsAvailableHandler(taintInfoflowResultsHandler);

    // In one-component-at-a-time, we do not have a single entry point creator
    List<SootClass> entrypointWorklist = new ArrayList<>();
    SootClass dummyEntrypoint;
    if (Scene.v().containsClass("dummy")) {
      dummyEntrypoint = Scene.v().getSootClass("dummy");
    } else {
      dummyEntrypoint = new SootClass("dummy");
    }
    entrypointWorklist.add(dummyEntrypoint);

    // For every entry point (or the dummy entry point which stands for all
    // entry points at once), run the data flow analysis
    while (!entrypointWorklist.isEmpty()) {
      entrypointWorklist.remove(0);

      // Perform basic app parsing
      try {
        calculateCallbacksForTaintTracking(taintTrackingSourceSinkProvider, null);
      } catch (IOException | XmlPullParserException e) {
        logger.error("Callgraph construction failed: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("Callgraph construction failed", e);
      }

      taintTrackingInfoflow.runAnalysis(taintTrackingSourceSinkManager, dummyMainMethod);

      // record collected source & sink
      if (config.getLogSourcesAndSinks() && taintTrackingInfoflow.getCollectedSources() != null) {
        this.collectedSources.addAll(taintTrackingInfoflow.getCollectedSources());
      }
      if (config.getLogSourcesAndSinks() && taintTrackingInfoflow.getCollectedSinks() != null) {
        this.collectedSinks.addAll(taintTrackingInfoflow.getCollectedSinks());
      }

      logger.info("Found {} leaks in taint tracking phase",
          taintInfoflowResultsHandler.getLastResults().size());
      taintedResultsRecorder
          .setTaintTrackingResults(taintInfoflowResultsHandler.getLastResults().getResults());
      taintedResultsRecorder.setTaintICfg(taintInfoflowResultsHandler.getLastICFG());

      // We don't need the computed callbacks anymore
      this.callbackMethods.clear();
      this.fragmentClasses.clear();
    }
    // reset the loggers
    ApkAnalyzerLoggerToggle.v().getTaintTrackingPhaseToggle().set(Level.INFO);
    FCResultsStatistics.v()
        .setTaintTrackingTime(System.currentTimeMillis() - taintTrackingStartTime);
    FCResultsStatistics.v().setTaintTrackingFinished(true);
    logger.info("Taint tracking taint analysis finished");

    // ================= //

    // Start second round taint analysis (SRTA)
    logger.info("Start view tracking taint analysis");
    long viewTrackingStartTime = System.currentTimeMillis();
    // Set necessary fields for SRTA
    this.dummyMainMethod = null;
    this.resources = null;
    this.manifest = null;
    this.collectedSources = config.getLogSourcesAndSinks() ? new HashSet<>() : null;
    this.collectedSinks = config.getLogSourcesAndSinks() ? new HashSet<>() : null;
    this.maxMemoryConsumption = 0;
    this.viewTrackingInfoflow = null;
    this.uiViewRelatedSourceSinkManager = new UIViewRelatedSourceSinkManager(getTaintSources(),
        getTaintSinks());
    this.viewTrackingSourceSinkProvider = createViewTrackingSourceSinkProvider(
        uiViewRelatedSourceSinkManager);

    // Reset configuration for SRTA
    config.getPathConfiguration().setPathReconstructionMode(PathReconstructionMode.Fast);
    config.getPathConfiguration().setPathBuildingAlgorithm(PathBuildingAlgorithm.ContextSensitive);
    config.setImplicitFlowMode(ImplicitFlowMode.AllImplicitFlows);

    // Reset Soot
    System.gc();
    initializeSoot(true);

    // Start to build the call graph
    // Perform basic app parsing
    try {
      parseAppResources();
    } catch (IOException | XmlPullParserException e) {
      logger.error("Callgraph construction failed: " + e.getMessage());
      e.printStackTrace();
      throw new RuntimeException("Callgraph construction failed", e);
    }

    viewTrackingInfoflow = createInfoflow();
    // Add result available handler to aggregate the results
    InfoflowResultsHandler viewInfoflowResultsHandler = new InfoflowResultsHandler();
    viewTrackingInfoflow.addResultsAvailableHandler(viewInfoflowResultsHandler);

    // In one-component-at-a-time, we do not have a single entry point
    // creator
    entrypointWorklist = new ArrayList<>();
    if (Scene.v().containsClass("dummy")) {
      dummyEntrypoint = Scene.v().getSootClass("dummy");
    } else {
      dummyEntrypoint = new SootClass("dummy");
    }
    entrypointWorklist.add(dummyEntrypoint);

    // For every entry point (or the dummy entry point which stands for all
    // entry points at once), run the data flow analysis
    while (!entrypointWorklist.isEmpty()) {
      SootClass entrypoint = entrypointWorklist.remove(0);

      // Perform basic app parsing
      try {
        calculateCallbacksForViewTracking(viewTrackingSourceSinkProvider, null);
      } catch (IOException | XmlPullParserException e) {
        logger.error("Callgraph construction failed: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("Callgraph construction failed", e);
      }

      final Set<SourceSinkDefinition> sources = viewTrackingSourceSinkProvider.getSources();
      final Set<SourceSinkDefinition> sinks = viewTrackingSourceSinkProvider.getSinks();
      logger.info(
          "Running data flow analysis with view tracking on {} with {} sources and {} sinks...",
          apkFileLocation,
          sources == null ? 0 : sources.size(), sinks == null ? 0 : sinks.size());

      viewTrackingInfoflow.runAnalysis(viewTrackingSourceSinkManager, dummyMainMethod);

      taintedResultsRecorder
          .setViewTrackingResults(viewInfoflowResultsHandler.getLastResults().getResults());
      taintedResultsRecorder.setViewICfg(viewInfoflowResultsHandler.getLastICFG());

      // Update the statistics
      this.maxMemoryConsumption = Math
          .max(this.maxMemoryConsumption, viewTrackingInfoflow.getMaxMemoryConsumption());
      FCResultsStatistics.v().setMemoryConsumption(maxMemoryConsumption);

      // Print out the found results
      int resCount = viewInfoflowResultsHandler.getLastResults() == null ? 0
          : viewInfoflowResultsHandler.getLastResults().size();
      logger.info("Found {} leaks in view tacking phase", resCount);

      // We don't need the computed callbacks anymore
      this.callbackMethods.clear();
      this.fragmentClasses.clear();

      FCResultsStatistics.v()
          .setViewTrackingTime(System.currentTimeMillis() - viewTrackingStartTime);
      FCResultsStatistics.v().setViewTrackingFinished(true);
      logger.info("View tracking taint analysis finished");
    }

    FlowTextCorrelator flowTextCorrelator = new FlowTextCorrelator(taintedResultsRecorder,
        config.getAnalysisFileConfig());
    long correlationStartTime = System.currentTimeMillis();
    flowTextCorrelator.correlateFlowText();
    flowTextCorrelator.printSematicExtractionResults();
    FCResultsStatistics.v().setUiCorrelationTime(System.currentTimeMillis() - correlationStartTime);
    FCResultsStatistics.v().setFlowCogFinished(true);
    return flowTextCorrelator.getSeResults();
  }

  private ISourceSinkDefinitionProvider initializeSourceSinkProvider(String sourceSinkFile)
      throws IOException {
    if (sourceSinkFile == null || sourceSinkFile.isEmpty()) {
      throw new RuntimeException("No source/sink file specified for data flow analysis");
    }
    String fileExtension = sourceSinkFile.substring(sourceSinkFile.lastIndexOf("."));
    fileExtension = fileExtension.toLowerCase();

    ISourceSinkDefinitionProvider parser = null;
    try {
      if (fileExtension.equals(".xml")) {
        parser =
            XMLSourceSinkParser.fromFile(
                sourceSinkFile, new ConfigurationBasedCategoryFilter(config.getSourceSinkConfig()));
      } else if (fileExtension.equals(".txt")) {
        parser = PermissionMethodParser.fromFile(sourceSinkFile);
      } else if (fileExtension.equals(".rifl")) {
        parser = new RIFLSourceSinkDefinitionProvider(sourceSinkFile);
      } else {
        throw new UnsupportedDataTypeException("The Inputfile isn't a .txt or .xml file.");
      }
    } catch (SAXException ex) {
      throw new IOException("Could not read XML file", ex);
    }

    return parser;

  }

  /**
   * Initializes soot for running the soot-based phases of the application metadata analysis
   *
   * @param constructCallgraph True if a callgraph shall be constructed, otherwise false
   */
  private void initializeSoot(boolean constructCallgraph) {
    logger.info("Initializing Soot...");

    final String androidJar = config.getAnalysisFileConfig().getAndroidPlatformDir();
    final String apkFileLocation = config.getAnalysisFileConfig().getTargetAPKFile();

    // Clean up any old Soot instance we may have
    G.reset();

    Options.v().set_no_bodies_for_excluded(true);
    Options.v().set_allow_phantom_refs(true);
    if (config.getWriteOutputFiles()) {
      Options.v().set_output_format(Options.output_format_jimple);
    } else {
      Options.v().set_output_format(Options.output_format_none);
    }
    Options.v().set_whole_program(constructCallgraph);
    Options.v().set_process_dir(Collections.singletonList(apkFileLocation));
    if (forceAndroidJar) {
      Options.v().set_force_android_jar(androidJar);
    } else {
      Options.v().set_android_jars(androidJar);
    }
    Options.v().set_src_prec(Options.src_prec_apk_class_jimple);
    Options.v().set_keep_line_number(false);
    Options.v().set_keep_offset(false);
    Options.v().set_throw_analysis(Options.throw_analysis_dalvik);
    Options.v().set_process_multiple_dex(config.getMergeDexFiles());

    // Set the Soot configuration options. Note that this will needs to be
    // done before we compute the classpath.
    if (sootConfig != null) {
      sootConfig.setSootOptions(Options.v(), config);
    }

    // Build the classpath for analysis
    final String additionalClasspath = config.getAnalysisFileConfig().getAdditionalClasspath();
    String classpath =
        forceAndroidJar ? androidJar : Scene.v().getAndroidJarPath(androidJar, apkFileLocation);
    if (additionalClasspath != null && !additionalClasspath.isEmpty()) {
      classpath += File.pathSeparator + additionalClasspath;
    }

    Options.v().set_soot_classpath(classpath);
    Main.v().autoSetOptions();

    // Configure the callgraph algorithm
    if (constructCallgraph) {
      switch (config.getCallgraphAlgorithm()) {
        case AutomaticSelection:
        case SPARK:
          Options.v().setPhaseOption("cg.spark", "on");
          break;
        case GEOM:
          Options.v().setPhaseOption("cg.spark", "on");
          AbstractInfoflow.setGeomPtaSpecificOptions();
          break;
        case CHA:
          Options.v().setPhaseOption("cg.cha", "on");
          break;
        case RTA:
          Options.v().setPhaseOption("cg.spark", "on");
          Options.v().setPhaseOption("cg.spark", "rta:true");
          Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
          break;
        case VTA:
          Options.v().setPhaseOption("cg.spark", "on");
          Options.v().setPhaseOption("cg.spark", "vta:true");
          break;
        default:
          throw new RuntimeException("Invalid callgraph algorithm");
      }
    }
    if (config.getEnableReflection()) {
      Options.v().setPhaseOption("cg", "types-for-invoke:true");
    }

    // Load whatever we need
    logger.info("Loading dex files...");
    Scene.v().loadNecessaryClasses();

    // Make sure that we have valid Jimple bodies
    PackManager.v().getPack("wjpp").apply();

    // Patch the callgraph to support additional edges. We do this now,
    // because during callback discovery, the context-insensitive callgraph
    // algorithm would flood us with invalid edges.
    LibraryClassPatcher patcher = new LibraryClassPatcher();
    patcher.patchLibraries();
  }

  /**
   * Parses common app resources such as the manifest file
   *
   * @throws IOException Thrown if the given source/sink file could not be read.
   * @throws XmlPullParserException Thrown if the Android manifest file could not be read.
   */
  private void parseAppResources() throws IOException, XmlPullParserException {
    final String targetAPK = config.getAnalysisFileConfig().getTargetAPKFile();

    // To look for callbacks, we need to start somewhere. We use the Android
    // lifecycle methods for this purpose.
    this.manifest = new ProcessManifest(targetAPK);
    Set<String> entryPoints = manifest.getEntryPointClasses();
    this.entrypoints = new HashSet<>(entryPoints.size());
    for (String className : entryPoints) {
      this.entrypoints.add(Scene.v().getSootClassUnsafe(className));
    }

    // Parse the resource file
    long beforeARSC = System.nanoTime();
    this.resources = new ARSCFileParser();
    this.resources.parse(targetAPK);
    logger.info("ARSC file parsing took " + (System.nanoTime() - beforeARSC) / 1E9 + " seconds");
  }

  /**
   * Instantiates and configures the data flow engine
   *
   * @return A properly configured instance of the {@link InternalInfoflowInst} class
   */
  private InternalInfoflowInst createInfoflow() {
    // Initialize and configure the data flow tracker
    final String androidJar = config.getAnalysisFileConfig().getTargetAPKFile();
    InternalInfoflowInst info = new InternalInfoflowInst(androidJar, forceAndroidJar, cfgFactory);
    if (ipcManager != null) {
      info.setIPCManager(ipcManager);
    }
    info.setConfig(config);
    info.setSootConfig(sootConfig);
    info.setTaintWrapper(taintWrapper);
    info.setTaintPropagationHandler(taintPropagationHandler);

    // We use a specialized memory manager that knows about Android
    info.setMemoryManagerFactory(new IMemoryManagerFactory() {

      @Override
      public IMemoryManager<Abstraction, Unit> getMemoryManager(boolean tracingEnabled,
          PathDataErasureMode erasePathData) {
        return new AndroidMemoryManager(tracingEnabled, erasePathData, entrypoints);
      }

    });

    // Inject additional post-processors
    info.setPostProcessors(Collections.singleton(new PostAnalysisHandler() {

      @Override
      public InfoflowResults onResultsAvailable(InfoflowResults results, IInfoflowCFG cfg) {
        // Purify the ICC results if requested
        final IccConfiguration iccConfig = config.getIccConfig();
        if (iccConfig.isIccResultsPurifyEnabled()) {
          results = IccResults.clean(cfg, results);
        } else if (iccConfig.isIccEnabled()) {
          results = IccResults.expand(cfg, results);
        }

        return results;
      }

    }));
    return info;
  }

  private ISourceSinkDefinitionProvider createViewTrackingSourceSinkProvider(
      UIViewRelatedSourceSinkManager uiSourceSink) {
    // Override parser to add sinks related to view
    return new ISourceSinkDefinitionProvider() {
      @Override
      public Set<SourceSinkDefinition> getSources() {
        return uiSourceSink.getSources().getComplete();
      }

      @Override
      public Set<SourceSinkDefinition> getSinks() {
        return uiSourceSink.getSinks().getComplete();
      }

      @Override
      public Set<SourceSinkDefinition> getAllMethods() {
        Set<SourceSinkDefinition> sources =
            uiSourceSink.getSources().getComplete();
        Set<SourceSinkDefinition> sinks =
            uiSourceSink.getSinks().getComplete();
        Set<SourceSinkDefinition> sourcesSinks = new HashSet<>(sources.size() + sinks.size());
        sourcesSinks.addAll(sources);
        sourcesSinks.addAll(sinks);
        return sourcesSinks;
      }
    };
  }


  private void calculateCallbacksForTaintTracking(
      ISourceSinkDefinitionProvider sourcesAndSinks, SootClass entryPoint)
      throws IOException, XmlPullParserException {
    // Add the callback methods
    LayoutFileParser lfp = null;
    if (config.getCallbackConfig().getEnableCallbacks()) {
      if (callbackClasses != null && callbackClasses.isEmpty()) {
        logger.warn("Callback definition file is empty, disabling callbacks");
      } else {
        lfp = new LayoutFileParser(this.manifest.getPackageName(), this.resources);
        switch (config.getCallbackConfig().getCallbackAnalyzer()) {
          case Fast:
            calculateCallbackMethodsFast(lfp, entryPoint);
            break;
          case Default:
            calculateCallbackMethods(lfp, entryPoint);
            break;
          default:
            throw new RuntimeException("Unknown callback analyzer");
        }
      }
    } else {
      try {
        ConfigGetSootIntegrationMode configGetSootIntegrationMode =
            new ConfigGetSootIntegrationMode(config.getSootIntegrationMode());
        if (configGetSootIntegrationMode.needsToBuildCallgraph()) {
          // Create the new iteration of the main method
          createMainMethod(null);
          constructCallgraphInternal();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    logger.info("Entry point calculation done.");

    if (taintTrackingSourceSinkProvider != null) {
      // Get the callbacks for the current entry point
      Set<CallbackDefinition> callbacks;
      if (entryPoint == null) {
        callbacks = this.callbackMethods.values();
      } else {
        callbacks = this.callbackMethods.get(entryPoint);
      }

      // Create the SourceSinkManager
      taintTrackingSourceSinkManager = createSourceSinkManager(lfp, callbacks,
          taintTrackingSourceSinkProvider);
    }
  }

  private void calculateCallbacksForViewTracking(
      ISourceSinkDefinitionProvider sourcesAndSinks, SootClass entryPoint)
      throws IOException, XmlPullParserException {
    // Add the callback methods
    LayoutFileParser lfp = null;
    if (config.getCallbackConfig().getEnableCallbacks()) {
      if (callbackClasses != null && callbackClasses.isEmpty()) {
        logger.warn("Callback definition file is empty, disabling callbacks");
      } else {
        lfp = new LayoutFileParser(this.manifest.getPackageName(), this.resources);
        switch (config.getCallbackConfig().getCallbackAnalyzer()) {
          case Fast:
            calculateCallbackMethodsFast(lfp, entryPoint);
            break;
          case Default:
            calculateCallbackMethods(lfp, entryPoint);
            break;
          default:
            throw new RuntimeException("Unknown callback analyzer");
        }
      }
    } else {
      try {
        ConfigGetSootIntegrationMode configGetSootIntegrationMode =
            new ConfigGetSootIntegrationMode(config.getSootIntegrationMode());
        if (configGetSootIntegrationMode.needsToBuildCallgraph()) {
          // Create the new iteration of the main method
          createMainMethod(null);
          constructCallgraphInternal();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    logger.info("Entry point calculation done.");

    if (viewTrackingSourceSinkProvider != null) {
      // Get the callbacks for the current entry point
      Set<CallbackDefinition> callbacks;
      if (entryPoint == null) {
        callbacks = this.callbackMethods.values();
      } else {
        callbacks = this.callbackMethods.get(entryPoint);
      }

      // Create the SourceSinkManager
      viewTrackingSourceSinkManager = createSourceSinkManager(lfp, callbacks,
          viewTrackingSourceSinkProvider);
    }
  }

  private void calculateCallbackMethodsFast(LayoutFileParser lfp, SootClass component)
      throws IOException {
    // Construct the current callgraph
    releaseCallgraph();
    createMainMethod(component);
    constructCallgraphInternal();

    // Get the classes for which to find callbacks
    Set<SootClass> entryPointClasses = getComponentsToAnalyze(component);

    // Collect the callback interfaces implemented in the app's
    // source code
    AbstractCallbackAnalyzer jimpleClass =
        callbackClasses == null
            ? new FastCallbackAnalyzer(config, entryPointClasses, callbackFile)
            : new FastCallbackAnalyzer(config, entryPointClasses, callbackClasses);
    if (valueProvider != null) {
      jimpleClass.setValueProvider(valueProvider);
    }
    jimpleClass.collectCallbackMethods();

    // Collect the results
    this.callbackMethods.putAll(jimpleClass.getCallbackMethods());
    this.entrypoints.addAll(jimpleClass.getDynamicManifestComponents());

    // Find the user-defined sources in the layout XML files. This
    // only needs to be done once, but is a Soot phase.
    lfp.parseLayoutFileDirect(config.getAnalysisFileConfig().getTargetAPKFile());

    // Collect the XML-based callback methods
    collectXmlBasedCallbackMethods(lfp, jimpleClass);

    // Construct the final callgraph
    releaseCallgraph();
    createMainMethod(component);
    constructCallgraphInternal();
  }

  /**
   * Calculates the set of callback methods declared in the XML resource files or the app's source
   * code
   *
   * @param lfp The layout file parser to be used for analyzing UI controls
   * @param component The Android component for which to compute the callbacks. Pass null to compute
   * callbacks for all components.
   * @throws IOException Thrown if a required configuration cannot be read
   */
  private void calculateCallbackMethods(LayoutFileParser lfp, SootClass component)
      throws IOException {
    final CallbackConfiguration callbackConfig = config.getCallbackConfig();

    // Load the APK file
    try {
      ConfigGetSootIntegrationMode configGetSootIntegrationMode =
          new ConfigGetSootIntegrationMode(config.getSootIntegrationMode());
      if (configGetSootIntegrationMode.needsToBuildCallgraph()) {
        releaseCallgraph();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Make sure that we don't have any leftovers from previous runs
    PackManager.v().getPack("wjtp").remove("wjtp.lfp");
    PackManager.v().getPack("wjtp").remove("wjtp.ajc");

    // Get the classes for which to find callbacks
    Set<SootClass> entryPointClasses = getComponentsToAnalyze(component);

    // Collect the callback interfaces implemented in the app's
    // source code. Note that the filters should know all components to
    // filter out callbacks even if the respective component is only
    // analyzed later.
    AbstractCallbackAnalyzer jimpleClass =
        callbackClasses == null
            ? new DefaultCallbackAnalyzer(config, entryPointClasses, callbackFile)
            : new DefaultCallbackAnalyzer(config, entryPointClasses, callbackClasses);
    if (valueProvider != null) {
      jimpleClass.setValueProvider(valueProvider);
    }
    jimpleClass.addCallbackFilter(new AlienHostComponentFilter(entrypoints));
    jimpleClass.addCallbackFilter(new ApplicationCallbackFilter(entrypoints));
    jimpleClass.collectCallbackMethods();

    // Find the user-defined sources in the layout XML files. This
    // only needs to be done once, but is a Soot phase.
    lfp.parseLayoutFile(config.getAnalysisFileConfig().getTargetAPKFile());

    // Watch the callback collection algorithm's memory consumption
    FlowDroidMemoryWatcher memoryWatcher = null;
    FlowDroidTimeoutWatcher timeoutWatcher = null;
    if (jimpleClass instanceof IMemoryBoundedSolver) {
      memoryWatcher = new FlowDroidMemoryWatcher();
      memoryWatcher.addSolver((IMemoryBoundedSolver) jimpleClass);

      // Make sure that we don't spend too much time in the callback
      // analysis
      if (callbackConfig.getCallbackAnalysisTimeout() > 0) {
        timeoutWatcher = new FlowDroidTimeoutWatcher(callbackConfig.getCallbackAnalysisTimeout());
        timeoutWatcher.addSolver((IMemoryBoundedSolver) jimpleClass);
        timeoutWatcher.start();
      }
    }

    try {
      int depthIdx = 0;
      boolean hasChanged = true;
      boolean isInitial = true;
      while (hasChanged) {
        hasChanged = false;

        // Check whether the solver has been aborted in the meantime
        if (jimpleClass instanceof IMemoryBoundedSolver) {
          if (((IMemoryBoundedSolver) jimpleClass).isKilled()) {
            break;
          }
        }

        // Create the new iteration of the main method
        createMainMethod(component);

        // Since the gerenation of the main method can take some time,
        // we check again whether we need to stop.
        if (jimpleClass instanceof IMemoryBoundedSolver) {
          if (((IMemoryBoundedSolver) jimpleClass).isKilled()) {
            break;
          }
        }

        if (!isInitial) {
          // Reset the callgraph
          releaseCallgraph();

          // We only want to parse the layout files once
          PackManager.v().getPack("wjtp").remove("wjtp.lfp");
        }
        isInitial = false;

        // Run the soot-based operations
        constructCallgraphInternal();
        PackManager.v().getPack("wjtp").apply();

        // Creating all callgraph takes time and memory. Check whether
        // the
        // solver has been aborted in the meantime
        if (jimpleClass instanceof IMemoryBoundedSolver) {
          if (((IMemoryBoundedSolver) jimpleClass).isKilled()) {
            logger.warn("Aborted callback collection because of low memory");
            break;
          }
        }

        // Collect the results of the soot-based phases
        if (this.callbackMethods.putAll(jimpleClass.getCallbackMethods())) {
          hasChanged = true;
        }

        if (entrypoints.addAll(jimpleClass.getDynamicManifestComponents())) {
          hasChanged = true;
        }

        // Collect the XML-based callback methods
        if (collectXmlBasedCallbackMethods(lfp, jimpleClass)) {
          hasChanged = true;
        }

        // Avoid callback overruns. If we are beyond the callback limit
        // for one entry point, we may not collect any further callbacks
        // for that entry point.
        if (callbackConfig.getMaxCallbacksPerComponent() > 0) {
          for (Iterator<SootClass> componentIt = this.callbackMethods.keySet().iterator();
              componentIt.hasNext(); ) {
            SootClass callbackComponent = componentIt.next();
            if (this.callbackMethods.get(callbackComponent).size()
                > callbackConfig.getMaxCallbacksPerComponent()) {
              componentIt.remove();
              jimpleClass.excludeEntryPoint(callbackComponent);
            }
          }
        }

        // Check depth limiting
        depthIdx++;
        if (callbackConfig.getMaxAnalysisCallbackDepth() > 0
            && depthIdx >= callbackConfig.getMaxAnalysisCallbackDepth()) {
          break;
        }

        // If we work with an existing callgraph, the callgraph never
        // changes and thus it doesn't make any sense to go multiple
        // rounds
        if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingCallgraph) {
          break;
        }
      }
    } finally {
      // Shut down the watchers
      if (timeoutWatcher != null) {
        timeoutWatcher.stop();
      }
      if (memoryWatcher != null) {
        memoryWatcher.close();
      }
    }

    // Filter out callbacks that belong to fragments that are not used by
    // the host activity
    AlienFragmentFilter fragmentFilter = new AlienFragmentFilter(invertMap(fragmentClasses));
    fragmentFilter.reset();
    for (Iterator<Pair<SootClass, CallbackDefinition>> cbIt = this.callbackMethods.iterator();
        cbIt.hasNext(); ) {
      Pair<SootClass, CallbackDefinition> pair = cbIt.next();

      // Check whether the filter accepts the given mapping
      if (!fragmentFilter.accepts(pair.getO1(), pair.getO2().getTargetMethod())) {
        cbIt.remove();
      } else if (!fragmentFilter.accepts(
          pair.getO1(), pair.getO2().getTargetMethod().getDeclaringClass())) {
        cbIt.remove();
      }
    }

    // Avoid callback overruns
    if (callbackConfig.getMaxCallbacksPerComponent() > 0) {
      for (Iterator<SootClass> componentIt = this.callbackMethods.keySet().iterator();
          componentIt.hasNext(); ) {
        SootClass callbackComponent = componentIt.next();
        if (this.callbackMethods.get(callbackComponent).size()
            > callbackConfig.getMaxCallbacksPerComponent()) {
          componentIt.remove();
        }
      }
    }

    // Make sure that we don't retain any weird Soot phases
    PackManager.v().getPack("wjtp").remove("wjtp.lfp");
    PackManager.v().getPack("wjtp").remove("wjtp.ajc");

    // Warn the user if we had to abort the callback analysis early
    boolean abortedEarly = false;
    if (jimpleClass instanceof IMemoryBoundedSolver) {
      if (((IMemoryBoundedSolver) jimpleClass).isKilled()) {
        logger.warn("Callback analysis aborted early due to time or memory exhaustion");
        abortedEarly = true;
      }
    }
    if (!abortedEarly) {
      logger.info("Callback analysis terminated normally");
    }
  }

  /**
   * Releases the callgraph and all intermediate objects associated with it
   */
  private void releaseCallgraph() {
    // If we are configured to use an existing callgraph, we may not release
    // it
    if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingCallgraph) {
      return;
    }

    Scene.v().releaseCallGraph();
    Scene.v().releasePointsToAnalysis();
    Scene.v().releaseReachableMethods();
    G.v().resetSpark();
  }

  /**
   * Creates the main method based on the current callback information, injects it into the Soot
   * scene.
   *
   * @param component class name of a component to create a main method containing only that
   * component, or null to create main method for all components
   */
  private void createMainMethod(SootClass component) {
    // There is no need to create a main method if we don't want to generate
    // a callgraph
    if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingCallgraph) {
      return;
    }

    // Always update the entry point creator to reflect the newest set
    // of callback methods
    dummyMainMethod = createEntryPointCreator(component).createDummyMain();
    Scene.v().setEntryPoints(Collections.singletonList(dummyMainMethod));
    if (!dummyMainMethod.getDeclaringClass().isInScene()) {
      Scene.v().addClass(dummyMainMethod.getDeclaringClass());
    }

    // addClass() declares the given class as a library class. We need to
    // fix this.
    dummyMainMethod.getDeclaringClass().setApplicationClass();
  }

  /**
   * Creates the {@link AndroidEntryPointCreator} instance which will later create the dummy main
   * method for the analysis
   *
   * @param component The single component to include in the dummy main method. Pass null to include
   * all components in the dummy main method.
   * @return The {@link AndroidEntryPointCreator} responsible for generating the dummy main method
   */
  private AndroidEntryPointCreator createEntryPointCreator(SootClass component) {
    Set<SootClass> components = getComponentsToAnalyze(component);
    AndroidEntryPointCreator entryPointCreator = new AndroidEntryPointCreator(components);

    MultiMap<SootClass, SootMethod> callbackMethodSigs = new HashMultiMap<>();
    if (component == null) {
      // Get all callbacks for all components
      for (SootClass sc : this.callbackMethods.keySet()) {
        Set<CallbackDefinition> callbackDefs = this.callbackMethods.get(sc);
        if (callbackDefs != null) {
          for (CallbackDefinition cd : callbackDefs) {
            callbackMethodSigs.put(sc, cd.getTargetMethod());
          }
        }
      }
    } else {
      // Get the callbacks for the current component only
      for (SootClass sc : components) {
        Set<CallbackDefinition> callbackDefs = this.callbackMethods.get(sc);
        if (callbackDefs != null) {
          for (CallbackDefinition cd : callbackDefs) {
            callbackMethodSigs.put(sc, cd.getTargetMethod());
          }
        }
      }
    }
    entryPointCreator.setCallbackFunctions(callbackMethodSigs);
    entryPointCreator.setFragments(fragmentClasses);
    return entryPointCreator;
  }

  /**
   * Gets the components to analyze. If the given component is not null, we assume that only this
   * component and the application class (if any) shall be analyzed. Otherwise, all components are
   * to be analyzed.
   *
   * @param component A component class name to only analyze this class and the application class
   * (if any), or null to analyze all classes.
   * @return The set of classes to analyze
   */
  private Set<SootClass> getComponentsToAnalyze(SootClass component) {
    if (component == null) {
      return this.entrypoints;
    } else {
      // We always analyze the application class together with each
      // component
      // as there might be interactions between the two
      Set<SootClass> components = new HashSet<>(2);
      components.add(component);

      String applictionName = manifest.getApplicationName();
      if (applictionName != null && !applictionName.isEmpty()) {
        components.add(Scene.v().getSootClassUnsafe(applictionName));
      }
      return components;
    }
  }

  /**
   * Triggers the callgraph construction in Soot
   */
  private void constructCallgraphInternal() {
    // If we are configured to use an existing callgraph, we may not replace
    // it
    if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingCallgraph) {
      return;
    }

    // Do we need ICC instrumentation?
    IccInstrumenter iccInstrumenter = null;
    if (config.getIccConfig().isIccEnabled()) {
      iccInstrumenter = new IccInstrumenter(config.getIccConfig().getIccModel());
      iccInstrumenter.onBeforeCallgraphConstruction();

      // To support Messenger-based ICC
      MessengerInstrumenter msgInstrumenter = new MessengerInstrumenter();
      msgInstrumenter.onBeforeCallgraphConstruction();
    }

    // Run the preprocessors
    for (PreAnalysisHandler handler : this.preprocessors) {
      handler.onBeforeCallgraphConstruction();
    }

    // Make sure that we don't have any weird leftovers
    releaseCallgraph();

    // Construct the actual callgraph
    logger.info("Constructing the callgraph...");
    PackManager.v().getPack("cg").apply();

    // ICC instrumentation
    if (iccInstrumenter != null) {
      iccInstrumenter.onAfterCallgraphConstruction();
    }

    // Run the preprocessors
    for (PreAnalysisHandler handler : this.preprocessors) {
      handler.onAfterCallgraphConstruction();
    }

    // Make sure that we have a hierarchy
    Scene.v().getOrMakeFastHierarchy();
  }

  /**
   * Collects the XML-based callback methods, e.g., Button.onClick() declared in layout XML files
   *
   * @param lfp The layout file parser
   * @param jimpleClass The analysis class that gives us a mapping between layout IDs and
   * components
   * @return True if at least one new callback method has been added, otherwise false
   */
  private boolean collectXmlBasedCallbackMethods(
      LayoutFileParser lfp, AbstractCallbackAnalyzer jimpleClass) {
    SootMethod smViewOnClick =
        Scene.v()
            .grabMethod("<android.view.View$OnClickListener: void onClick(android.view.View)>");

    // Collect the XML-based callback methods
    boolean hasNewCallback = false;
    for (final SootClass callbackClass : jimpleClass.getLayoutClasses().keySet()) {
      if (jimpleClass.isExcludedEntryPoint(callbackClass)) {
        continue;
      }

      Set<Integer> classIds = jimpleClass.getLayoutClasses().get(callbackClass);
      for (Integer classId : classIds) {
        AbstractResource resource = this.resources.findResource(classId);
        if (resource instanceof StringResource) {
          final String layoutFileName = ((StringResource) resource).getValue();

          // Add the callback methods for the given class
          Set<String> callbackMethods = lfp.getCallbackMethods().get(layoutFileName);
          if (callbackMethods != null) {
            for (String methodName : callbackMethods) {
              final String subSig = "void " + methodName + "(android.view.View)";

              // The callback may be declared directly in the
              // class or in one of the superclasses
              SootClass currentClass = callbackClass;
              while (true) {
                SootMethod callbackMethod = currentClass.getMethodUnsafe(subSig);
                if (callbackMethod != null) {
                  if (this.callbackMethods.put(
                      callbackClass,
                      new CallbackDefinition(callbackMethod, smViewOnClick, CallbackType.Widget))) {
                    hasNewCallback = true;
                  }
                  break;
                }
                if (!currentClass.hasSuperclass()) {
                  logger.error(
                      String.format(
                          "Callback method %s not found in class %s",
                          methodName, callbackClass.getName()));
                  break;
                }
                currentClass = currentClass.getSuperclass();
              }
            }
          }

          // Add the fragments for this class
          Set<SootClass> fragments = lfp.getFragments().get(layoutFileName);
          if (fragments != null) {
            for (SootClass fragment : fragments) {
              if (fragmentClasses.put(callbackClass, fragment)) {
                hasNewCallback = true;
              }
            }
          }

          // For user-defined views, we need to emulate their
          // callbacks
          Set<LayoutControl> controls = lfp.getUserControls().get(layoutFileName);
          if (controls != null) {
            for (LayoutControl lc : controls) {
              if (!SystemClassHandler.isClassInSystemPackage(lc.getViewClass().getName())) {
                registerCallbackMethodsForView(callbackClass, lc);
              }
            }
          }
        } else {
          logger.error("Unexpected resource type for layout class");
        }
      }
    }

    // Collect the fragments, merge the fragments created in the code with
    // those declared in Xml files
    if (fragmentClasses.putAll(jimpleClass.getFragmentClasses())) // Fragments
    // declared
    // in
    // code
    {
      hasNewCallback = true;
    }

    return hasNewCallback;
  }

  /**
   * Registers the callback methods in the given layout control so that they are included in the
   * dummy main method
   *
   * @param callbackClass The class with which to associate the layout callbacks
   * @param lc The layout control whose callbacks are to be associated with the given class
   */
  private void registerCallbackMethodsForView(SootClass callbackClass, LayoutControl lc) {
    // Ignore system classes
    if (SystemClassHandler.isClassInSystemPackage(callbackClass.getName())) {
      return;
    }

    // Get common Android classes
    if (scView == null) {
      scView = Scene.v().getSootClass("android.view.View");
    }

    // Check whether the current class is actually a view
    if (!Scene.v()
        .getOrMakeFastHierarchy()
        .canStoreType(lc.getViewClass().getType(), scView.getType())) {
      return;
    }

    // There are also some classes that implement interesting callback
    // methods.
    // We model this as follows: Whenever the user overwrites a method in an
    // Android OS class, we treat it as a potential callback.
    SootClass sc = lc.getViewClass();
    Map<String, SootMethod> systemMethods = new HashMap<>(10000);
    for (SootClass parentClass : Scene.v().getActiveHierarchy().getSuperclassesOf(sc)) {
      if (parentClass.getName().startsWith("android.")) {
        for (SootMethod sm : parentClass.getMethods()) {
          if (!sm.isConstructor()) {
            systemMethods.put(sm.getSubSignature(), sm);
          }
        }
      }
    }

    // Scan for methods that overwrite parent class methods
    for (SootMethod sm : sc.getMethods()) {
      if (!sm.isConstructor()) {
        SootMethod parentMethod = systemMethods.get(sm.getSubSignature());
        if (parentMethod != null)
        // This is a real callback method
        {
          this.callbackMethods.put(
              callbackClass, new CallbackDefinition(sm, parentMethod, CallbackType.Widget));
        }
      }
    }
  }

  /**
   * Gets the set of sources loaded into FlowDroid. These are the sources as they are defined
   * through the SourceSinkManager.
   *
   * @return The set of sources loaded into FlowDroid
   */
  public Set<SourceSinkDefinition> getTaintSources() {
    return this.taintTrackingSourceSinkProvider == null ? null
        : this.taintTrackingSourceSinkProvider.getSources();
  }

  /**
   * Gets the set of sinks loaded into FlowDroid These are the sinks as they are defined through the
   * SourceSinkManager.
   *
   * @return The set of sinks loaded into FlowDroid
   */
  public Set<SourceSinkDefinition> getTaintSinks() {
    return this.taintTrackingSourceSinkProvider == null ? null
        : this.taintTrackingSourceSinkProvider.getSinks();
  }

  /**
   * Creates an instance of {@link ISourceSinkManager} that defines what FlowDorid shall consider as
   * a source or sink, respectively.
   *
   * @param lfp The parser that handles the layout XML files
   * @param callbacks The callbacks that have been collected so far
   * @return The new source sink manager
   */
  private ISourceSinkManager createSourceSinkManager(
      LayoutFileParser lfp, Set<CallbackDefinition> callbacks,
      ISourceSinkDefinitionProvider sourceSinkDefinitionProvider) {
    AccessPathBasedSourceSinkManager sourceSinkManager =
        new AccessPathBasedSourceSinkManager(
            sourceSinkDefinitionProvider.getSources(),
            sourceSinkDefinitionProvider.getSinks(),
            callbacks,
            config,
            lfp == null ? null : lfp.getUserControlsByID());

    sourceSinkManager.setAppPackageName(this.manifest.getPackageName());
    sourceSinkManager.setResourcePackages(this.resources.getPackages());
    return sourceSinkManager;
  }

  /**
   * Inverts the given {@link MultiMap}. The keys become values and vice versa
   *
   * @param original The map to invert
   * @return An inverted copy of the given map
   */
  private <K, V> MultiMap<K, V> invertMap(MultiMap<V, K> original) {
    MultiMap<K, V> newTag = new HashMultiMap<>();
    for (V key : original.keySet()) {
      for (K value : original.get(key)) {
        newTag.put(value, key);
      }
    }
    return newTag;
  }

  /**
   * Internal class to leverage internal variables inside ApkAnalyzer while running infoflow
   * analysis.
   */
  private static class InternalInfoflowInst extends FCInfoflow {

    /**
     * Creates a new instance of the FCInfoflow class for analyzing Android APK files.
     *
     * @param androidPath If forceAndroidJar is false, this is the base directory of the platform
     * files in the Android SDK. If forceAndroidJar is true, this is the full path of a single
     * android.jar file.
     * @param forceAndroidJar True if a single platform JAR file shall be forced, false if Soot
     * shall pick the appropriate platform version
     * @param icfgFactory The interprocedural CFG to be used by the InfoFlowProblem
     */
    public InternalInfoflowInst(
        String androidPath, boolean forceAndroidJar, BiDirICFGFactory icfgFactory) {
      super(androidPath, forceAndroidJar, icfgFactory);
    }

    public void runAnalysis(final ISourceSinkManager sourcesSinks, SootMethod entryPoint) {
      this.dummyMainMethod = entryPoint;
      super.runAnalysis(sourcesSinks);
    }
  }

  /**
   * Class for aggregating the data flow results obtained through multiple runs of the data flow
   * solver.
   *
   * @author Steven Arzt
   */
  private static class InfoflowResultsHandler implements ResultsAvailableHandler {

    private final Logger logger = LoggerFactory.getLogger(InfoflowResultsHandler.class);

    private final InfoflowResults aggregatedResults = new InfoflowResults();
    private InfoflowResults lastResults = null;
    private IInfoflowCFG lastICFG = null;

    @Override
    public void onResultsAvailable(IInfoflowCFG icfg, InfoflowResults results) {
      this.aggregatedResults.addAll(results);
      this.lastResults = results;
      this.lastICFG = icfg;
    }

    /**
     * Gets all data flow results aggregated so far
     *
     * @return All data flow results aggregated so far
     */
    public InfoflowResults getAggregatedResults() {
      return this.aggregatedResults;
    }

    /**
     * Gets the total number of source-to-sink connections from the last partial result that was
     * added to this aggregator
     *
     * @return The results from the last run of the data flow analysis
     */
    public InfoflowResults getLastResults() {
      return this.lastResults;
    }

    /**
     * Clears the stored result set from the last data flow run
     */
    public void clearLastResults() {
      this.lastResults = null;
      this.lastICFG = null;
    }

    /**
     * Gets the ICFG that was returned together with the last set of data flow results
     *
     * @return The ICFG that was returned together with the last set of data flow results
     */
    public IInfoflowCFG getLastICFG() {
      return this.lastICFG;
    }
  }

}
