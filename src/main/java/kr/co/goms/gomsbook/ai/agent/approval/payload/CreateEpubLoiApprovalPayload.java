/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public final class CreateEpubLoiApprovalPayload {

    private String fileName;

    public CreateEpubLoiApprovalPayload() {
    }

    public CreateEpubLoiApprovalPayload(String fileName) {

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");

        this.fileName = fileName.trim();
    }

    public String getFileName() {

        return fileName;
    }

    public void setFileName(String fileName) {

        this.fileName = fileName;
    }

}