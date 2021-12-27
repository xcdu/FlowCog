package com.flowcog.android.data.ui.view;

import java.util.HashSet;
import java.util.Set;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;

public class UIViewRelatedTaintDefinition {
  private Set<SourceSinkDefinition> original = null;
  private Set<SourceSinkDefinition> extra = null;


  public UIViewRelatedTaintDefinition(Set<SourceSinkDefinition> original,
      Set<SourceSinkDefinition> extra) {
    this.original = original;
    this.extra = extra;
  }


  public Set<SourceSinkDefinition> getOriginal() {
    return original;
  }

  public Set<SourceSinkDefinition> getExtra() {
    return extra;
  }

  public Set<SourceSinkDefinition> getComplete() {
    Set<SourceSinkDefinition> complete = new HashSet<>();
    complete.addAll(original);
    complete.addAll(extra);
    return complete;
  }

  public Set<SourceSinkDefinition> getConflict(){
    Set<SourceSinkDefinition> intersection= new HashSet<>();
    intersection.addAll(extra);
    intersection.retainAll(original);
    return intersection;
  }

}
