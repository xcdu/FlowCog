package com.flowcog.android.resources.structure.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AxmlHierarchyTree {

  private AxmlHierarchyTreeNode root;
  private Map<Integer, AxmlHierarchyTreeNode> idMap = new HashMap<>();
  private Integer nextId = 0;

  public AxmlHierarchyTree() {
    root = new AxmlHierarchyTreeNode(nextId);
    register(root);
  }

  private void register(AxmlHierarchyTreeNode node) {
    if (node.getId() != null) {
      nextId = nextId > node.getId() ? nextId : node.getId();
    } else {
      node.setId(nextId);
    }
    idMap.put(nextId, node);
    nextId++;
  }

  public AxmlHierarchyTreeNode findNodeById(Integer id) {
    if (idMap.keySet().contains(id)) {
      return idMap.get(id);
    }
    return root.findNodeById(id);
  }

  public AxmlHierarchyTreeNode findNodeByResourceId(Integer resourceId) {
    return root.findNodeByResourceId(resourceId);
  }

  public boolean addNode(AxmlHierarchyTreeNode parent, AxmlHierarchyTreeNode child) {
    return addNode(parent.getId(), child);
  }

  public boolean addNode(Integer parentId, AxmlHierarchyTreeNode child) {
    AxmlHierarchyTreeNode parent = root.findNodeById(parentId);
    if (parent == null) {
      return false;
    }
    parent.addChildNode(child);
    register(child);
    return true;
  }

  public void traverse() {
    traverse(root);
  }

  private void traverse(AxmlHierarchyTreeNode root) {
    System.out.print(root.getId() + "(" + root.getResourceId() + ")" + " - >");
    for (AxmlHierarchyTreeNode child : root.getChildNodes()) {
      System.out.print(" " + child.getId() + "(" + child.getResourceId() + ")");
    }
    System.out.println();
    for (AxmlHierarchyTreeNode child : root.getChildNodes()) {
      traverse(child);
    }
  }

  public AxmlHierarchyTreeNode getRoot() {
    return root;
  }

  public List<String> resolveDirectlyByResourceId(Integer resourceId) {
    AxmlHierarchyTreeNode node = findNodeByResourceId(resourceId);
    if (node == null) {
      return new ArrayList<>();
    }
    return node.getLayoutStrings();
  }

  public List<String> resolveByResourceId(Integer resourceId) {
    AxmlHierarchyTreeNode node = findNodeByResourceId(resourceId);
    if (node == null) {
      return new ArrayList<>();
    }
    if (node.getType().equals("layout")) {
      return node.getLayoutStrings();
    } else {

      List<String> layoutStrings = new ArrayList<>();
      for (AxmlHierarchyTreeNode child : node.getParentNode().getChildNodes()) {
        layoutStrings.addAll(child.getLayoutStrings());
      }
      return layoutStrings;
    }
  }

  public List<String> resolveRelatedStringsByResourceId(Integer resourceId) {
    AxmlHierarchyTreeNode node = findNodeByResourceId(resourceId);
    if (node == null) {
      return new ArrayList<>();
    }

    if (node.getType().equals("layout")) {
      return node.getLayoutStrings();
    } else {
      AxmlHierarchyTreeNode found = node;
      while (!found.getParentNode().getId().equals(0)) {
        found = found.getParentNode();
      }
      if (found.getType() != null && found.getType().equals("layout")) {
        return found.getLayoutStrings();
      } else {
        List<String> layoutStrings = new ArrayList<>();
        for (AxmlHierarchyTreeNode child : node.getParentNode().getChildNodes()) {
          layoutStrings.addAll(child.getLayoutStrings());
        }
        return layoutStrings;
      }
    }
  }

  public Set<Integer> getAllIds() {
    if (idMap == null) {
      return new HashSet<>();
    }
    return idMap.keySet();
  }

  public Set<Integer> getAllResourceIds() {
    if(idMap == null) {
      return new HashSet<>();
    }
    Set<Integer> resourceIds = new HashSet<>();
    for(Integer id: idMap.keySet()) {
      Integer resourceId = findNodeById(id).getResourceId();
      resourceIds.add(resourceId);
    }
    return resourceIds;
  }
}
