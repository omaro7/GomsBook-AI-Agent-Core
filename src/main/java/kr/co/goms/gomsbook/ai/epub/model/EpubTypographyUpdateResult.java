/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Collections;
import java.util.List;

public record EpubTypographyUpdateResult(List<EpubTypographyChangeItem> items) {

    public EpubTypographyUpdateResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public int getFileCount() {
        return (int) items.stream().filter(EpubTypographyChangeItem::isChanged).map(EpubTypographyChangeItem::fileName).distinct().count();
    }

    public int getChangeCount() {
        return items.stream().filter(EpubTypographyChangeItem::isChanged).mapToInt(EpubTypographyChangeItem::changeCount).sum();
    }

    public int getSingleQuoteCount() {
        return items.stream().filter(EpubTypographyChangeItem::isChanged).filter(item -> item.operation() == EpubTypographyOperation.SINGLE_QUOTES).mapToInt(EpubTypographyChangeItem::changeCount).sum();
    }

    public int getDoubleQuoteCount() {
        return items.stream().filter(EpubTypographyChangeItem::isChanged).filter(item -> item.operation() == EpubTypographyOperation.DOUBLE_QUOTES).mapToInt(EpubTypographyChangeItem::changeCount).sum();
    }

    public int getEllipsisCount() {
        return items.stream().filter(EpubTypographyChangeItem::isChanged).filter(item -> item.operation() == EpubTypographyOperation.ELLIPSIS).mapToInt(EpubTypographyChangeItem::changeCount).sum();
    }

    public boolean hasChanges() {
        return items.stream().anyMatch(EpubTypographyChangeItem::isChanged);
    }

    public List<EpubTypographyChangeItem> getChangedItems() {
        return items.stream().filter(EpubTypographyChangeItem::isChanged).toList();
    }

    public List<EpubTypographyChangeItem> getPreviewItems(int limit) {
        if (limit <= 0) return Collections.emptyList();

        return items.stream().filter(EpubTypographyChangeItem::isChanged).limit(limit).toList();
    }
}