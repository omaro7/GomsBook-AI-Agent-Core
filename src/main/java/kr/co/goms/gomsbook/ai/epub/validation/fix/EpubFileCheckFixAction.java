/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation.fix;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.validation.EpubFileCheckIssueCategory;

public final class EpubFileCheckFixAction {

    private final String issueId;
    private final EpubFileCheckIssueCategory category;
    private final EpubFileCheckFixType fixType;
    private final String toolName;
    private final String operation;
    private final String reason;
    private final Map<String, Object> arguments;

    public EpubFileCheckFixAction(
            String issueId,
            EpubFileCheckIssueCategory category,
            EpubFileCheckFixType fixType,
            String toolName,
            String operation,
            String reason,
            Map<String, Object> arguments) {

        this.issueId = issueId;
        this.category = Objects.requireNonNull(category, "category must not be null.");
        this.fixType = Objects.requireNonNull(fixType, "fixType must not be null.");
        this.toolName = trimToNull(toolName);
        this.operation = trimToNull(operation);
        this.reason = trimToNull(reason);
        this.arguments = immutableArguments(arguments);
    }

    public String getIssueId() {
        return issueId;
    }

    public EpubFileCheckIssueCategory getCategory() {
        return category;
    }

    public EpubFileCheckFixType getFixType() {
        return fixType;
    }

    public String getToolName() {
        return toolName;
    }

    public String getOperation() {
        return operation;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public boolean requiresInspection() {
        return fixType == EpubFileCheckFixType.INSPECT_REQUIRED;
    }

    public boolean requiresManualFix() {
        return fixType == EpubFileCheckFixType.MANUAL_REQUIRED;
    }

    public boolean hasTool() {
        return toolName != null;
    }

    private static Map<String, Object> immutableArguments(Map<String, Object> arguments) {

        if (arguments == null || arguments.isEmpty()) return Map.of();

        return Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    private static String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}