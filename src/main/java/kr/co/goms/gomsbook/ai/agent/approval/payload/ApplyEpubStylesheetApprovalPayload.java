/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

import java.util.Objects;

public final class ApplyEpubStylesheetApprovalPayload {

    private String sourcePath;

    private String targetFileName;

    public ApplyEpubStylesheetApprovalPayload() {
    }

    public ApplyEpubStylesheetApprovalPayload(String sourcePath, String targetFileName) {

        this.sourcePath = requireText(sourcePath, "sourcePath");

        this.targetFileName = requireText(targetFileName, "targetFileName");
    }

    public String getSourcePath() {

        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {

        this.sourcePath = requireText(sourcePath, "sourcePath");
    }

    public String getTargetFileName() {

        return targetFileName;
    }

    public void setTargetFileName(String targetFileName) {

        this.targetFileName = requireText(targetFileName, "targetFileName");
    }

    private static String requireText(String value, String name) {

        Objects.requireNonNull(value, name);

        String normalized = value.trim();

        if (normalized.isEmpty()) {

            throw new IllegalArgumentException(name + " must not be empty.");
        }

        return normalized;
    }
}