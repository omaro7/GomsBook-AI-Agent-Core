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


    public UpdateEpubPartApprovalPayload() {
    }


    public UpdateEpubPartApprovalPayload(
            int partNumber,
            String fileName,
            String title) {

        this.partNumber = partNumber;

        this.fileName = fileName;

        this.title = title;
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
}