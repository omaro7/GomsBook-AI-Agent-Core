/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.List;

public record EpubKoreanTypoCheckResult(
        int checkedFileCount,
        int issueCount,
        List<EpubKoreanTypoIssue> issues) {

    public EpubKoreanTypoCheckResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}