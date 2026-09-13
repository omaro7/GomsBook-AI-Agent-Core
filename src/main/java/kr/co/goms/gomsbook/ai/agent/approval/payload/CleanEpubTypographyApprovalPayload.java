/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyOperation;

public record CleanEpubTypographyApprovalPayload(EpubTypographyOperation operation, String fileName) {

    public CleanEpubTypographyApprovalPayload {
        if (operation == null) operation = EpubTypographyOperation.ALL;
        if (fileName != null && fileName.isBlank()) fileName = null;
    }

    public static CleanEpubTypographyApprovalPayload cleanAll() {
        return new CleanEpubTypographyApprovalPayload(EpubTypographyOperation.ALL, null);
    }

    public static CleanEpubTypographyApprovalPayload cleanAll(EpubTypographyOperation operation) {
        return new CleanEpubTypographyApprovalPayload(operation, null);
    }

    public static CleanEpubTypographyApprovalPayload cleanFile(String fileName) {
        return new CleanEpubTypographyApprovalPayload(EpubTypographyOperation.ALL, fileName);
    }

    public static CleanEpubTypographyApprovalPayload cleanFile(EpubTypographyOperation operation, String fileName) {
        return new CleanEpubTypographyApprovalPayload(operation, fileName);
    }
}