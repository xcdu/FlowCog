package com.flowcog.cmd.helper;

import com.flowcog.exception.AbortAnalysisException;
import java.io.IOException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.AliasingAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode;
import soot.jimple.infoflow.InfoflowConfiguration.DataFlowSolver;
import soot.jimple.infoflow.InfoflowConfiguration.ImplicitFlowMode;
import soot.jimple.infoflow.InfoflowConfiguration.PathBuildingAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.PathReconstructionMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackSourceMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.LayoutMatchingMode;
import soot.jimple.infoflow.android.config.XMLConfigurationParser;

@SuppressWarnings("WeakerAccess")
public class ConfigurationHelper {
  // Files
  public static final String OPTION_CONFIG_FILE = "c";
  public static final String OPTION_APK_FILE = "a";
  public static final String OPTION_PLATFORMS_DIR = "p";
  public static final String OPTION_SOURCES_SINKS_FILE = "s";
  public static final String OPTION_OUTPUT_FILE = "o";
  // Timeouts
  public static final String OPTION_TIMEOUT = "dt";
  public static final String OPTION_CALLBACK_TIMEOUT = "ct";
  public static final String OPTION_RESULT_TIMEOUT = "rt";
  // Optional features
  public static final String OPTION_NO_STATIC_FLOWS = "ns";
  public static final String OPTION_NO_CALLBACK_ANALYSIS = "nc";
  public static final String OPTION_NO_EXCEPTIONAL_FLOWS = "ne";
  public static final String OPTION_NO_TYPE_CHECKING = "nt";
  public static final String OPTION_REFLECTION = "r";
  // Taint wrapper
  public static final String OPTION_TAINT_WRAPPER = "tw";
  public static final String OPTION_TAINT_WRAPPER_FILE = "t";
  // Individual settings
  public static final String OPTION_ACCESS_PATH_LENGTH = "al";
  public static final String OPTION_FLOW_INSENSITIVE_ALIASING = "af";
  public static final String OPTION_COMPUTE_PATHS = "cp";
  public static final String OPTION_ONE_SOURCE = "os";
  public static final String OPTION_ONE_COMPONENT = "ot";
  public static final String OPTION_SEQUENTIAL_PATHS = "sp";
  public static final String OPTION_LOG_SOURCES_SINKS = "ls";
  public static final String OPTION_MERGE_DEX_FILES = "d";
  public static final String OPTION_SINGLE_JOIN_POINT = "sa";
  public static final String OPTION_MAX_CALLBACKS_COMPONENT = "mc";
  public static final String OPTION_MAX_CALLBACKS_DEPTH = "md";
  public static final String OPTION_PATH_SPECIFIC_RESULTS = "ps";
  // Inter-component communication
  public static final String OPTION_ICC_MODEL = "im";
  public static final String OPTION_ICC_NO_PURIFY = "np";
  // Modes and algorithms
  public static final String OPTION_CALLGRAPH_ALGO = "cg";
  public static final String OPTION_LAYOUT_MODE = "l";
  public static final String OPTION_PATH_RECONSTRUCTION_ALGO = "pa";
  public static final String OPTION_CALLBACK_ANALYZER = "ca";
  public static final String OPTION_DATA_FLOW_SOLVER = "ds";
  public static final String OPTION_ALIAS_ALGO = "aa";
  public static final String OPTION_CODE_ELIMINATION_MODE = "ce";
  public static final String OPTION_CALLBACK_SOURCE_MODE = "cs";
  public static final String OPTION_PATH_RECONSTRUCTION_MODE = "pr";
  public static final String OPTION_IMPLICIT_FLOW_MODE = "i";
  private static final Options options = new Options();
  private static final HelpFormatter helpFormatter = new HelpFormatter();

  public ConfigurationHelper() {
    initializeCommandLineOptions();
  }

  private static CallgraphAlgorithm parseCallgraphAlgorithm(String algo) {
    if (algo.equalsIgnoreCase("AUTO"))
      return CallgraphAlgorithm.AutomaticSelection;
    else if (algo.equalsIgnoreCase("CHA"))
      return CallgraphAlgorithm.CHA;
    else if (algo.equalsIgnoreCase("VTA"))
      return CallgraphAlgorithm.VTA;
    else if (algo.equalsIgnoreCase("RTA"))
      return CallgraphAlgorithm.RTA;
    else if (algo.equalsIgnoreCase("SPARK"))
      return CallgraphAlgorithm.SPARK;
    else if (algo.equalsIgnoreCase("GEOM"))
      return CallgraphAlgorithm.GEOM;
    else {
      System.err.println(String.format("Invalid callgraph algorithm: %s", algo));
      throw new AbortAnalysisException();
    }
  }

  private static LayoutMatchingMode parseLayoutMatchingMode(String layoutMode) {
    if (layoutMode.equalsIgnoreCase("NONE"))
      return LayoutMatchingMode.NoMatch;
    else if (layoutMode.equalsIgnoreCase("PWD"))
      return LayoutMatchingMode.MatchSensitiveOnly;
    else if (layoutMode.equalsIgnoreCase("ALL"))
      return LayoutMatchingMode.MatchAll;
    else {
      System.err.println(String.format("Invalid layout matching mode: %s", layoutMode));
      throw new AbortAnalysisException();
    }
  }

  private static PathBuildingAlgorithm parsePathReconstructionAlgo(String pathAlgo) {
    if (pathAlgo.equalsIgnoreCase("CONTEXTSENSITIVE"))
      return PathBuildingAlgorithm.ContextSensitive;
    else if (pathAlgo.equalsIgnoreCase("CONTEXTINSENSITIVE"))
      return PathBuildingAlgorithm.ContextInsensitive;
    else if (pathAlgo.equalsIgnoreCase("SOURCESONLY"))
      return PathBuildingAlgorithm.ContextInsensitiveSourceFinder;
    else {
      System.err.println(String.format("Invalid path reconstruction algorithm: %s", pathAlgo));
      throw new AbortAnalysisException();
    }
  }

  private static CallbackAnalyzer parseCallbackAnalyzer(String callbackAnalyzer) {
    if (callbackAnalyzer.equalsIgnoreCase("DEFAULT"))
      return CallbackAnalyzer.Default;
    else if (callbackAnalyzer.equalsIgnoreCase("FAST"))
      return CallbackAnalyzer.Fast;
    else {
      System.err.println(String.format("Invalid callback analysis algorithm: %s", callbackAnalyzer));
      throw new AbortAnalysisException();
    }
  }

  private static DataFlowSolver parseDataFlowSolver(String solver) {
    if (solver.equalsIgnoreCase("CONTEXTFLOWSENSITIVE"))
      return DataFlowSolver.ContextFlowSensitive;
    else if (solver.equalsIgnoreCase("FLOWINSENSITIVE"))
      return DataFlowSolver.FlowInsensitive;
    else {
      System.err.println(String.format("Invalid data flow solver: %s", solver));
      throw new AbortAnalysisException();
    }
  }

  private static AliasingAlgorithm parseAliasAlgorithm(String aliasAlgo) {
    if (aliasAlgo.equalsIgnoreCase("NONE"))
      return AliasingAlgorithm.None;
    else if (aliasAlgo.equalsIgnoreCase("FLOWSENSITIVE"))
      return AliasingAlgorithm.FlowSensitive;
    else if (aliasAlgo.equalsIgnoreCase("PTSBASED"))
      return AliasingAlgorithm.PtsBased;
    else if (aliasAlgo.equalsIgnoreCase("LAZY"))
      return AliasingAlgorithm.Lazy;
    else {
      System.err.println(String.format("Invalid aliasing algorithm: %s", aliasAlgo));
      throw new AbortAnalysisException();
    }
  }

  private static CodeEliminationMode parseCodeEliminationMode(String eliminationMode) {
    if (eliminationMode.equalsIgnoreCase("NONE"))
      return CodeEliminationMode.NoCodeElimination;
    else if (eliminationMode.equalsIgnoreCase("PROPAGATECONSTS"))
      return CodeEliminationMode.PropagateConstants;
    else if (eliminationMode.equalsIgnoreCase("REMOVECODE"))
      return CodeEliminationMode.RemoveSideEffectFreeCode;
    else {
      System.err.println(String.format("Invalid code elimination mode: %s", eliminationMode));
      throw new AbortAnalysisException();
    }
  }

  private static CallbackSourceMode parseCallbackSourceMode(String callbackMode) {
    if (callbackMode.equalsIgnoreCase("NONE"))
      return CallbackSourceMode.NoParametersAsSources;
    else if (callbackMode.equalsIgnoreCase("ALL"))
      return CallbackSourceMode.AllParametersAsSources;
    else if (callbackMode.equalsIgnoreCase("SOURCELIST"))
      return CallbackSourceMode.SourceListOnly;
    else {
      System.err.println(String.format("Invalid callback source mode: %s", callbackMode));
      throw new AbortAnalysisException();
    }
  }

  private static PathReconstructionMode parsePathReconstructionMode(String pathReconstructionMode) {
    if (pathReconstructionMode.equalsIgnoreCase("NONE"))
      return PathReconstructionMode.NoPaths;
    else if (pathReconstructionMode.equalsIgnoreCase("FAST"))
      return PathReconstructionMode.Fast;
    else if (pathReconstructionMode.equalsIgnoreCase("PRECISE"))
      return PathReconstructionMode.Precise;
    else {
      System.err.println(String.format("Invalid path reconstruction mode: %s", pathReconstructionMode));
      throw new AbortAnalysisException();
    }
  }

  private static ImplicitFlowMode parseImplicitFlowMode(String implicitFlowMode) {
    if (implicitFlowMode.equalsIgnoreCase("NONE"))
      return ImplicitFlowMode.NoImplicitFlows;
    else if (implicitFlowMode.equalsIgnoreCase("ARRAYONLY"))
      return ImplicitFlowMode.ArrayAccesses;
    else if (implicitFlowMode.equalsIgnoreCase("ALL"))
      return ImplicitFlowMode.AllImplicitFlows;
    else {
      System.err.println(String.format("Invalid implicit flow mode: %s", implicitFlowMode));
      throw new AbortAnalysisException();
    }
  }

  private static int getIntOption(CommandLine cmd, String option) {
    String str = cmd.getOptionValue(option);
    if (str == null || str.isEmpty())
      return -1;
    else
      return Integer.parseInt(str);
  }

  /**
   * Loads the data flow configuration from the given file
   *
   * @param configFile
   *            The configuration file from which to load the data flow
   *            configuration
   * @return The loaded data flow configuration
   */
  private static InfoflowAndroidConfiguration loadConfigurationFile(String configFile) {
    try {
      InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
      XMLConfigurationParser.fromFile(configFile).parse(config);
      return config;
    } catch (IOException e) {
      System.err.println("Could not parse configuration file: " + e.getMessage());
      return null;
    }
  }

  /**
   * Initializes the set of available command-line options
   */
  private void initializeCommandLineOptions() {
    options.addOption("?", "help", false, "Print this help message");

    // Files
    options.addOption(OPTION_CONFIG_FILE, "configfile", true, "Use the given configuration file");
    options.addOption(OPTION_APK_FILE, "apkfile", true, "APK file to analyze");
    options.addOption(OPTION_PLATFORMS_DIR, "platformsdir", true,
        "Path to the platforms directory from the Android SDK");
    options.addOption(OPTION_SOURCES_SINKS_FILE, "sourcessinksfile", true, "Definition file for sources and sinks");
    options.addOption(OPTION_OUTPUT_FILE, "outputfile", true, "Output XML file for the discovered data flows");

    // Timeouts
    options.addOption(OPTION_TIMEOUT, "timeout", true, "Timeout for the main data flow analysis");
    options.addOption(OPTION_CALLBACK_TIMEOUT, "callbacktimeout", true,
        "Timeout for the callback collection phase");
    options.addOption(OPTION_RESULT_TIMEOUT, "resulttimeout", true, "Timeout for the result collection phase");

    // Optional features
    options.addOption(OPTION_NO_STATIC_FLOWS, "nostatic", false, "Do not track static data flows");
    options.addOption(OPTION_NO_CALLBACK_ANALYSIS, "nocallbacks", false, "Do not analyze Android callbacks");
    options.addOption(OPTION_NO_EXCEPTIONAL_FLOWS, "noexceptions", false,
        "Do not track taints across exceptional control flow edges");
    options.addOption(OPTION_NO_TYPE_CHECKING, "notypechecking", false,
        "Disable type checking during taint propagation");
    options.addOption(OPTION_REFLECTION, "enablereflection", false, "Enable support for reflective method calls");

    // Taint wrapper
    options.addOption(OPTION_TAINT_WRAPPER, "taintwrapper", true,
        "Use the specified taint wrapper algorithm (NONE, EASY, STUBDROID, MULTI)");
    options.addOption(OPTION_TAINT_WRAPPER_FILE, "taintwrapperfile", true, "Definition file for the taint wrapper");

    // Individual settings
    options.addOption(OPTION_ACCESS_PATH_LENGTH, "aplength", true, "Maximum access path length");
    options.addOption(OPTION_FLOW_INSENSITIVE_ALIASING, "aliasflowins", false,
        "Use a flow-insensitive alias analysis");
    options.addOption(OPTION_COMPUTE_PATHS, "paths", false,
        "Compute the taint propagation paths and not just source-to-sink connections. This is a shorthand notation for -pr fast.");
    options.addOption(OPTION_LOG_SOURCES_SINKS, "logsourcesandsinks", false,
        "Write the discovered sources and sinks to the log output");
    options.addOption("mt", "maxthreadnum", true, "Limit the maximum number of threads to the given value");
    options.addOption(OPTION_ONE_COMPONENT, "onecomponentatatime", false,
        "Analyze one Android component at a time");
    options.addOption(OPTION_ONE_SOURCE, "onesourceatatime", false, "Analyze one source at a time");
    options.addOption(OPTION_SEQUENTIAL_PATHS, "sequentialpathprocessing", false,
        "Process the result paths sequentially instead of in parallel");
    options.addOption(OPTION_SINGLE_JOIN_POINT, "singlejoinpointabstraction", false,
        "Only use a single abstraction at join points, i.e., do not support multiple sources for one value");
    options.addOption(OPTION_MAX_CALLBACKS_COMPONENT, "maxcallbackspercomponent", true,
        "Eliminate Android components that have more than the given number of callbacks");
    options.addOption(OPTION_MAX_CALLBACKS_DEPTH, "maxcallbacksdepth", true,
        "Only analyze callback chains up to the given depth");
    options.addOption(OPTION_MERGE_DEX_FILES, "mergedexfiles", false,
        "Merge all dex files in the given APK file into one analysis target");
    options.addOption(OPTION_PATH_SPECIFIC_RESULTS, "pathspecificresults", false,
        "Report different results for same source/sink pairs if they differ in their propagation paths");

    // Inter-component communication
    options.addOption(OPTION_ICC_MODEL, "iccmodel", true,
        "File containing the inter-component data flow model (ICC model)");
    options.addOption(OPTION_ICC_NO_PURIFY, "noiccresultspurify", false,
        "Do not purify the ICC results, i.e., do not remove simple flows that also have a corresponding ICC flow");

    // Modes and algorithms
    options.addOption(OPTION_CALLGRAPH_ALGO, "cgalgo", true,
        "Callgraph algorithm to use (AUTO, CHA, VTA, RTA, SPARK, GEOM)");
    options.addOption(OPTION_LAYOUT_MODE, "layoutmode", true,
        "Mode for considerung layout controls as sources (NONE, PWD, ALL)");
    options.addOption(OPTION_PATH_RECONSTRUCTION_ALGO, "pathalgo", true,
        "Use the specified algorithm for computing result paths (CONTEXTSENSITIVE, CONTEXTINSENSITIVE, SOURCESONLY)");
    options.addOption(OPTION_CALLBACK_ANALYZER, "callbackanalyzer", true,
        "Use the specified callback analyzer (DEFAULT, FAST)");
    options.addOption(OPTION_DATA_FLOW_SOLVER, "dataflowsolver", true,
        "Use the specified data flow solver (HEROS, CONTEXTFLOWSENSITIVE, FLOWINSENSITIVE)");
    options.addOption(OPTION_ALIAS_ALGO, "aliasalgo", true,
        "Use the specified aliasing algorithm (NONE, FLOWSENSITIVE, PTSBASED, LAZY)");
    options.addOption(OPTION_CODE_ELIMINATION_MODE, "codeelimination", true,
        "Use the specified code elimination algorithm (NONE, PROPAGATECONSTS, REMOVECODE)");
    options.addOption(OPTION_CALLBACK_SOURCE_MODE, "callbacksourcemode", true,
        "Use the specified mode for defining which callbacks introduce which sources (NONE, ALL, SOURCELIST)");
    options.addOption(OPTION_PATH_RECONSTRUCTION_MODE, "pathreconstructionmode", true,
        "Use the specified mode for reconstructing taint propagation paths (NONE, FAST, PRECISE).");
    options.addOption(OPTION_IMPLICIT_FLOW_MODE, "implicit", true,
        "Use the specified mode when processing implicit data flows (NONE, ARRAYONLY, ALL)");
  }

  /**
   * Parse the given String args and return CommandLine options
   * @param args
   *             The String args to parse
   */
  @SuppressWarnings("UnnecessaryLocalVariable")
  public CommandLine parseArgs(String[] args) throws ParseException{
    if (args.length == 0) {
      printHelp();
      throw new ParseException("No argument found");
    }
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    return cmd;
  }

  public InfoflowAndroidConfiguration parseCommandLineOptions(CommandLine cmd) {
    // Do we have a configuration file?
    String configFile = cmd.getOptionValue(OPTION_CONFIG_FILE);
    final InfoflowAndroidConfiguration config = configFile == null || configFile.isEmpty()
        ? new InfoflowAndroidConfiguration() : loadConfigurationFile(configFile);
    if (config == null) {
      System.err.println("TaintWrapper configuration file initializing failed.");
      throw new AbortAnalysisException();
    }
    return parseCommandLineOptions(cmd, config);
  }

  /**
   * Parses the given command-line options and fills the given configuration
   * object accordingly
   *
   * @param cmd
   *            The command line to parse
   * @param config
   *            The configuration object to fill
   */
  private InfoflowAndroidConfiguration parseCommandLineOptions(CommandLine cmd, InfoflowAndroidConfiguration config) {
    // Files
    {
      String apkFile = cmd.getOptionValue(OPTION_APK_FILE);
      if (apkFile != null && !apkFile.isEmpty())
        config.getAnalysisFileConfig().setTargetAPKFile(apkFile);
    }
    {
      String platformsDir = cmd.getOptionValue(OPTION_PLATFORMS_DIR);
      if (platformsDir != null && !platformsDir.isEmpty())
        config.getAnalysisFileConfig().setAndroidPlatformDir(platformsDir);
    }
    {
      String sourcesSinks = cmd.getOptionValue(OPTION_SOURCES_SINKS_FILE);
      if (sourcesSinks != null && !sourcesSinks.isEmpty())
        config.getAnalysisFileConfig().setSourceSinkFile(sourcesSinks);
    }
    {
      String outputFile = cmd.getOptionValue(OPTION_OUTPUT_FILE);
      if (outputFile != null && !outputFile.isEmpty())
        config.getAnalysisFileConfig().setOutputFile(outputFile);
    }

    // Timeouts
    {
      int timeout = getIntOption(cmd, OPTION_TIMEOUT);
      config.setDataFlowTimeout(timeout);
    }
    {
      int timeout = getIntOption(cmd, OPTION_CALLBACK_TIMEOUT);
      config.getCallbackConfig().setCallbackAnalysisTimeout(timeout);
    }
    {
      int timeout = getIntOption(cmd, OPTION_RESULT_TIMEOUT);
      config.getPathConfiguration().setPathReconstructionTimeout(timeout);
    }

    // Optional features
    if (cmd.hasOption(OPTION_NO_STATIC_FLOWS))
      config.setEnableStaticFieldTracking(false);
    if (cmd.hasOption(OPTION_NO_CALLBACK_ANALYSIS))
      config.getCallbackConfig().setEnableCallbacks(false);
    if (cmd.hasOption(OPTION_NO_EXCEPTIONAL_FLOWS))
      config.setEnableExceptionTracking(false);
    if (cmd.hasOption(OPTION_NO_TYPE_CHECKING))
      config.setEnableTypeChecking(false);
    if (cmd.hasOption(OPTION_REFLECTION))
      config.setEnableRefection(true);

    // Individual settings
    {
      int aplength = getIntOption(cmd, OPTION_ACCESS_PATH_LENGTH);
      if (aplength >= 0)
        config.setAccessPathLength(aplength);
    }
    if (cmd.hasOption(OPTION_FLOW_INSENSITIVE_ALIASING))
      config.setFlowSensitiveAliasing(false);
    if (cmd.hasOption(OPTION_COMPUTE_PATHS))
      config.getPathConfiguration().setPathReconstructionMode(PathReconstructionMode.Fast);
    if (cmd.hasOption(OPTION_ONE_SOURCE))
      config.setOneSourceAtATime(true);
    if (cmd.hasOption(OPTION_ONE_COMPONENT))
      config.setOneComponentAtATime(true);
    if (cmd.hasOption(OPTION_SEQUENTIAL_PATHS))
      config.getPathConfiguration().setSequentialPathProcessing(true);
    if (cmd.hasOption(OPTION_LOG_SOURCES_SINKS))
      config.setLogSourcesAndSinks(true);
    if (cmd.hasOption(OPTION_MERGE_DEX_FILES))
      config.setMergeDexFiles(true);
    if (cmd.hasOption(OPTION_PATH_SPECIFIC_RESULTS))
      InfoflowConfiguration.setPathAgnosticResults(false);
    if (cmd.hasOption(OPTION_SINGLE_JOIN_POINT))
      config.getSolverConfiguration().setSingleJoinPointAbstraction(true);
    {
      int maxCallbacks = getIntOption(cmd, OPTION_MAX_CALLBACKS_COMPONENT);
      if (maxCallbacks >= 0)
        config.getCallbackConfig().setMaxCallbacksPerComponent(maxCallbacks);
    }
    {
      int maxDepth = getIntOption(cmd, OPTION_MAX_CALLBACKS_DEPTH);
      if (maxDepth >= 0)
        config.getCallbackConfig().setMaxAnalysisCallbackDepth(maxDepth);
    }

    // Inter-component communication
    if (cmd.hasOption(OPTION_ICC_NO_PURIFY))
      config.getIccConfig().setIccResultsPurify(false);
    {
      String iccModel = cmd.getOptionValue(OPTION_ICC_MODEL);
      if (iccModel != null && !iccModel.isEmpty())
        config.getIccConfig().setIccModel(iccModel);
    }

    // Modes and algorithms
    {
      String cgalgo = cmd.getOptionValue(OPTION_CALLGRAPH_ALGO);
      if (cgalgo != null && !cgalgo.isEmpty())
        config.setCallgraphAlgorithm(parseCallgraphAlgorithm(cgalgo));
    }
    {
      String layoutMode = cmd.getOptionValue(OPTION_LAYOUT_MODE);
      if (layoutMode != null && !layoutMode.isEmpty())
        config.getSourceSinkConfig().setLayoutMatchingMode(parseLayoutMatchingMode(layoutMode));
    }
    {
      String pathAlgo = cmd.getOptionValue(OPTION_PATH_RECONSTRUCTION_ALGO);
      if (pathAlgo != null && !pathAlgo.isEmpty())
        config.getPathConfiguration().setPathBuildingAlgorithm(parsePathReconstructionAlgo(pathAlgo));
    }
    {
      String callbackAnalyzer = cmd.getOptionValue(OPTION_CALLBACK_ANALYZER);
      if (callbackAnalyzer != null && !callbackAnalyzer.isEmpty())
        config.getCallbackConfig().setCallbackAnalyzer(parseCallbackAnalyzer(callbackAnalyzer));
    }
    {
      String solver = cmd.getOptionValue(OPTION_DATA_FLOW_SOLVER);
      if (solver != null && !solver.isEmpty())
        config.getSolverConfiguration().setDataFlowSolver(parseDataFlowSolver(solver));
    }
    {
      String aliasAlgo = cmd.getOptionValue(OPTION_ALIAS_ALGO);
      if (aliasAlgo != null && !aliasAlgo.isEmpty())
        config.setAliasingAlgorithm(parseAliasAlgorithm(aliasAlgo));
    }
    {
      String eliminationMode = cmd.getOptionValue(OPTION_CODE_ELIMINATION_MODE);
      if (eliminationMode != null && !eliminationMode.isEmpty())
        config.setCodeEliminationMode(parseCodeEliminationMode(eliminationMode));
    }
    {
      String callbackMode = cmd.getOptionValue(OPTION_CALLBACK_SOURCE_MODE);
      if (callbackMode != null && !callbackMode.isEmpty())
        config.getSourceSinkConfig().setCallbackSourceMode(parseCallbackSourceMode(callbackMode));
    }
    {
      String pathMode = cmd.getOptionValue(OPTION_PATH_RECONSTRUCTION_MODE);
      if (pathMode != null && !pathMode.isEmpty())
        config.getPathConfiguration().setPathReconstructionMode(parsePathReconstructionMode(pathMode));
    }
    {
      String implicitMode = cmd.getOptionValue(OPTION_IMPLICIT_FLOW_MODE);
      if (implicitMode != null && !implicitMode.isEmpty())
        config.setImplicitFlowMode(parseImplicitFlowMode(implicitMode));
    }
    return config;
  }

  public void printHelp() {
    helpFormatter.printHelp("flowcog.cmd [OPTIONS]", options);
  }
}
