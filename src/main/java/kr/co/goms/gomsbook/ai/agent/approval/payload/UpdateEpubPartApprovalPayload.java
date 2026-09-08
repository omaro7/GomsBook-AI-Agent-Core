/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public class UpdateEpubPartApprovalPayload {

    private int partNumber;
    private String fileName;
    private String title;
    private String xhtml;

    public UpdateEpubPartApprovalPayload() {
    }

    public UpdateEpubPartApprovalPayload(int partNumber, String fileName, String title, String xhtml) {
        this.partNumber = partNumber;
        this.fileName = fileName;
        this.title = title;
        this.xhtml = xhtml;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getXhtml() {
        return xhtml;
    }
}