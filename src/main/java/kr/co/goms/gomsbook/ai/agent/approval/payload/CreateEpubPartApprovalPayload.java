/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public class CreateEpubPartApprovalPayload {

    private String fileName;
    private int partNumber;
    private String title;
    private String stylesheetHref;
    private String xhtml;

    public CreateEpubPartApprovalPayload() {
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStylesheetHref() {
        return stylesheetHref;
    }

    public void setStylesheetHref(String stylesheetHref) {
        this.stylesheetHref = stylesheetHref;
    }

    public String getXhtml() {
        return xhtml;
    }

    public void setXhtml(String xhtml) {
        this.xhtml = xhtml;
    }
}