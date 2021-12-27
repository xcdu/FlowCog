package com.flowcog.android.resources.structure;

import com.flowcog.android.resources.structure.tree.AxmlHierarchyTreeNode;

public class AxmlTupleNodeConverter {

  public static AxmlHierarchyTreeNode toAxmlTreeNode(AxmlValueTuple tuple) {
    if (tuple == null) {
      return null;
    }
    return new AxmlHierarchyTreeNode(null, tuple.getId(), tuple.getName(), tuple.getType(),
        tuple.getValue());
  }

  public static AxmlValueTuple toAxmlValueTuple(AxmlHierarchyTreeNode node) {
    return new AxmlValueTuple(node.getResourceId(), node.getName(), node.getType(),
        node.getValue());

  }

  public static void copyFromTupleToNode(AxmlValueTuple tuple, AxmlHierarchyTreeNode node) {
    if (tuple == null) {
      return;
    }
    if (tuple.getId() != null) {
      node.setResourceId(tuple.getId());
    }

    if (tuple.getName() != null) {
      node.setName(tuple.getName());
    }

    node.setType(tuple.getType());
    node.setValue(tuple.getValue());
  }

}
