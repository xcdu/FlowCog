package com.flowcog.result;

import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class TaintedResultsRecorder {
//  private final Logger logger = LoggerFactory.getLogger(getClass());
  private MultiMap<ResultSinkInfo, ResultSourceInfo> taintTrackingResults;
  private IInfoflowCFG taintICfg;

  private MultiMap<ResultSinkInfo, ResultSourceInfo> viewTrackingResults;
  private IInfoflowCFG viewICfg;

  public MultiMap<ResultSinkInfo, ResultSourceInfo> getTaintTrackingResults() {
    if(taintTrackingResults == null) {
      return new HashMultiMap<>();
    }
    return taintTrackingResults;
  }

  public void setTaintTrackingResults(
      MultiMap<ResultSinkInfo, ResultSourceInfo> taintTrackingResults) {
    this.taintTrackingResults = taintTrackingResults;
  }

  public MultiMap<ResultSinkInfo, ResultSourceInfo> getViewTrackingResults() {
    if(viewTrackingResults == null) {
      return new HashMultiMap<>();
    }
    return viewTrackingResults;
  }

  public void setViewTrackingResults(
      MultiMap<ResultSinkInfo, ResultSourceInfo> viewTrackingResults) {
    this.viewTrackingResults = viewTrackingResults;
  }

  public IInfoflowCFG getTaintICfg() {
    return taintICfg;
  }

  public void setTaintICfg(IInfoflowCFG taintICfg) {
    this.taintICfg = taintICfg;
  }

  public IInfoflowCFG getViewICfg() {
    return viewICfg;
  }

  public void setViewICfg(IInfoflowCFG viewICfg) {
    this.viewICfg = viewICfg;
  }
}
