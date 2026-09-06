/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

import java.util.List;

import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdateItem;

public final class UpdateEpubNavigationApprovalPayload {

    private final String fileName;
    private final List<EpubNavigationUpdateItem> items;

    public UpdateEpubNavigationApprovalPayload(
            String fileName,
            List<EpubNavigationUpdateItem> items) {

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items must not be empty.");

        this.fileName = fileName.trim();
        this.items = List.copyOf(items);
    }

    public String getFileName() {
        return fileName;
    }

    public List<EpubNavigationUpdateItem> toUpdateItems() {
        return items;
    }
}