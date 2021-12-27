package com.flowcog.android.data.ui.view.predefine;

import com.flowcog.android.data.SourceSinkDefinitionParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;

public class UIViewRelatedPreDefinedSources {

  private List<String> extraSources = Arrays.asList(
      "<android.app.Activity: android.view.View findViewById(int)> -> _SOURCE_",
      "<android.content.res.Resources: int getIdentifier(String,String,String)> -> _SOURCE_"
  );
  private Set<SourceSinkDefinition> extraSourcesDef = new HashSet<>();


  public UIViewRelatedPreDefinedSources() {
    SourceSinkDefinitionParser parser = new SourceSinkDefinitionParser();
    for (String source : extraSources) {
      extraSourcesDef.add(parser.parse(source));
    }
  }

  public UIViewRelatedPreDefinedSources(List<String> extraSources) {
    if(extraSources != null) {
      this.extraSources = extraSources;
    }
    SourceSinkDefinitionParser parser = new SourceSinkDefinitionParser();
    for (String source : extraSources) {
      extraSourcesDef.add(parser.parse(source));
    }
  }

  public Set<SourceSinkDefinition> getExtraSources() {
    return extraSourcesDef;
  }
}
