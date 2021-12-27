package com.flowcog.cmd.helper;

import static com.flowcog.cmd.helper.ConfigurationHelper.OPTION_TAINT_WRAPPER;
import static com.flowcog.cmd.helper.ConfigurationHelper.OPTION_TAINT_WRAPPER_FILE;

import com.flowcog.exception.AbortAnalysisException;
import java.io.File;
import java.util.Arrays;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import soot.jimple.infoflow.methodSummary.data.provider.LazySummaryProvider;
import soot.jimple.infoflow.methodSummary.taintWrappers.SummaryTaintWrapper;
import soot.jimple.infoflow.methodSummary.taintWrappers.TaintWrapperFactory;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.taintWrappers.TaintWrapperSet;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class TaintWrapperHelper {

  /**
   * Initializes the taint wrapper based on the command-line parameters
   *
   * @param cmd The command-line parameters
   * @return The taint wrapper to use for the data flow analysis, or null in case no taint wrapper
   * shall be used
   */
  public ITaintPropagationWrapper initializeTaintWrapper(CommandLine cmd) throws Exception {
    // Get the definition file(s) for the taint wrapper
    String[] definitionFiles = cmd.getOptionValues(OPTION_TAINT_WRAPPER_FILE);

    // If the user did not specify a taint wrapper, but definition files, we
    // use the most
    // permissive option
    String taintWrapper = cmd.getOptionValue(OPTION_TAINT_WRAPPER);
    if (taintWrapper == null || taintWrapper.isEmpty()) {
      if (definitionFiles != null && definitionFiles.length > 0) {
        taintWrapper = "multi";
      } else {
        // If we don't have a taint wrapper configuration, we use the
        // default
        taintWrapper = "default";
      }
    }

    // Create the respective taint wrapper object
    switch (taintWrapper.toLowerCase()) {
      case "default":
        // We use StubDroid, but with the summaries from inside the JAR
        // files
        SummaryTaintWrapper summaryWrapper = new SummaryTaintWrapper(
            new LazySummaryProvider("summariesManual"));
        summaryWrapper.setFallbackTaintWrapper(new EasyTaintWrapper());
        return summaryWrapper;
      case "none":
        return null;
      case "easy":
        // If the user has not specified a definition file for the easy
        // taint wrapper, we try to locate a default file
        String defFile = null;
        if (definitionFiles == null || definitionFiles.length == 0) {
          File defaultFile = EasyTaintWrapper.locateDefaultDefinitionFile();
          if (defaultFile == null) {
            try {
              return new EasyTaintWrapper(defFile);
            } catch (Exception e) {
              e.printStackTrace();
              System.err.println(
                  "No definition file for the easy taint wrapper specified and could not find the default file");
              throw new AbortAnalysisException();
            }
          } else {
            defFile = defaultFile.getCanonicalPath();
          }
        } else if (definitionFiles == null || definitionFiles.length != 1) {
          System.err.println("Must specify exactly one definition file for the easy taint wrapper");
          throw new AbortAnalysisException();
        } else {
          defFile = definitionFiles[0];
        }
        return new EasyTaintWrapper(defFile);
      case "stubdroid":
        if (definitionFiles == null || definitionFiles.length == 0) {
          System.err.println("Must specify at least one definition file for StubDroid");
          throw new AbortAnalysisException();
        }
        return TaintWrapperFactory.createTaintWrapper(Arrays.asList(definitionFiles));
      case "multi":
        // We need explicit definition files
        if (definitionFiles == null || definitionFiles.length == 0) {
          System.err.println("Must explicitly specify the definition files for the multi mode");
          throw new AbortAnalysisException();
        }

        // We need to group the definition files by their type
        MultiMap<String, String> extensionToFile = new HashMultiMap<>(definitionFiles.length);
        for (String str : definitionFiles) {
          File f = new File(str);
          if (f.isFile()) {
            String fileName = f.getName();
            extensionToFile
                .put(fileName.substring(fileName.lastIndexOf(".")), f.getCanonicalPath());
          } else if (f.isDirectory()) {
            extensionToFile.put(".xml", f.getCanonicalPath());
          }
        }

        // For each definition file, we create the respective taint wrapper
        TaintWrapperSet wrapperSet = new TaintWrapperSet();
        SummaryTaintWrapper stubDroidWrapper = null;
        if (extensionToFile.containsKey(".xml")) {
          stubDroidWrapper = TaintWrapperFactory.createTaintWrapper(extensionToFile.get(".xml"));
          wrapperSet.addWrapper(stubDroidWrapper);
        }
        Set<String> easyDefinitions = extensionToFile.get(".txt");
        if (!easyDefinitions.isEmpty()) {
          if (easyDefinitions.size() > 1) {
            System.err
                .println("Must specify exactly one definition file for the easy taint wrapper");
            throw new AbortAnalysisException();
          }

          // If we use StubDroid as well, we use the easy taint wrapper as
          // a fallback
          EasyTaintWrapper easyWrapper = new EasyTaintWrapper(easyDefinitions.iterator().next());
          if (stubDroidWrapper == null) {
            wrapperSet.addWrapper(easyWrapper);
          } else {
            stubDroidWrapper.setFallbackTaintWrapper(easyWrapper);
          }
        }
        return wrapperSet;
    }

    System.err.println("Invalid taint propagation wrapper specified, ignoring.");
    throw new AbortAnalysisException();
  }
}
