package com.flowcog.result.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.flowcog.result.data.TaintedGroup;
import java.io.IOException;

public class TaintedGroupSerializer extends JsonSerializer<TaintedGroup> {

  @Override
  public void serialize(TaintedGroup taintedGroup, JsonGenerator jGen,
      SerializerProvider serializerProvider) throws IOException {
    jGen.writeStartObject();
    SingleTaintedGroupFeildsSerializer.serialize(taintedGroup, jGen, serializerProvider);
    jGen.writeEndArray();
  }
}

