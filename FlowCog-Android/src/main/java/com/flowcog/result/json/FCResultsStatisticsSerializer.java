package com.flowcog.result.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.flowcog.result.statistics.FCResultsStatistics;
import java.io.IOException;

public class FCResultsStatisticsSerializer extends JsonSerializer<FCResultsStatistics> {

  @Override
  public void serialize(FCResultsStatistics fcResultsStatistics, JsonGenerator jGen,
      SerializerProvider serializerProvider) throws IOException {
    jGen.writeStartObject();
    jGen.writeStringField("app_name", fcResultsStatistics.getAppName());
    jGen.writeNumberField("datetime", fcResultsStatistics.getStartDatetime().getTime());

    jGen.writeNumberField("taint_relative_flow_cnt", fcResultsStatistics.getTaintRelativeFlowCnt());
    jGen.writeNumberField("view_relative_flow_cnt", fcResultsStatistics.getViewRelativeFlowCnt());
    jGen.writeNumberField("correlated_flow_cnt", fcResultsStatistics.getCorrelatedFlowCnt());
    jGen.writeNumberField("text_found_flow_cnt", fcResultsStatistics.getTextFoundFlowCnt());

    jGen.writeNumberField("total_time", fcResultsStatistics.getTotalTime());
    jGen.writeNumberField("taint_tracking_time", fcResultsStatistics.getTaintTrackingTime());
    jGen.writeNumberField("view_tracking_time", fcResultsStatistics.getViewTrackingTime());
    jGen.writeNumberField("ui_correlation_time", fcResultsStatistics.getUiCorrelationTime());
    jGen.writeNumberField("memory_consumption", fcResultsStatistics.getMemoryConsumption());
    jGen.writeBooleanField("is_taint_tracking_finished",
        fcResultsStatistics.getTaintTrackingFinished());
    jGen.writeBooleanField("is_view_tracking_finished",
        fcResultsStatistics.getViewTrackingFinished());
    jGen.writeBooleanField("is_flowcog_finished", fcResultsStatistics.getFlowCogFinished());
    jGen.writeEndObject();
  }
}
