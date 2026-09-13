/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

public record EpubTypographyChangeItem(String fileName, EpubTypographyOperation operation, String before, String after, int changeCount) {

    public EpubTypographyChangeItem {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (operation == null) throw new IllegalArgumentException("operation must not be null.");
        if (before == null) throw new IllegalArgumentException("before must not be null.");
        if (after == null) throw new IllegalArgumentException("after must not be null.");
        if (changeCount < 0) throw new IllegalArgumentException("changeCount must not be negative.");
    }

    public boolean isChanged() {
        return changeCount > 0 && !before.equals(after);
    }
}