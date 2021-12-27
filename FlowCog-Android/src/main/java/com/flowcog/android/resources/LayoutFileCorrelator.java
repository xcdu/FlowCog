package com.flowcog.android.resources;

import com.flowcog.android.resources.structure.AxmlTupleNodeConverter;
import com.flowcog.android.resources.structure.AxmlValueTuple;
import com.flowcog.android.resources.structure.tree.AxmlHierarchyTree;
import com.flowcog.android.resources.structure.tree.AxmlHierarchyTreeNode;
import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pxb.android.axml.AxmlVisitor;
import soot.jimple.infoflow.android.axml.AXmlAttribute;
import soot.jimple.infoflow.android.axml.AXmlHandler;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.axml.parsers.AXML20Parser;
import soot.jimple.infoflow.android.resources.AbstractResourceParser;
import soot.jimple.infoflow.android.resources.IResourceHandler;


public class LayoutFileCorrelator extends AbstractResourceParser {

  private static final Logger logger = LoggerFactory.getLogger(LayoutFileCorrelator.class);
  private Map<Integer, AxmlValueTuple> idToValueTuple;
  private AxmlHierarchyTree axmlHierarchyTree = new AxmlHierarchyTree();


  // for temporary usage in parse include attribute in layout files
  private ZipFile archive;
  private Map<String, ZipEntry> nameEntryMappingInZipFile;


  public LayoutFileCorrelator() {
    // TODO: 2018/7/12 to be disabled
  }

  public LayoutFileCorrelator(Map<Integer, AxmlValueTuple> idToValueTuple) {
    this.idToValueTuple = idToValueTuple;
  }

  private void parseLayoutNode(Integer axmlHierarchyTreeNodeId,
      AXmlNode currentParsedAxmlTreeNode) {
    if (currentParsedAxmlTreeNode.getTag() == null || currentParsedAxmlTreeNode.getTag()
        .isEmpty()) {
      logger.warn("Encountered a null or empty node name in file %s, skipping node...");
      return;
    }
    String tagName = currentParsedAxmlTreeNode.getTag().trim();
    // Check for inclusions
    if (tagName.equals("include")) {
      parseIncludeAttributes(axmlHierarchyTreeNodeId, currentParsedAxmlTreeNode);
    } else {
      parseLayoutAttributes(axmlHierarchyTreeNodeId, currentParsedAxmlTreeNode);
    }

    // Parse the child nodes
    for (AXmlNode parsedChildNode : currentParsedAxmlTreeNode.getChildren()) {
      AxmlHierarchyTreeNode mirroredChildNode = new AxmlHierarchyTreeNode();
      axmlHierarchyTree.addNode(axmlHierarchyTreeNodeId, mirroredChildNode);
      parseLayoutNode(mirroredChildNode.getId(), parsedChildNode);
    }
  }


  private void parseLayoutAttributes(Integer axmlHierarchyTreeNodeId,
      AXmlNode currentParsedAxmlTreeNode) {
    logger.debug("parsing layout node attributes");
    if (currentParsedAxmlTreeNode == null) {
      logger.warn("Argument node is null");
      return;
    }
    for (Entry<String, AXmlAttribute<?>> entry : currentParsedAxmlTreeNode.getAttributes()
        .entrySet()) {
      String attrName = entry.getKey().trim();
      AXmlAttribute<?> attribute = entry.getValue();
      if (attrName.equals("id") && attribute.getType() == AxmlVisitor.TYPE_INT_HEX && attribute
          .getValue() instanceof Integer) {
        Integer id = (Integer) attribute.getValue();
        AxmlValueTuple tuple = idToValueTuple.get(id);
        if (tuple != null) {
          AxmlTupleNodeConverter
              .copyFromTupleToNode(tuple, axmlHierarchyTree.findNodeById(axmlHierarchyTreeNodeId));
        }
      }

      if (attrName.equals("text")) {
        if (attribute.getType() == AxmlVisitor.TYPE_STRING && attribute
            .getValue() instanceof String) {
          String textString = (String) attribute.getValue();
          axmlHierarchyTree.findNodeById(axmlHierarchyTreeNodeId).setValue(textString);
          axmlHierarchyTree.findNodeById(axmlHierarchyTreeNodeId).setType("string");
        } else if (attribute.getType() == AxmlVisitor.TYPE_INT_HEX && attribute
            .getValue() instanceof Integer) {
          Integer textId = (Integer) attribute.getValue();
          AxmlValueTuple tuple = idToValueTuple.get(textId);
          if (tuple != null) {
            AxmlTupleNodeConverter.copyFromTupleToNode(tuple,
                axmlHierarchyTree.findNodeById(axmlHierarchyTreeNodeId));
          }
        }
      }

    }

  }

  private void parseIncludeAttributes(Integer axmlHierarchyTreeNodeId, AXmlNode rootNode) {
    for (Entry<String, AXmlAttribute<?>> entry : rootNode.getAttributes().entrySet()) {
      String attrName = entry.getKey().trim();
      AXmlAttribute<?> attribute = entry.getValue();
      if (attrName.equals("layout")) {
        if (attribute.getType() == AxmlVisitor.TYPE_REFERENCE
            || attribute.getType() == AxmlVisitor.TYPE_INT_HEX && attribute
            .getValue() instanceof Integer) {
          String targetFile = idToValueTuple.get((Integer)attribute.getValue()).getValue();
          try {
            ZipEntry zipEntry = nameEntryMappingInZipFile.get(targetFile);
            InputStream inputStream = archive.getInputStream(zipEntry);
            // add a empty node as connect node between layout file that includes other layout and
            // included layout
            AxmlHierarchyTreeNode node = new AxmlHierarchyTreeNode();
            axmlHierarchyTree.addNode(axmlHierarchyTreeNodeId, node);
            AXmlHandler handler = new AXmlHandler(inputStream, new AXML20Parser());
            parseLayoutNode(node.getId(), handler.getDocument().getRootNode());
          } catch (Exception e) {
            logger.error("Error when looking for " + targetFile + " resource files in apk ", e);
            throw new RuntimeException(e);
          }

        }
      }
    }
  }

  private Integer findIdByValue(String value) {
    for (Integer id : idToValueTuple.keySet()) {
      if (idToValueTuple.get(id).getValue().equals(value)) {
        return id;
      }
    }
    return null;
  }

  public void parse(final String fileName) {

    File apkFile = new File(fileName);
    if(!apkFile.exists()) {
      throw new RuntimeException("File '" + fileName + "' does not exist!");
    }

    try {
      archive = null;
      try {
        archive = new ZipFile(apkFile);
        nameEntryMappingInZipFile = new HashMap<>();
        Enumeration<?> entries = archive.entries();
        while(entries.hasMoreElements()) {
          ZipEntry entry = (ZipEntry) entries.nextElement();
          String entryName = entry.getName();
          nameEntryMappingInZipFile.put(entryName, entry);
        }

        handleAndroidResourceFiles(fileName, null, new IResourceHandler() {
          @Override
          public void handleResourceFile(String fileName, Set<String> fileNameFilter,
              InputStream stream) {
            // We only process valid layout XML files
            if (!fileName.startsWith("res/layout")) {
              return;
            }
            if (!fileName.endsWith(".xml")) {
              logger.warn("Skipping file %s in layout folder...", fileName);
              return;
            }

            try {
              AXmlHandler handler = new AXmlHandler(stream, new AXML20Parser());

              Integer layoutId = findIdByValue(fileName);
              AxmlHierarchyTreeNode layoutNode = AxmlTupleNodeConverter
                  .toAxmlTreeNode(idToValueTuple.get(layoutId));
              if (layoutNode == null) {
                return;
              }
              axmlHierarchyTree.addNode(axmlHierarchyTree.getRoot(), layoutNode);
              parseLayoutNode(layoutNode.getId(), handler.getDocument().getRootNode());

            } catch (Exception e) {
              logger.error("Could not read binary XML file: " + e.getMessage(), e);
            }
          }
        });
      }finally {
        if (archive != null) {
          archive.close();
        }
        archive = null;
      }

    } catch (Exception e) {
      logger.error("Error when looking for XML resource files in apk " + fileName, e);
      if (e instanceof RuntimeException)
        throw (RuntimeException) e;
      else
        throw new RuntimeException(e);
    }
  }

  public AxmlHierarchyTree getAxmlHierarchyTree() {
    return axmlHierarchyTree;
  }
}
