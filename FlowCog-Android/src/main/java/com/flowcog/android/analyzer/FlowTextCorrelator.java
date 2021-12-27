package com.flowcog.android.analyzer;

import com.flowcog.android.resources.AxmlResourceCollector;
import com.flowcog.result.FCSemanticExtractionResults;
import com.flowcog.result.TaintedResultsRecorder;
import com.flowcog.result.data.TaintedFlowWithMethod;
import com.flowcog.result.data.TaintedGroup;
import com.flowcog.result.data.UnitWithMethod;
import com.flowcog.result.statistics.FCResultsStatistics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.AnalysisFileConfiguration;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.internal.ImmediateBox;
import soot.jimple.internal.RValueBox;

public class FlowTextCorrelator {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final List<String> targetFunctionName = Arrays.asList(
      "findViewById",
      "setContentView",
      "inflate",
      "getIdentifier"
  );
  private TaintedResultsRecorder resultsRecorder;
  private AnalysisFileConfiguration config;

  private FCSemanticExtractionResults seResults;

  public FlowTextCorrelator(TaintedResultsRecorder resultsRecorder,
      AnalysisFileConfiguration config) {
    this.resultsRecorder = resultsRecorder;
    this.config = config;

    this.seResults = new FCSemanticExtractionResults(FCResultsStatistics.v().getAppName(),
        FCResultsStatistics.v().getStartDatetime());
  }

  public void correlateFlowText() {
    if (resultsRecorder == null) {
      logger.error("result recorder is null, perhaps error occurs in data analysis");
      return;
    }

    FCResultsStatistics.v()
        .setTaintRelativeFlowCnt(resultsRecorder.getTaintTrackingResults() != null ? resultsRecorder
            .getTaintTrackingResults().keySet().size() : 0);
    FCResultsStatistics.v()
        .setViewRelativeFlowCnt(resultsRecorder.getTaintTrackingResults() != null ? resultsRecorder
            .getViewTrackingResults().keySet().size() : 0);

    // build abstraction for view tracking taints
    IInfoflowCFG viewICfg = resultsRecorder.getViewICfg();
    Map<UnitWithMethod, Set<TaintedFlowWithMethod>> viewUnitToViewFlows = new HashMap<>();

    for (ResultSinkInfo viewSink : resultsRecorder.getViewTrackingResults().keySet()) {
      for (ResultSourceInfo viewSource : resultsRecorder.getViewTrackingResults().get(viewSink)) {
        TaintedFlowWithMethod taintedFlowWithMethod = new TaintedFlowWithMethod(viewSource,
            viewSink,
            viewICfg.getMethodOf(viewSource.getStmt()), viewICfg.getMethodOf(viewSink.getStmt()));
        if (viewSource.getPath() != null) {
          for (Unit p : viewSource.getPath()) {
            UnitWithMethod pathNode = new UnitWithMethod(p, viewICfg.getMethodOf(p));
            if (!viewUnitToViewFlows.containsKey(pathNode)) {
              viewUnitToViewFlows.put(pathNode, new HashSet<>());
            }
            viewUnitToViewFlows.get(pathNode).add(taintedFlowWithMethod);
          }
        } else {
          UnitWithMethod sourceUnitWithMethod = new UnitWithMethod(viewSource.getStmt(),
              viewICfg.getMethodOf(viewSource.getStmt()));
          if (!viewUnitToViewFlows.containsKey(sourceUnitWithMethod)) {
            viewUnitToViewFlows.put(sourceUnitWithMethod, new HashSet<>());
          }
          viewUnitToViewFlows.get(sourceUnitWithMethod).add(taintedFlowWithMethod);

          UnitWithMethod sinkUnitWithMethod = new UnitWithMethod(viewSink.getStmt(),
              viewICfg.getMethodOf(viewSink.getStmt()));
          if (!viewUnitToViewFlows.containsKey(sinkUnitWithMethod)) {
            viewUnitToViewFlows.put(sinkUnitWithMethod, new HashSet<>());
          }
          viewUnitToViewFlows.get(sinkUnitWithMethod).add(taintedFlowWithMethod);
        }
      }
    }

    try {
      AxmlResourceCollector axmlResourceCollector = new AxmlResourceCollector(config);
      List<String> allLayouts = axmlResourceCollector.getAllLayoutStrings();
      IInfoflowCFG taintICfg = resultsRecorder.getTaintICfg();
      int flowSequenceId = 0;
      for (ResultSinkInfo taintSink : resultsRecorder.getTaintTrackingResults().keySet()) {
        Set<String> relativeLayoutTexts = new HashSet<>();
        Set<String> relativeParameterTexts = new HashSet<>();
        List<TaintedGroup> taintedGroupsWithSameSink = new ArrayList<>();
        for (ResultSourceInfo taintSource : resultsRecorder.getTaintTrackingResults()
            .get(taintSink)) {
          Map<TaintedFlowWithMethod, Integer> sameViewNodeCounter = new HashMap<>();
          List<UnitWithMethod> searchQueue = new LinkedList<>();
          if (taintSource.getPath() != null) {
            for (Unit p : taintSource.getPath()) {
              UnitWithMethod pathNode = new UnitWithMethod(p, taintICfg.getMethodOf(p));
              searchQueue.add(pathNode);
            }
          } else {
            searchQueue.add(
                new UnitWithMethod(taintSource.getStmt(),
                    taintICfg.getMethodOf(taintSource.getStmt())));
            searchQueue
                .add(new UnitWithMethod(taintSink.getStmt(),
                    taintICfg.getMethodOf(taintSink.getStmt())));
          }
          for (UnitWithMethod nodeToSearch : searchQueue) {
            if (!viewUnitToViewFlows.containsKey(nodeToSearch)) {
              continue;
            }
            for (TaintedFlowWithMethod viewFlowWithMethod : viewUnitToViewFlows.get(nodeToSearch)) {
              sameViewNodeCounter
                  .put(viewFlowWithMethod,
                      sameViewNodeCounter.getOrDefault(viewFlowWithMethod, 0) + 1);
            }
          }

          TaintedGroup curTaintedGroup = new TaintedGroup(seResults.getAppName(),
              seResults.getDatetime(), flowSequenceId++, taintSource, taintSink, taintICfg);

          // Prepare parameter texts
          Set<String> viewTexts = new HashSet<>();
          Set<String> layoutTexts = new HashSet<>();
          Set<String> parameterTexts = new HashSet<>();

          // Search flow path itself. If none path provided, we just search source and sink
          Stmt[] flowPath = (taintSource.getPath() != null) ? taintSource.getPath()
              : new Stmt[]{taintSource.getStmt(), taintSink.getStmt()};
          for (Stmt p : flowPath) {
            for (ValueBox valueBox : p.getUseAndDefBoxes()) {
              // add texts from UI view
              if (valueBox instanceof RValueBox) {
                String valueString = valueBox.getValue().toString();
                for (String fName : targetFunctionName) {
                  if (valueString.contains(fName)) {
                    int idx = valueString.indexOf(fName);
                    String subString =
                        valueString.substring(idx + fName.length());
                    Pattern pattern = Pattern.compile("[0-9]+");
                    Matcher matcher = pattern.matcher(subString);
                    if (matcher.find()) {
                      Integer id = Integer.parseInt(matcher.group(0));
                      logger.debug("found id {} for viewTexts", id);
                      viewTexts.addAll(axmlResourceCollector.resolveRelatedStrings(id));
                    }
                  }
                }
              }
              // add texts from parameter
              if (valueBox instanceof ImmediateBox) {
                Value value = valueBox.getValue();
                String valueString = value.toString();
                String valueType = value.getType().toString();
                if (valueType.equals("java.lang.String") && !Pattern
                    .matches("\\$[a-z][0-9]+", valueString)) {
                  parameterTexts.add(valueString);
                  relativeParameterTexts.add(valueString);
                }
              }
            }

            SootMethod pathNodeMethodBody = taintICfg.getMethodOf(p);
            if (pathNodeMethodBody.hasActiveBody()) {
              Body body = pathNodeMethodBody.getActiveBody();
              for (Unit bodyUnit : body.getUnits()) {
                for (ValueBox vb : bodyUnit.getUseAndDefBoxes()) {
                  for (String tName : targetFunctionName) {
                    String valueString = vb.toString();
                    if (valueString.contains(tName)) {
                      int idx = valueString.indexOf(tName);
                      String subString =
                          valueString.substring(idx + tName.length());
                      Pattern pattern = Pattern.compile("[0-9]+");
                      Matcher matcher = pattern.matcher(subString);
                      if (matcher.find()) {
                        Integer id = Integer.parseInt(matcher.group(0));
                        logger.debug("found id {} for relative layout texts", id);
                        List<String> relatedTexts = axmlResourceCollector.resolveRelatedStrings(id);
                        viewTexts.addAll(relatedTexts);
                        relativeLayoutTexts.addAll(relatedTexts);
                      }
                    }
                  }
                }
              }
            }


          }

          // at least have the same source and sink
          if (!sameViewNodeCounter.isEmpty()) {
            for (TaintedFlowWithMethod curViewFlow : sameViewNodeCounter.keySet()) {
              if (sameViewNodeCounter.get(curViewFlow) > 1) {
                // only has subpath
                ResultSourceInfo curViewSource = curViewFlow.getSource();
                Stmt[] curViewFlowPath = (curViewSource.getPath() != null) ? curViewSource.getPath()
                    : new Stmt[]{curViewFlow.getSource().getStmt(),
                        curViewFlow.getSink().getStmt()};
                for (Stmt p : curViewFlowPath) {
                  for (ValueBox viewFlowStmtValueBox : p.getUseAndDefBoxes()) {
                    // add texts from UI view
                    if (viewFlowStmtValueBox instanceof RValueBox) {
                      String valueString = viewFlowStmtValueBox.getValue().toString();
                      for (String fName : targetFunctionName) {
                        if (valueString.contains(fName)) {
                          int idx = valueString.indexOf(fName);
                          String subString =
                              valueString.substring(idx + fName.length());
                          Pattern pattern = Pattern.compile("[0-9]+");
                          Matcher matcher = pattern.matcher(subString);
                          if (matcher.find()) {
                            Integer id = Integer.parseInt(matcher.group(0));
                            logger.debug("found id {} for layout texts", id);
                            // since the flow path could not be the subpath of this path, it must
                            // branched somewhere. So we add the id we found to layout texts
                            List<String> curLayoutTexts = axmlResourceCollector
                                .resolveRelatedStrings(id);
                            layoutTexts.addAll(curLayoutTexts);
                            relativeLayoutTexts.addAll(curLayoutTexts);
                          }
                        }
                      }
                    }
                    // add texts from parameter
                    if (viewFlowStmtValueBox instanceof ImmediateBox) {
                      Value value = viewFlowStmtValueBox.getValue();
                      String valueString = value.toString();
                      String valueType = value.getType().toString();
                      if (valueType.equals("java.lang.String") && !Pattern
                          .matches("\\$[a-z][0-9]+", valueString)) {
                        parameterTexts.add(valueString);
                        relativeParameterTexts.add(valueString);
                      }
                    }
                  }

                  SootMethod pathNodeMethodBody = viewICfg.getMethodOf(p);
                  if (pathNodeMethodBody.hasActiveBody()) {
                    Body body = pathNodeMethodBody.getActiveBody();
                    for (Unit bodyUnit : body.getUnits()) {
                      for (ValueBox vb : bodyUnit.getUseAndDefBoxes()) {
                        for (String tName : targetFunctionName) {
                          String valueString = vb.toString();
                          if (valueString.contains(tName)) {
                            int idx = valueString.indexOf(tName);
                            String subString =
                                valueString.substring(idx + tName.length());
                            Pattern pattern = Pattern.compile("[0-9]+");
                            Matcher matcher = pattern.matcher(subString);
                            if (matcher.find()) {
                              Integer id = Integer.parseInt(matcher.group(0));
                              List<String> relatedTexts = axmlResourceCollector
                                  .resolveRelatedStrings(id);
                              viewTexts.addAll(relatedTexts);
                              relativeLayoutTexts.addAll(relatedTexts);
                            }
                          }
                        }
                      }
                    }
                  }
                }

              }
            }
          }

          curTaintedGroup.settView(new ArrayList<>(viewTexts));
          curTaintedGroup.settLayoutContext(new ArrayList<>(layoutTexts));
          curTaintedGroup.settAllLayouts(allLayouts);
          curTaintedGroup.settParameter(new ArrayList<>(parameterTexts));
          taintedGroupsWithSameSink.add(curTaintedGroup);
        }
        for (TaintedGroup taintedGroup : taintedGroupsWithSameSink) {
          taintedGroup.settRelativeLayout(new ArrayList<>(relativeLayoutTexts));
          taintedGroup.settRelativeParameter(new ArrayList<>(relativeParameterTexts));
          seResults.getTaintedGroups().add(taintedGroup);
        }
      }
    } catch (IOException | XmlPullParserException e) {
      logger.error("Error occurs while correlating the resources, errmsg: {}", e.toString());
    }

    FCResultsStatistics.v().setCorrelatedFlowCnt(seResults.getTaintedGroups().size());

    int textFoundFlowCnt = 0;
    for (TaintedGroup taintedGroup : seResults.getTaintedGroups()) {
      if (!taintedGroup.gettLayoutContext().isEmpty() || !taintedGroup.gettRelativeLayout()
          .isEmpty() || !taintedGroup.gettView().isEmpty() || !taintedGroup.gettAllLayouts()
          .isEmpty()) {
        textFoundFlowCnt += 1;
      }
    }
    FCResultsStatistics.v().setTextFoundFlowCnt(textFoundFlowCnt);
  }

  public void printSematicExtractionResults() {
    logger.info("[PRINT JSON]");
    if (seResults != null && seResults.getTaintedGroups().size() != 0) {
      logger.info(seResults.getJsonSerializedTaintedGroups());
    }
    logger.info("[PRINT JSON END]");
    logger.info("{} flows correlated in total", (resultsRecorder != null) ?
        resultsRecorder.getTaintTrackingResults().keySet().size() : 0);

  }


  public TaintedResultsRecorder getResultsRecorder() {
    return resultsRecorder;
  }

  public void setResultsRecorder(TaintedResultsRecorder resultsRecorder) {
    this.resultsRecorder = resultsRecorder;
  }

  public FCSemanticExtractionResults getSeResults() {
    return seResults;
  }

  public void setSeResults(FCSemanticExtractionResults seResults) {
    this.seResults = seResults;
  }
}
