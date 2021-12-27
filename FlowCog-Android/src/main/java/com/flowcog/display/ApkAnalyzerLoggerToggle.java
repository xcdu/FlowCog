package com.flowcog.display;

import ch.qos.logback.classic.Level;
import java.util.Arrays;
import java.util.List;
import soot.Singletons;

public class ApkAnalyzerLoggerToggle {
  private TaintTrackingPhaseToggle taintTrackingPhaseToggle = new TaintTrackingPhaseToggle();

  private ViewTrackingPhaseToggle viewTrackingPhaseToggle = new ViewTrackingPhaseToggle();

  private static ApkAnalyzerLoggerToggle singleton = null;

  public static  ApkAnalyzerLoggerToggle v() {
    if (singleton == null) {
      synchronized (Singletons.class) {
        if (singleton == null) {
          singleton = new ApkAnalyzerLoggerToggle();
        }
      }
    }
    return singleton;
  }

  public TaintTrackingPhaseToggle getTaintTrackingPhaseToggle() {
    return taintTrackingPhaseToggle;
  }

  public ViewTrackingPhaseToggle getViewTrackingPhaseToggle() {
    return viewTrackingPhaseToggle;
  }

  private static class LoggerToggle {
    private static void off(List<String> classes) {
      for(String cls:classes) {
        LoggerManager.v().setLoggerLevel(cls, Level.OFF);
      }
    }
    private static void set(List<String> classes, Level level) {
      for(String cls:classes) {
        LoggerManager.v().setLoggerLevel(cls, level);
      }
    }
  }

  public class TaintTrackingPhaseToggle {
    private final List<String> classesBoundByLogger = Arrays.asList(
        "soot.jimple.infoflow",
        "com.flowcog.infoflow",
        "com.flowcog.android.analyzer.ApkAnalyzer$InternalInfoflowInst"
    );

    private boolean isTaintTrackingLoggerEnabled = true;


    public void off() {
      LoggerToggle.off(classesBoundByLogger);
      isTaintTrackingLoggerEnabled = false;
    }

    public void set(Level level) {
      LoggerToggle.set(classesBoundByLogger, level);
      if(level != Level.OFF) {
        isTaintTrackingLoggerEnabled = true;
      }
    }

    public boolean isTaintTrackingLoggerEnabled() {
      return isTaintTrackingLoggerEnabled;
    }
  }

  public class ViewTrackingPhaseToggle {
    private final List<String> classesBoundByLogger = Arrays.asList();

    public void off() {
      LoggerToggle.off(classesBoundByLogger);
    }

    public void set(Level level) {
      LoggerToggle.set(classesBoundByLogger, level);
    }
  }

}
