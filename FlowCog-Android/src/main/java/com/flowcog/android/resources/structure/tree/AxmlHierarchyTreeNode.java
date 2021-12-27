package com.flowcog.android.resources.structure.tree;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class AxmlHierarchyTreeNode {

  private Integer id;
  private Integer resourceId;
  private String name;
  private String type;
  private String value;
  private AxmlHierarchyTreeNode parentNode;
  private List<AxmlHierarchyTreeNode> childNodes = new ArrayList<>();

  public AxmlHierarchyTreeNode() {
  }

  public AxmlHierarchyTreeNode(Integer id) {
    this.id = id;
  }

  public AxmlHierarchyTreeNode(Integer id, Integer resourceId, String name, String type,
      String value) {
    this.id = id;
    this.resourceId = resourceId;
    this.name = name;
    this.type = type;
    this.value = value;
  }

  public void addChildNode(AxmlHierarchyTreeNode child) {
    childNodes.add(child);
    child.parentNode = this;
  }

  public AxmlHierarchyTreeNode findNodeById(Integer id) {
    return findNodeById(this, id);
  }

  private AxmlHierarchyTreeNode findNodeById(AxmlHierarchyTreeNode root, Integer id) {
    if (root == null) {
      return null;
    }
    if (root.id != null && root.id.equals(id)) {
      return root;
    }
    for (AxmlHierarchyTreeNode child : root.childNodes) {
      AxmlHierarchyTreeNode found = findNodeById(child, id);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  public AxmlHierarchyTreeNode findNodeByResourceId(Integer resourceId) {
    Queue<AxmlHierarchyTreeNode> queue = new LinkedList<>();
    queue.add(this);
    while (!queue.isEmpty()) {
      AxmlHierarchyTreeNode currentNode = queue.poll();
      if (currentNode != null && currentNode.getResourceId() != null && currentNode.getResourceId().equals(resourceId)) {
        return currentNode;
      }
      if (currentNode ==null || currentNode.getChildNodes() == null) {
        continue;
      }
      for (AxmlHierarchyTreeNode child: currentNode.getChildNodes()) {
        queue.add(child);
      }
    }
    return null;
  }


  public List<String> getLayoutStrings() {
    if(this.getParentNode() != null) {
      return getLayoutStrings(this.getParentNode(), new ArrayList<>());
    } else {
      return getLayoutStrings(this, new ArrayList<>());
    }
  }

  private List<String> getLayoutStrings(AxmlHierarchyTreeNode root, List<String> layoutStrings) {
    if (root == null) {
      return null;
    }
    if (root.value != null && root.value.length() > 0 && root.type != null && root.type
        .equals("string")) {
      layoutStrings.add(root.value);
    }

    for (AxmlHierarchyTreeNode childNode : root.getChildNodes()) {
      getLayoutStrings(childNode, layoutStrings);
    }

    return layoutStrings;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getResourceId() {
    return resourceId;
  }

  public void setResourceId(Integer resourceId) {
    this.resourceId = resourceId;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public AxmlHierarchyTreeNode getParentNode() {
    return parentNode;
  }

  public void setParentNode(AxmlHierarchyTreeNode parentNode) {
    this.parentNode = parentNode;
  }

  public List<AxmlHierarchyTreeNode> getChildNodes() {
    return childNodes;
  }

  public void setChildNodes(
      List<AxmlHierarchyTreeNode> childNodes) {
    this.childNodes = childNodes;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "AxmlHierarchyTreeNode{" +
        "id=" + id +
        ", resourceId=" + resourceId +
        ", name='" + name + '\'' +
        ", value='" + value + '\'' +
        ", type='" + type + '\'' +
        ", parentNode=" + parentNode +
        ", childNodes=" + childNodes +
        '}';
  }
}
