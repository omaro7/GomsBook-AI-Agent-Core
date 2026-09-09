/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

import java.util.List;
import java.util.Locale;

import kr.co.goms.gomsbook.ai.epub.updater.navigation.EpubNavigationUpdateItem;

public final class UpdateEpubNavigationApprovalPayload {

    private final String operation;
    private final String fileName;
    private final String href;
    private final List<EpubNavigationUpdateItem> items;

    public UpdateEpubNavigationApprovalPayload(
            String operation,
            String fileName,
            String href,
            List<EpubNavigationUpdateItem> items) {

        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation must not be blank.");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");

        String normalizedOperation = operation.trim().toUpperCase(Locale.ROOT);

        if (!isSupportedOperation(normalizedOperation)) throw new IllegalArgumentException("Unsupported navigation operation: " + operation);

        if ("REMOVE".equals(normalizedOperation) && (href == null || href.isBlank())) {
            throw new IllegalArgumentException("href must not be blank for REMOVE operation.");
        }

        if (("ADD".equals(normalizedOperation) || "UPDATE".equals(normalizedOperation)) && (items == null || items.isEmpty())) {
            throw new IllegalArgumentException("items must not be empty for " + normalizedOperation + " operation.");
        }

        this.operation = normalizedOperation;
        this.fileName = fileName.trim();
        this.href = href == null || href.isBlank() ? null : href.trim();
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public String getOperation() {
        return operation;
    }

    public String getFileName() {
        return fileName;
    }

    public String getHref() {
        return href;
    }

    public List<EpubNavigationUpdateItem> toUpdateItems() {
        return items;
    }

    public boolean isAdd() {
        return "ADD".equals(operation);
    }

    public boolean isUpdate() {
        return "UPDATE".equals(operation);
    }

    public boolean isRemove() {
        return "REMOVE".equals(operation);
    }

    public boolean isCleanup() {
        return "CLEANUP".equals(operation);
    }

    private boolean isSupportedOperation(String operation) {
        return "ADD".equals(operation)
                || "UPDATE".equals(operation)
                || "REMOVE".equals(operation)
                || "CLEANUP".equals(operation);
    }
}