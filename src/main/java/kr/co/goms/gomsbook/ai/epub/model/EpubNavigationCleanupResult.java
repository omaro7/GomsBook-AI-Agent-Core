/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

public class EpubNavigationCleanupResult {

    private final int removedListItems;
    private final int removedLists;

    public EpubNavigationCleanupResult(int removedListItems, int removedLists) {
        this.removedListItems = removedListItems;
        this.removedLists = removedLists;
    }

    public int getRemovedListItems() {
        return removedListItems;
    }

    public int getRemovedLists() {
        return removedLists;
    }

    public boolean isChanged() {
        return removedListItems > 0 || removedLists > 0;
    }
}