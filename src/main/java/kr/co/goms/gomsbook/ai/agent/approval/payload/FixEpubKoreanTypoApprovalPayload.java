/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public record FixEpubKoreanTypoApprovalPayload(
        String fileName,
        String original,
        String suggestion,
        int startOffset,
        int endOffset) {

    public FixEpubKoreanTypoApprovalPayload {

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank.");
        }

        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("original must not be blank.");
        }

        if (suggestion == null || suggestion.isBlank()) {
            throw new IllegalArgumentException("suggestion must not be blank.");
        }

        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be greater than or equal to 0.");
        }

        if (endOffset <= startOffset) {
            throw new IllegalArgumentException("endOffset must be greater than startOffset.");
        }

        fileName = fileName.trim();
    }
}