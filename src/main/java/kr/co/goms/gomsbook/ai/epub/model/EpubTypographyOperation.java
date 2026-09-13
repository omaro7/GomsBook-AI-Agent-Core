/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

public enum EpubTypographyOperation {

    SINGLE_QUOTES,

    DOUBLE_QUOTES,

    ELLIPSIS,

    ALL;

    public boolean includesSingleQuotes() {
        return this == SINGLE_QUOTES || this == ALL;
    }

    public boolean includesDoubleQuotes() {
        return this == DOUBLE_QUOTES || this == ALL;
    }

    public boolean includesEllipsis() {
        return this == ELLIPSIS || this == ALL;
    }

    public static EpubTypographyOperation from(String value) {
        if (value == null || value.isBlank()) return ALL;

        return EpubTypographyOperation.valueOf(value.trim().toUpperCase());
    }
}