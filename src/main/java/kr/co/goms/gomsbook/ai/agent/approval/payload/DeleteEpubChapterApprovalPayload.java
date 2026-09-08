/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public final class DeleteEpubChapterApprovalPayload {

    private final String projectId;
    private final int partNumber;
    private final int chapterNumber;
    private final String fileName;

    public DeleteEpubChapterApprovalPayload(String projectId, int partNumber, int chapterNumber, String fileName) {

        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank.");
        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");
        if (chapterNumber <= 0) throw new IllegalArgumentException("chapterNumber must be greater than 0.");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (!fileName.trim().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("fileName must have the .xhtml extension.");

        this.projectId = projectId.trim();
        this.partNumber = partNumber;
        this.chapterNumber = chapterNumber;
        this.fileName = fileName.trim();
    }

    public String getProjectId() {
        return projectId;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public int getChapterNumber() {
        return chapterNumber;
    }

    public String getFileName() {
        return fileName;
    }
}