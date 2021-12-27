package com.flowcog.result.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.flowcog.result.data.TaintedGroup;
import com.flowcog.result.data.TaintedGroupList;
import java.io.IOException;

public class TaintedGroupListSerializer extends JsonSerializer<TaintedGroupList> {

  @Override
  public void serialize(TaintedGroupList taintedGroups, JsonGenerator jGen,
      SerializerProvider serializerProvider) throws IOException {
    jGen.writeStartArray();
    for (TaintedGroup taintedGroup : taintedGroups.getTaintedGroups()) {
      jGen.writeStartObject();
      SingleTaintedGroupFeildsSerializer.serialize(taintedGroup, jGen, serializerProvider);
      jGen.writeEndArray();
      jGen.writeEndObject();
    }
    jGen.writeEndArray();
  }
}
