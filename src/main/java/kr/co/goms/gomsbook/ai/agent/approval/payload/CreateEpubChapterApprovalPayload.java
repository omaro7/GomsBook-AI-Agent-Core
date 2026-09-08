/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public final class CreateEpubChapterApprovalPayload {

    private final int partNumber;
    private final int chapterNumber;
    private final String fileName;
    private final String title;
    private final String content;

    public CreateEpubChapterApprovalPayload(int partNumber, int chapterNumber, String fileName, String title, String content) {

        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");
        if (chapterNumber <= 0) throw new IllegalArgumentException("chapterNumber must be greater than 0.");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank.");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content must not be blank.");

        this.partNumber = partNumber;
        this.chapterNumber = chapterNumber;
        this.fileName = fileName.trim();
        this.title = title.trim();
        this.content = content;
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

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}