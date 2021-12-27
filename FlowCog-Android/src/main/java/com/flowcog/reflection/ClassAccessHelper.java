package com.flowcog.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.android.SetupApplication;

public class ClassAccessHelper {

  private static final Logger logger = LoggerFactory.getLogger(ClassAccessHelper.class);

  public static Object newInstance(String className, Object... objects) {
    Object object = null;
    try {
      logger.debug("creating class {}", className);
      Class<?> cls = Class.forName(className);
      Constructor<?> constructor = null;
      if (objects.length == 0) {
        constructor = cls.getDeclaredConstructor();
      } else {
        Class<?>[] params = new Class<?>[objects.length];
        for (int i = 0; i < objects.length; ++i) {
          params[i] = objects[i].getClass();
        }
        constructor = cls.getDeclaredConstructor(params);
      }
      constructor.setAccessible(true);
      object = constructor.newInstance(objects);
    } catch (Exception e) {
      // TODO: 2018/5/15 Add ClassNotFoundException handler
      e.printStackTrace();
    }
    return object;
  }

  public static Method accessMethod(Object objectInstance, String methodName) {
    try {
      Method m = objectInstance.getClass().getDeclaredMethod(methodName);
      m.setAccessible(true);
      return m;
    } catch (NoSuchMethodException e) {
      // TODO: 2018/6/27 Add NoSuchMethodException handler
      e.printStackTrace();
    }
    return null;
  }


  public static void main(String[] args) throws Exception {
    String targetApk = "InsecureBank.apk";
    String androidDir = "D:\\apps\\Android\\sdk\\platforms";
//    Class<?> cls = Class.forName("soot.jimple.infoflow.android.SetupApplication");
//    Constructor<?> constructor = cls.getDeclaredConstructor(String.class, String.class);
    SetupApplication setupApplication = (SetupApplication) ClassAccessHelper
        .newInstance(SetupApplication.class.getName(), targetApk, androidDir);
    setupApplication.runInfoflow();
  }
}
