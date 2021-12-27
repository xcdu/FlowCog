package com.flowcog.result.data;

import soot.SootMethod;
import soot.Unit;

public class UnitWithMethod {

  private Unit unit;
  private SootMethod method;

  public UnitWithMethod() {
  }

  public UnitWithMethod(Unit unit, SootMethod method) {
    this.unit = unit;
    this.method = method;
  }

  public Unit getUnit() {
    return unit;
  }

  public void setUnit(Unit unit) {
    this.unit = unit;
  }

  public SootMethod getMethod() {
    return method;
  }

  public void setMethod(SootMethod method) {
    this.method = method;
  }

  @Override
  public String toString() {
    return String.format("%s @ %s", unit, method);
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof UnitWithMethod)) {
      return false;
    }
    UnitWithMethod o = (UnitWithMethod) obj;
    return this.toString().equals(o.toString());
  }
}
