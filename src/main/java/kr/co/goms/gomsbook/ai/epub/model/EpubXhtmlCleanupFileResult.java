/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

public record EpubXhtmlCleanupFileResult(String fileName, boolean updated, boolean failed, String message) {

    public EpubXhtmlCleanupFileResult {
        fileName = fileName == null ? "" : fileName;
        message = message == null ? "" : message;
    }

    public static EpubXhtmlCleanupFileResult updated(String fileName) {
        return new EpubXhtmlCleanupFileResult(fileName, true, false, "정리 완료");
    }

    public static EpubXhtmlCleanupFileResult unchanged(String fileName) {
        return new EpubXhtmlCleanupFileResult(fileName, false, false, "변경 없음");
    }

    public static EpubXhtmlCleanupFileResult failed(String fileName, String message) {
        return new EpubXhtmlCleanupFileResult(fileName, false, true, message);
    }

    public boolean unchanged() {
        return !updated && !failed;
    }
}