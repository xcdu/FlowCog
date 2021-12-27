package com.flowcog.result.data;

import soot.SootMethod;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;

public class TaintedFlowWithMethod {
  private ResultSourceInfo source;
  private ResultSinkInfo sink;
  private SootMethod sourceMethod;
  private SootMethod sinkMethod;

  public TaintedFlowWithMethod() {
  }

  public TaintedFlowWithMethod(ResultSourceInfo source,
      ResultSinkInfo sink, SootMethod sourceMethod, SootMethod sinkMethod) {
    this.source = source;
    this.sink = sink;
    this.sourceMethod = sourceMethod;
    this.sinkMethod = sinkMethod;
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

  public SootMethod getSourceMethod() {
    return sourceMethod;
  }

  public void setSourceMethod(SootMethod sourceMethod) {
    this.sourceMethod = sourceMethod;
  }

  public SootMethod getSinkMethod() {
    return sinkMethod;
  }

  public void setSinkMethod(SootMethod sinkMethod) {
    this.sinkMethod = sinkMethod;
  }

  @Override
  public String toString() {
    return "TaintedFlowWithMethod{" +
        "source=" + source +
        ", sink=" + sink +
        ", sourceMethod=" + sourceMethod +
        ", sinkMethod=" + sinkMethod +
        '}';
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == this) {
      return true;
    }
    if(!(obj instanceof TaintedFlowWithMethod)) {
      return false;
    }
    TaintedFlowWithMethod o = (TaintedFlowWithMethod) obj;
    return this.toString().equals(o.toString());
  }
}
