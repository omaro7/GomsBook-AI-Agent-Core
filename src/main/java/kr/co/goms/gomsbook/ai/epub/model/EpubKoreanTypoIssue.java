/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

public record EpubKoreanTypoIssue(
        String file,
        String original,
        String suggestion,
        String type,
        String reason,
        double confidence,
        int line,
        int startOffset,
        int endOffset) {
}