package com.flowcog.android.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.sourcesSinks.definitions.MethodSourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkType;

public class SourceSinkDefinitionParser {

  private final String regex = "^<(.+):\\s*(.+)\\s+(.+)\\s*\\((.*)\\)>\\s*(.*?)(\\s+->\\s+(.*))?$";
  private final String regexNoRet = "^<(.+):\\s*(.+)\\s*\\((.*)\\)>\\s*(.*?)?(\\s+->\\s+(.*))?$";

  private AndroidMethod createMethod(Matcher m){
    AndroidMethod androidMethod = parseMethod(m, true);
    return androidMethod;
  }

  private AndroidMethod parseMethod(Matcher m, boolean hasReturnType) {
    assert (m.group(1) != null && m.group(2) != null && m.group(3) != null && m.group(4) != null);
    AndroidMethod singleMethod;
    int groupIdx = 1;

    // class name
    String className = m.group(groupIdx++).trim();

    String returnType = "";
    if (hasReturnType) {
      // return type
      returnType = m.group(groupIdx++).trim();
    }

    // method name
    String methodName = m.group(groupIdx++).trim();

    // method parameter
    List<String> methodParameters = new ArrayList<>();
    String params = m.group(groupIdx++).trim();
    if (!params.isEmpty())
      for (String parameter : params.split(","))
        methodParameters.add(parameter.trim());

    // permissions
    String classData = "";
    String permData = "";
    Set<String> permissions = null;

    if (groupIdx < m.groupCount() && m.group(groupIdx) != null) {
      permData = m.group(groupIdx);
      if (permData.contains("->")) {
        classData = permData.replace("->", "").trim();
        permData = "";
      }
      groupIdx++;
    }
    if (!permData.isEmpty()) {
      permissions = new HashSet<String>();
      for (String permission : permData.split(" "))
        permissions.add(permission);
    }

    // create method signature
    singleMethod = new AndroidMethod(methodName, methodParameters, returnType, className, permissions);

    if (classData.isEmpty())
      if (m.group(groupIdx) != null) {
        classData = m.group(groupIdx).replace("->", "").trim();
        groupIdx++;
      }
    if (!classData.isEmpty())
      for (String target : classData.split("\\s")) {
        target = target.trim();

        // Throw away categories
        if (target.contains("|"))
          target = target.substring(target.indexOf('|'));

        if (!target.isEmpty() && !target.startsWith("|")) {
          if (target.equals("_SOURCE_"))
            singleMethod.setSourceSinkType(SourceSinkType.Source);
          else if (target.equals("_SINK_"))
            singleMethod.setSourceSinkType(SourceSinkType.Sink);
          else if (target.equals("_NONE_"))
            singleMethod.setSourceSinkType(SourceSinkType.Neither);
          else
            throw new RuntimeException("error in target definition: " + target);
        }
      }
    return singleMethod;
  }

  public SourceSinkDefinition parse(String definition) {
    Pattern p = Pattern.compile(regex);
    Pattern pNoRet = Pattern.compile(regexNoRet);
    AndroidMethod androidMethod = null;
    if (definition.isEmpty() || definition.startsWith("%"))
      return null;
    Matcher m = p.matcher(definition);
    if (m.find()) {
      androidMethod = createMethod(m);
    } else {
      Matcher mNoRet = pNoRet.matcher(definition);
      if (mNoRet.find()) {
        androidMethod = createMethod(mNoRet);
      } else {
        // TODO: 2018/7/27 Modify output here
        System.out.println("Source/Sink definition not match: " + definition);
      }
    }

    if (androidMethod == null) {
      return null;
    }
    return new MethodSourceSinkDefinition(androidMethod);
  }
}
