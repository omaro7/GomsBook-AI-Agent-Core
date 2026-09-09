/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

/**
 * {
  "fileName": "nav.xhtml",
  "elementName": "nav",
  "matchAttributeName": "epub:type",
  "matchAttributeValue": "toc",
  "attributeName": "role",
  "attributeValue": "doc-toc"
}
 */
public class UpdateEpubXhtmlAttributeApprovalPayload {

    private String fileName;

    private String elementName;

    private String matchAttributeName;

    private String matchAttributeValue;

    private String attributeName;

    private String attributeValue;

    public UpdateEpubXhtmlAttributeApprovalPayload() {
    }

    public UpdateEpubXhtmlAttributeApprovalPayload(String fileName, String elementName, String matchAttributeName, String matchAttributeValue, String attributeName, String attributeValue) {

        this.fileName = fileName;

        this.elementName = elementName;

        this.matchAttributeName = matchAttributeName;

        this.matchAttributeValue = matchAttributeValue;

        this.attributeName = attributeName;

        this.attributeValue = attributeValue;
    }

    public String getFileName() {

        return fileName;
    }

    public void setFileName(String fileName) {

        this.fileName = fileName;
    }

    public String getElementName() {

        return elementName;
    }

    public void setElementName(String elementName) {

        this.elementName = elementName;
    }

    public String getMatchAttributeName() {

        return matchAttributeName;
    }

    public void setMatchAttributeName(String matchAttributeName) {

        this.matchAttributeName = matchAttributeName;
    }

    public String getMatchAttributeValue() {

        return matchAttributeValue;
    }

    public void setMatchAttributeValue(String matchAttributeValue) {

        this.matchAttributeValue = matchAttributeValue;
    }

    public String getAttributeName() {

        return attributeName;
    }

    public void setAttributeName(String attributeName) {

        this.attributeName = attributeName;
    }

    public String getAttributeValue() {

        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {

        this.attributeValue = attributeValue;
    }
}