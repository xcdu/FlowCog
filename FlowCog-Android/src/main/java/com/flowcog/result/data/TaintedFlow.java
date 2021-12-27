package com.flowcog.result.data;

import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;

public class TaintedFlow {

  private ResultSourceInfo source;
  private ResultSinkInfo sink;

  public TaintedFlow() {
  }

  public TaintedFlow(ResultSourceInfo source, ResultSinkInfo sink) {
    this.source = source;
    this.sink = sink;
  }

  public ResultSourceInfo getSource() {
    return source;
  }

  public void setSource(ResultSourceInfo source) {
    this.source = source;
  }

  public ResultSinkInfo getSink() {
    return sink;
  }

  public void setSink(ResultSinkInfo sink) {
    this.sink = sink;
  }

  @Override
  public int hashCode() {
    String sourceSink = source.toString() + sink.toString();
    return sourceSink.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == this) {
      return true;
    }
    if(!(obj instanceof TaintedFlow)) {
      return false;
    }
    TaintedFlow o = (TaintedFlow) obj;
    String oSourceSink = o.source.toString() + o.sink.toString();
    String sourceSink = source.toString() + sink.toString();
    return sourceSink.equals(oSourceSink);
  }
}
