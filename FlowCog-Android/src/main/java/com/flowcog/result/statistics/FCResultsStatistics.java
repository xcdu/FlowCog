package com.flowcog.result.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.flowcog.result.json.FCResultsStatisticsSerializer;
import java.util.Date;
import soot.Singletons;

/**
 * Used for statistic collection
 */
public class FCResultsStatistics {
  private static volatile FCResultsStatistics singleton = null;

  private String appName;
  private Date startDatetime;
  private Integer viewRelativeFlowCnt;
  private Integer taintRelativeFlowCnt;
  private Integer correlatedFlowCnt;
  private Integer textFoundFlowCnt;
  private Long totalTime;
  private Long viewTrackingTime;
  private Long taintTrackingTime;
  private Long uiCorrelationTime;
  private Long memoryConsumption;
  private Boolean isViewTrackingFinished;
  private Boolean isTaintTrackingFinished;
  private Boolean isFlowCogFinished;

  private FCResultsStatistics() {
    appName = "";
    startDatetime = new Date(0);

    viewRelativeFlowCnt = 0;
    taintRelativeFlowCnt = 0;
    correlatedFlowCnt = 0;
    textFoundFlowCnt = 0;

    totalTime = (long) 0;
    viewTrackingTime = (long) 0;
    taintTrackingTime = (long) 0;
    uiCorrelationTime = (long) 0;
    memoryConsumption = (long) 0;
    isViewTrackingFinished = false;
    isTaintTrackingFinished = false;
    isFlowCogFinished = false;
  }

  public static FCResultsStatistics v() {
    if(singleton == null) {
      synchronized (Singletons.class) {
        if(singleton == null) {
          singleton = new FCResultsStatistics();
        }
      }
    }
    return singleton;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public Date getStartDatetime() {
    return startDatetime;
  }

  public void setStartDatetime(Date startDatetime) {
    this.startDatetime = startDatetime;
  }

  public Integer getViewRelativeFlowCnt() {
    return viewRelativeFlowCnt;
  }

  public void setViewRelativeFlowCnt(Integer viewRelativeFlowCnt) {
    this.viewRelativeFlowCnt = viewRelativeFlowCnt;
  }

  public Integer getTaintRelativeFlowCnt() {
    return taintRelativeFlowCnt;
  }

  public void setTaintRelativeFlowCnt(Integer taintRelativeFlowCnt) {
    this.taintRelativeFlowCnt = taintRelativeFlowCnt;
  }

  public Integer getCorrelatedFlowCnt() {
    return correlatedFlowCnt;
  }

  public void setCorrelatedFlowCnt(Integer correlatedFlowCnt) {
    this.correlatedFlowCnt = correlatedFlowCnt;
  }

  public Integer getTextFoundFlowCnt() {
    return textFoundFlowCnt;
  }

  public void setTextFoundFlowCnt(Integer textFoundFlowCnt) {
    this.textFoundFlowCnt = textFoundFlowCnt;
  }

  public Long getTotalTime() {
    return totalTime;
  }

  public void setTotalTime(Long totalTime) {
    this.totalTime = totalTime;
  }

  public Long getViewTrackingTime() {
    return viewTrackingTime;
  }

  public void setViewTrackingTime(Long viewTrackingTime) {
    this.viewTrackingTime = viewTrackingTime;
  }

  public Long getTaintTrackingTime() {
    return taintTrackingTime;
  }

  public void setTaintTrackingTime(Long taintTrackingTime) {
    this.taintTrackingTime = taintTrackingTime;
  }

  public Long getUiCorrelationTime() {
    return uiCorrelationTime;
  }

  public void setUiCorrelationTime(Long uiCorrelationTime) {
    this.uiCorrelationTime = uiCorrelationTime;
  }

  public Long getMemoryConsumption() {
    return memoryConsumption;
  }

  public void setMemoryConsumption(Long memoryConsumption) {
    this.memoryConsumption = memoryConsumption;
  }

  public Boolean getViewTrackingFinished() {
    return isViewTrackingFinished;
  }

  public void setViewTrackingFinished(Boolean viewTrackingFinished) {
    isViewTrackingFinished = viewTrackingFinished;
  }

  public Boolean getTaintTrackingFinished() {
    return isTaintTrackingFinished;
  }

  public void setTaintTrackingFinished(Boolean taintTrackingFinished) {
    isTaintTrackingFinished = taintTrackingFinished;
  }

  public Boolean getFlowCogFinished() {
    return isFlowCogFinished;
  }

  public void setFlowCogFinished(Boolean flowCogFinished) {
    isFlowCogFinished = flowCogFinished;
  }

  public String toJson() {
    ObjectMapper jsonMapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(FCResultsStatistics.class, new FCResultsStatisticsSerializer());
    jsonMapper.registerModule(module);
    jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
    try {
      return jsonMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return "";
  }

}
