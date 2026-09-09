/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Objects;

/**
 * EPUB 프로젝트 접근성 검증 중 발견된 개별 이슈를 나타냅니다.
 */
public final class EpubProjectAccessibilityValidationIssue {

    private final Severity severity;
    private final String code;
    private final String message;

    public EpubProjectAccessibilityValidationIssue(Severity severity, String code, String message) {
        this.severity = Objects.requireNonNull(severity, "severity must not be null.");
        this.code = requireText(code, "code");
        this.message = requireText(message, "message");
    }

    public static EpubProjectAccessibilityValidationIssue error(String code, String message) {
        return new EpubProjectAccessibilityValidationIssue(Severity.ERROR, code, message);
    }

    public static EpubProjectAccessibilityValidationIssue warning(String code, String message) {
        return new EpubProjectAccessibilityValidationIssue(Severity.WARNING, code, message);
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");
        return value.trim();
    }

    public enum Severity {
        ERROR,
        WARNING
    }
}