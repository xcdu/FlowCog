package com.flowcog.result.data;

import java.util.ArrayList;
import java.util.List;

public class TaintedGroupList {

  private List<TaintedGroup> taintedGroups = new ArrayList<>();

  public TaintedGroupList() {
  }

  public TaintedGroupList(List<TaintedGroup> taintedGroups) {
    this.taintedGroups = taintedGroups;
  }

  public List<TaintedGroup> getTaintedGroups() {
    return taintedGroups;
  }

  public void setTaintedGroups(List<TaintedGroup> taintedGroups) {
    this.taintedGroups = taintedGroups;
  }
}
