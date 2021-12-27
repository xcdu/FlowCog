package com.flowcog.android.data.ui.view;

import com.flowcog.android.data.ui.view.predefine.UIViewRelatedPreDefinedSinks;
import com.flowcog.android.data.ui.view.predefine.UIViewRelatedPreDefinedSources;
import java.util.HashSet;
import java.util.Set;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;

public class UIViewRelatedSourceSinkManager {

  UIViewRelatedTaintDefinition sourcesDef = null;
  UIViewRelatedTaintDefinition sinksDef = null;

  public UIViewRelatedSourceSinkManager(Set<SourceSinkDefinition> originalSources,
      Set<SourceSinkDefinition> originalSinks) {
    //

    if (originalSources == null) {
      originalSources = new HashSet<>();
    }
    UIViewRelatedPreDefinedSources extraSources = new UIViewRelatedPreDefinedSources();
    this.sourcesDef = new UIViewRelatedTaintDefinition(originalSources,
        extraSources.getExtraSources());

    if (originalSinks == null) {
      originalSinks = new HashSet<>();
    }
    UIViewRelatedPreDefinedSinks extraSinks = new UIViewRelatedPreDefinedSinks();
    this.sinksDef = new UIViewRelatedTaintDefinition(originalSinks, extraSinks.getExtraSinks());
  }

  public UIViewRelatedTaintDefinition getSources() {
    return sourcesDef;
  }

  public UIViewRelatedTaintDefinition getSinks() {
    return sinksDef;
  }
}

