package com.flowcog.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.flowcog.result.data.TaintedGroup;
import com.flowcog.result.data.TaintedGroupList;
import com.flowcog.result.json.TaintedGroupListSerializer;
import java.util.Date;
import java.util.List;

public class FCSemanticExtractionResults {
  private String appName;
  private Date datetime;
  private TaintedGroupList taintedGroups = new TaintedGroupList();

  public FCSemanticExtractionResults() {}

  public FCSemanticExtractionResults(String appName, Date datetime) {
    this.appName = appName;
    this.datetime = datetime;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public Date getDatetime() {
    return datetime;
  }

  public void setDatetime(Date datetime) {
    this.datetime = datetime;
  }

  public List<TaintedGroup> getTaintedGroups() {
    return taintedGroups.getTaintedGroups();
  }

  public void setTaintedGroups(List<TaintedGroup> taintedGroups) {
    this.taintedGroups.setTaintedGroups(taintedGroups);
  }

  public String getUUID() {
    return appName + "_" + datetime.getTime() + "_";
  }

  public String getJsonSerializedTaintedGroups() {
    ObjectMapper jsonMapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(TaintedGroupList.class, new TaintedGroupListSerializer());
    jsonMapper.registerModule(module);
    jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
    try {
      return jsonMapper.writeValueAsString(taintedGroups);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return "";
  }


}
