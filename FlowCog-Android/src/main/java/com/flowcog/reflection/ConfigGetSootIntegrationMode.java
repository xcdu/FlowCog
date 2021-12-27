package com.flowcog.reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.SootIntegrationMode;

/**
 * Fetch unreachable method inside default-modified internal enum class.
 * Original method "config.getSootIntegrationMode().needsToBuildCallGraph()"
 */
public class ConfigGetSootIntegrationMode {
  private SootIntegrationMode instance = null;

  public ConfigGetSootIntegrationMode(SootIntegrationMode instance) {
    this.instance = instance;
  }

  public boolean needsToBuildCallgraph()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    if (instance == null) {
      instance = SootIntegrationMode.CreateNewInstace;
    }
    Method m = instance.getClass().getDeclaredMethod("needsToBuildCallgraph");
    m.setAccessible(true);
    return (boolean)m.invoke(instance);
  }
}
