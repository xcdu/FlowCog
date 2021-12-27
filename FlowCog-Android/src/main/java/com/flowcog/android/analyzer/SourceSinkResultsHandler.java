package com.flowcog.android.analyzer;

import com.flowcog.android.data.ui.view.UIViewRelatedSourceSinkManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Unit;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;

public class SourceSinkResultsHandler {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private IInfoflowCFG icfg;
  private InfoflowResults results;
  private UIViewRelatedSourceSinkManager sourceSinkManager;

  // filtered results means results without extra sources or sinks predefined
  private Map<ResultSinkInfo, Set<ResultSourceInfo>> filteredResults;
  private int resultsCnt = 0;
  private int filteredResultsCnt = 0;

  public SourceSinkResultsHandler(IInfoflowCFG icfg, InfoflowResults results,
      UIViewRelatedSourceSinkManager sourceSinkManager) {
    this.icfg = icfg;
    this.results = results;
    this.sourceSinkManager = sourceSinkManager;
  }

  private void filterResults() {
    filteredResults = new HashMap<>();
    this.resultsCnt = 0;
    this.filteredResultsCnt = 0;

    Set<SourceSinkDefinition> extraSourcesSinks = new HashSet<>();
    extraSourcesSinks.addAll(sourceSinkManager.getSources().getExtra());
    extraSourcesSinks.addAll(sourceSinkManager.getSinks().getExtra());
    for (ResultSinkInfo sinkInfo : results.getResults().keySet()) {
      boolean isExtraSink = false;
      if (extraSourcesSinks.contains(sinkInfo.getDefinition())) {
        isExtraSink = true;
      }
      for (ResultSourceInfo sourceInfo : results.getResults().get(sinkInfo)) {
        this.resultsCnt++;
        if (isExtraSink || extraSourcesSinks.contains(sourceInfo.getDefinition())) {
          if (!isExtraSink) {
          }
          continue;
        }
        if (!filteredResults.keySet().contains(sinkInfo)) {
          filteredResults.put(sinkInfo, new HashSet<>());
        }
        filteredResults.get(sinkInfo).add(sourceInfo);
        filteredResultsCnt++;
      }
    }
    logger.info("Found {} leaks in total with {} leaks not relative to UI", resultsCnt,
        filteredResultsCnt);
  }


  public void printResults() {
    if (filteredResults == null) {
      filterResults();
    }


    int necessarycnt = 0;
    if (results == null || results.isEmpty()) {
      logger.warn("No results found.");
    } else if (logger.isInfoEnabled()) {
      for (ResultSinkInfo sink : results.getResults().keySet()) {
        logger.info(
            "The sink {} in method {} was called with values from the following sources:",
            sink,
            icfg.getMethodOf(sink.getStmt()).getSignature());
        for (ResultSourceInfo source : results.getResults().get(sink)) {
          logger.info(
              "- {} in method {}", source, icfg.getMethodOf(source.getStmt()).getSignature());
          if (source.getPath() != null) {
            logger.info("\ton Path: ");
            boolean hasReturn = false;
            for (Unit p : source.getPath()) {
              logger.info("\t -> " + icfg.getMethodOf(p));
              logger.info("\t\t -> " + p);
              if(p.toString().equals("return")) {
                hasReturn = true;
              }
            }
            if (hasReturn) {
              continue;
            }
            necessarycnt ++;
          }
        }
//        System.out.println("necessarycnt = " + necessarycnt);
      }
    }
  }


}
