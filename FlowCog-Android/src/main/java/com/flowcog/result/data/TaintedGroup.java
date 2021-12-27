package com.flowcog.result.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.flowcog.result.json.TaintedGroupSerializer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;

public class TaintedGroup {

  private String appName;
  private Date datetime;
  // Number ID of flow in specific on processing;
  private Integer flowSeqId;

  private ResultSourceInfo source;
  private ResultSinkInfo sink;
  private UnitWithMethod sourceSig;
  private UnitWithMethod sinkSig;
  private Stmt[] path;
  private ArrayList<UnitWithMethod> pathSig;

  // Prefix 't' means "texts"
  // View texts are texts directly related to UI view
  private List<String> tView = new ArrayList<>();

  // Layout context texts are relative to the context to specific UI View. They're used as
  // supplement of view texts
  private List<String> tLayoutContext = new ArrayList<>();
  // Layout context texts from relative flows which end with the same sink
  private List<String> tRelativeLayout = new ArrayList<>();
  // All Layout texts from layout files
  private List<String> tAllLayouts = new ArrayList<>();

  // Parameter texts will be used as attribute of tainted flow
  private List<String> tParameter = new ArrayList<>();
  // Parameter texts from from relative flows which end with the same sink
  private List<String> tRelativeParameter = new ArrayList<>();

  public TaintedGroup() {
  }

  public TaintedGroup(String appName, Date datetime, Integer flowSeqId) {
    this.appName = appName;
    this.datetime = datetime;
    this.flowSeqId = flowSeqId;
  }

  public TaintedGroup(String appName, Date datetime, Integer flowSeqId,
      ResultSourceInfo source, ResultSinkInfo sink, IInfoflowCFG icfg) {
    this.appName = appName;
    this.datetime = datetime;
    this.flowSeqId = flowSeqId;
    this.source = source;
    this.sink = sink;

    updateFlow(icfg);
    updatePath(icfg);
  }

  private void updateFlow(IInfoflowCFG icfg) {
    if (icfg == null) {
      this.sourceSig = new UnitWithMethod();
      this.sinkSig = new UnitWithMethod();
    } else {
      this.sourceSig = new UnitWithMethod(source.getStmt(), icfg.getMethodOf(source.getStmt()));
      this.sinkSig = new UnitWithMethod(sink.getStmt(), icfg.getMethodOf(sink.getStmt()));
    }
  }


  private void updatePath(IInfoflowCFG icfg) {
    boolean hasPath = source.getPath() != null;
    path = new Stmt[]{};
    pathSig = new ArrayList<>();
    if (!hasPath) {
      return;
    }
    path = source.getPath();
    for (Stmt s : path) {
      pathSig.add(new UnitWithMethod(s, icfg.getMethodOf(s)));
    }
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

  public Integer getFlowSeqId() {
    return flowSeqId;
  }

  public void setFlowSeqId(Integer flowSeqId) {
    this.flowSeqId = flowSeqId;
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

  public List<String> gettView() {
    return tView;
  }

  public void settView(List<String> tView) {
    this.tView = tView;
  }

  public List<String> gettLayoutContext() {
    return tLayoutContext;
  }

  public void settLayoutContext(List<String> tLayoutContext) {
    this.tLayoutContext = tLayoutContext;
  }

  public List<String> gettParameter() {
    return tParameter;
  }

  public void settParameter(List<String> tParameter) {
    this.tParameter = tParameter;
  }

  public Stmt[] getPath() {
    return path;
  }

  public void setPath(Stmt[] path) {
    this.path = path;
  }

  public List<String> gettRelativeLayout() {
    return tRelativeLayout;
  }

  public void settRelativeLayout(List<String> tRelativeLayout) {
    this.tRelativeLayout = tRelativeLayout;
  }

  public List<String> gettRelativeParameter() {
    return tRelativeParameter;
  }

  public void settRelativeParameter(List<String> tRelativeParameter) {
    this.tRelativeParameter = tRelativeParameter;
  }

  public UnitWithMethod getSourceSig() {
    return sourceSig;
  }

  public void setSourceSig(UnitWithMethod sourceSig) {
    this.sourceSig = sourceSig;
  }

  public UnitWithMethod getSinkSig() {
    return sinkSig;
  }

  public void setSinkSig(UnitWithMethod sinkSig) {
    this.sinkSig = sinkSig;
  }

  public ArrayList<UnitWithMethod> getPathSig() {
    return pathSig;
  }

  public void setPathSig(ArrayList<UnitWithMethod> pathSig) {
    this.pathSig = pathSig;
  }

  public List<String> gettAllLayouts() {
    return tAllLayouts;
  }

  public void settAllLayouts(List<String> tAllLayouts) {
    this.tAllLayouts = tAllLayouts;
  }

  @Override
  public String toString() {
    return "TaintedGroup{" +
        "appName='" + appName + '\'' +
        ", datetime=" + datetime +
        ", flowSeqId=" + flowSeqId +
        ", source=" + source +
        ", sink=" + sink +
        ", sourceSig=" + sourceSig +
        ", sinkSig=" + sinkSig +
        ", path=" + Arrays.toString(path) +
        ", pathSig=" + pathSig +
        ", tView=" + tView +
        ", tLayoutContext=" + tLayoutContext +
        ", tRelativeLayout=" + tRelativeLayout +
        ", tAllLayouts=" + tAllLayouts +
        ", tParameter=" + tParameter +
        ", tRelativeParameter=" + tRelativeParameter +
        '}';
  }

  public String toJson() {
    ObjectMapper jsonMapper = new ObjectMapper();
    SimpleModule jsonModule = new SimpleModule();
    jsonModule.addSerializer(TaintedGroup.class, new TaintedGroupSerializer());
    jsonMapper.registerModule(jsonModule);
    jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
    try {
      return jsonMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return "";
  }


}
