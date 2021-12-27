package com.flowcog.cmd;

import com.flowcog.android.analyzer.ApkAnalyzer;
import com.flowcog.cmd.helper.ConfigurationHelper;
import com.flowcog.cmd.helper.TaintWrapperHelper;
import com.flowcog.exception.AbortAnalysisException;
import com.flowcog.result.statistics.FCResultsStatistics;
import java.util.Date;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;

/**
 * Main class for running FlowDroid from the command-line
 *
 * @author Steven Arzt
 */
public class Main {

  private static Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.run(args);
  }

  private void run(String[] args) throws Exception {
    long startTime = System.currentTimeMillis();
    // Record the start time
    FCResultsStatistics.v().setStartDatetime(new Date(startTime));

    ConfigurationHelper configurationHelper = new ConfigurationHelper();
    try {
      CommandLine cmd = configurationHelper.parseArgs(args);
      InfoflowAndroidConfiguration config = configurationHelper.parseCommandLineOptions(cmd);

      // Initialize the taint wrapper
      TaintWrapperHelper taintWrapperHelper = new TaintWrapperHelper();
      ITaintPropagationWrapper taintWrapper = taintWrapperHelper.initializeTaintWrapper(cmd);

      // Analyzer initialize
      ApkAnalyzer analyzer = new ApkAnalyzer(config);
      analyzer.setTaintWrapper(taintWrapper);
      // Start the data flow analysis
      analyzer.run();
//      analyzer.runTest();
    } catch (AbortAnalysisException e) {
      // Silently return
    } catch (ParseException e) {
      System.exit(1);
    } catch (Exception e) {
      logger.error("The data flow analysis has failed. Error message: {}", e);
    }
    long endTime = System.currentTimeMillis();
    FCResultsStatistics.v().setTotalTime(endTime - startTime);
    logger.info("[PRINT STATISTICS]");
    logger.info(FCResultsStatistics.v().toJson());
    logger.info("[PRINT STATISTICS END]");
    logger.info("Runtime: " + (endTime - startTime) + "ms");
  }

}

