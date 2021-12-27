package com.flowcog.display;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class LoggerFilter extends Filter<ILoggingEvent> {

  @Override
  public FilterReply decide(ILoggingEvent event) {
//    System.out.println("event = " + event.getLoggerName());
//    if (event.getLoggerName().contains("com.flowcog.android.analyzer.ApkAnalyzer")
//        && !ApkAnalyzerLoggerToggle.v().getTaintTrackingPhaseToggle()
//        .isTaintTrackingLoggerEnabled()) {
//      return FilterReply.DENY;
//    }
    return FilterReply.NEUTRAL;
  }

}
