package com.flowcog.android.resources;

import com.flowcog.android.resources.structure.AxmlValueTuple;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.AnalysisFileConfiguration;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.ARSCFileParser.AbstractResource;
import soot.jimple.infoflow.android.resources.ARSCFileParser.ResPackage;
import soot.jimple.infoflow.android.resources.ARSCFileParser.ResType;

public class AxmlResourceCollector {

  private static final Logger logger = LoggerFactory.getLogger(AxmlResourceCollector.class);

  private final AnalysisFileConfiguration config;
  private final ProcessManifest manifest;
  private ARSCFileParser arscFileParser = null;
  private LayoutFileCorrelator layoutFileCorrelator = null;

  private Map<Integer, AxmlValueTuple> idToValueTuple = new HashMap<>();

  public AxmlResourceCollector(AnalysisFileConfiguration config)
      throws IOException, XmlPullParserException {
    this.config = config;
    this.manifest = new ProcessManifest(config.getTargetAPKFile());
    parseResources();
    correlateLayoutAndStrings();
  }

  private void parseResources() throws IOException {
    if (arscFileParser == null) {
      this.arscFileParser = new ARSCFileParser();
    }
    arscFileParser.parse(config.getTargetAPKFile());
    for (ResPackage resPackage : arscFileParser.getPackages()) {
      for (ResType resType : resPackage.getDeclaredTypes()) {
        for (AbstractResource resource : resType.getAllResources()) {
          Integer id = resource.getResourceID();
          String name = resource.getResourceName();
          String type = arscFileParser.findResourceType(id).getTypeName();
          String value = resource.toString();
          AxmlValueTuple axmlValueTuple = new AxmlValueTuple(id, name, type, value);
          idToValueTuple.put(id, axmlValueTuple);
        }
      }
    }
  }

  private void correlateLayoutAndStrings() {
    if (layoutFileCorrelator == null) {
      layoutFileCorrelator = new LayoutFileCorrelator(idToValueTuple);
    }
    layoutFileCorrelator.parse(config.getTargetAPKFile());
  }


  public List<String> resolve(Integer id) {
    System.out.println("id = " + id);
    return layoutFileCorrelator.getAxmlHierarchyTree().resolveDirectlyByResourceId(id);
  }

  public List<String> resolveRelatedStrings(Integer id) {
    System.out.println("id = " + id);
    return layoutFileCorrelator.getAxmlHierarchyTree().resolveRelatedStringsByResourceId(id);
  }

  public List<String> getAllLayoutStrings() {
    Set<String> allLayouts = new HashSet<>();
    for (Integer resourceId : layoutFileCorrelator.getAxmlHierarchyTree().getAllResourceIds()) {
      allLayouts
          .addAll(layoutFileCorrelator.getAxmlHierarchyTree().resolveByResourceId(resourceId));
    }
    return new ArrayList<>(allLayouts);
  }
}
