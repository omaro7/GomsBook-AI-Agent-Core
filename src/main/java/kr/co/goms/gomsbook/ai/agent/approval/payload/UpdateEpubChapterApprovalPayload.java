/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public class UpdateEpubChapterApprovalPayload {

    private String fileName;

    private String title;

    private String content;

    private String stylesheetHref;

    private String xhtml;

    public UpdateEpubChapterApprovalPayload() {
    }

    public UpdateEpubChapterApprovalPayload(String fileName, String title, String content, String stylesheetHref, String xhtml) {

        this.fileName = fileName;
        this.title = title;
        this.content = content;
        this.stylesheetHref = stylesheetHref;
        this.xhtml = xhtml;
    }

    public String getFileName() {

        return fileName;
    }

    public void setFileName(String fileName) {

        this.fileName = fileName;
    }

    public String getTitle() {

        return title;
    }

    public void setTitle(String title) {

        this.title = title;
    }

    public String getContent() {

        return content;
    }

    public void setContent(String content) {

        this.content = content;
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