package com.flowcog.display;

import ch.qos.logback.classic.Level;
import soot.Singletons;

public class LoggerManager {

  private static volatile LoggerManager singleton = null;

  private LoggerManager() {
  }

  public static LoggerManager v() {
    if (singleton == null) {
      synchronized (Singletons.class) {
        if (singleton == null) {
          singleton = new LoggerManager();
        }
      }
    }
    return singleton;
  }

  public void setLoggerLevel(Class<?> cls, Level level) {
    ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
        .getLogger(cls);
    logger.setLevel(level);
  }

  public void setLoggerLevel(String className, Level level) {
    ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
        .getLogger(className);
    logger.setLevel(level);
  }


}
