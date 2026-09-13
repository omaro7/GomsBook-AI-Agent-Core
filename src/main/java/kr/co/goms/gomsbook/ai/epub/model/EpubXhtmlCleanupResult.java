/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.List;

public record EpubXhtmlCleanupResult(
        int totalCount,
        int updatedCount,
        int unchangedCount,
        int failedCount,
        List<EpubXhtmlCleanupFileResult> files) {

    public EpubXhtmlCleanupResult {
        files = files == null ? List.of() : List.copyOf(files);
    }

    public boolean isSuccess() {
        return failedCount == 0;
    }

    public boolean hasChanges() {
        return updatedCount > 0;
    }

    public static EpubXhtmlCleanupResult of(List<EpubXhtmlCleanupFileResult> files) {
        List<EpubXhtmlCleanupFileResult> safeFiles = files == null ? List.of() : List.copyOf(files);
        int totalCount = safeFiles.size();
        int updatedCount = (int) safeFiles.stream().filter(EpubXhtmlCleanupFileResult::updated).count();
        int failedCount = (int) safeFiles.stream().filter(EpubXhtmlCleanupFileResult::failed).count();
        int unchangedCount = totalCount - updatedCount - failedCount;
        return new EpubXhtmlCleanupResult(totalCount, updatedCount, unchangedCount, failedCount, safeFiles);
    }
}