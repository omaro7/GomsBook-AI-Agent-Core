/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public record CleanEpubXhtmlApprovalPayload(String scope, String fileName) {

    public static final String SCOPE_TEXT = "TEXT";

    public static final String SCOPE_FILE = "FILE";

    public static CleanEpubXhtmlApprovalPayload cleanText() {
        return new CleanEpubXhtmlApprovalPayload(SCOPE_TEXT, null);
    }

    public static CleanEpubXhtmlApprovalPayload cleanFile(String fileName) {
        return new CleanEpubXhtmlApprovalPayload(SCOPE_FILE, fileName);
    }

    public boolean isTextScope() {
        return SCOPE_TEXT.equalsIgnoreCase(scope);
    }

    public boolean isFileScope() {
        return SCOPE_FILE.equalsIgnoreCase(scope);
    }
}