package com.flowcog.android.data.ui.view.predefine;

import com.flowcog.android.data.SourceSinkDefinitionParser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;

/**
 * Extra sinks used to identify the flows related to dynamically generated view.
 */
public class UIViewRelatedPreDefinedSinks {

  private List<String> extraSinks = Arrays.asList(
      "<android.view.View: void addView(android.view.View,android.view.ViewGroup$LayoutParams)> -> _SINK_",
      "<android.view.View: void addView(android.view.View,int)> -> _SINK_",
      "<android.view.View: void addView(android.view.View,int,android.view.ViewGroup$LayoutParams)> -> _SINK_",
      "<android.view.View: void addView(android.view.View)> -> _SINK_",
      "<android.view.View: void addView(android.view.View,int,int)> -> _SINK_",
      "<android.widget.RelativeLayout: void addView(android.view.View,android.view.ViewGroup$LayoutParams)> -> _SINK_",
      "<android.widget.LinerLayout: void addView(android.view.View,android.view.ViewGroup)> -> _SINK_",
      "<android.app.Activity: void setContentView(android.view.View,android.view.ViewGroup$LayoutParams)> -> _SINK_",
      "<android.app.Activity: void setContentView(android.view.View)> -> _SINK_",
      "<android.app.Activity: void setContentView(int)> -> _SINK_"
//      "<android.app.Activity: android.view.View findViewById(int)> -> _SINK_"
  );

  private Set<SourceSinkDefinition> extraSinksDef = new HashSet<>();

  public UIViewRelatedPreDefinedSinks() {
    SourceSinkDefinitionParser parser = new SourceSinkDefinitionParser();
    for (String sink : extraSinks) {
      extraSinksDef.add(parser.parse(sink));
    }
  }

  public UIViewRelatedPreDefinedSinks(List<String> extraSinks) {
    if(extraSinks != null) {
      this.extraSinks = extraSinks;
    }
    SourceSinkDefinitionParser parser = new SourceSinkDefinitionParser();
    for (String sink : extraSinks) {
      extraSinksDef.add(parser.parse(sink));
    }
  }

  public Set<SourceSinkDefinition> getExtraSinks() {
    return extraSinksDef;
  }

}

