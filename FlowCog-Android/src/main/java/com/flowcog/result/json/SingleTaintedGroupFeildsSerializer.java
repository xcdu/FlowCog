package com.flowcog.result.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.flowcog.result.data.TaintedGroup;
import com.flowcog.result.data.UnitWithMethod;
import java.io.IOException;
import soot.jimple.Stmt;

public class SingleTaintedGroupFeildsSerializer {

  public static void serialize(TaintedGroup taintedGroup, JsonGenerator jGen,
      SerializerProvider serializerProvider) throws IOException {

    jGen.writeStringField("app_name", taintedGroup.getAppName());
    jGen.writeNumberField("datetime", taintedGroup.getDatetime().getTime());
    jGen.writeNumberField("flow_seq_id", taintedGroup.getFlowSeqId());
    jGen.writeStringField("source", taintedGroup.getSource().toString());
    jGen.writeStringField("sink", taintedGroup.getSink().toString());
    jGen.writeStringField("source_with_method", taintedGroup.getSourceSig().toString());
    jGen.writeStringField("sink_with_method", taintedGroup.getSinkSig().toString());

    jGen.writeArrayFieldStart("path");
    for (Stmt pathNode : taintedGroup.getPath()) {
      jGen.writeString(pathNode.toString());
    }
    jGen.writeEndArray();

    jGen.writeArrayFieldStart("path_with_method");
    for (UnitWithMethod pathNode : taintedGroup.getPathSig()) {
      jGen.writeString(pathNode.toString());
    }
    jGen.writeEndArray();

    jGen.writeArrayFieldStart("view");
    for (String viewText : taintedGroup.gettView()) {
      jGen.writeString(viewText);
    }
    jGen.writeEndArray();

    jGen.writeArrayFieldStart("layout_context");
    for (String layoutContext : taintedGroup.gettLayoutContext()) {
      jGen.writeString(layoutContext);
    }
    jGen.writeEndArray();

    jGen.writeArrayFieldStart("relative_layout");
    for (String rLayout : taintedGroup.gettRelativeLayout()) {
      jGen.writeString(rLayout);
    }
    jGen.writeEndArray();

    jGen.writeArrayFieldStart("all_layouts");
    for (String layout : taintedGroup.gettAllLayouts()) {
      jGen.writeString(layout);
    }
    jGen.writeEndArray();

    jGen.writeArrayFieldStart("parameter");
    for (String parameter : taintedGroup.gettParameter()) {
      jGen.writeString(parameter);
    }
    jGen.writeEndArray();

    jGen.writeArrayFieldStart("relative_parameter");
    for (String rParamter : taintedGroup.gettRelativeParameter()) {
      jGen.writeString(rParamter);
    }
  }
}
