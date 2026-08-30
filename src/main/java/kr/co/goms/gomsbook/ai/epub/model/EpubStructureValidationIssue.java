/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Objects;
import java.util.Optional;


/**
 * EPUB 구조 검증에서 발견된 단일 문제를 표현합니다.
 *
 * <p>하나의 Issue는 code, severity, message, target 정보를 가집니다.</p>
 */
public final class EpubStructureValidationIssue {


    private final String code;

    private final String severity;

    private final String message;

    private final String target;


    public EpubStructureValidationIssue(
            String code,
            String severity,
            String message,
            String target) {

        this.code = requireText(code, "code");

        this.severity = normalizeSeverity(severity);

        this.message = requireText(message, "message");

        this.target = trimToNull(target);
    }


    public String getCode() {

        return code;
    }


    public String getSeverity() {

        return severity;
    }


    public String getMessage() {

        return message;
    }


    public Optional<String> getTarget() {

        return Optional.ofNullable(target);
    }


    public boolean isError() {

        return "ERROR".equals(severity);
    }


    public boolean isWarning() {

        return "WARNING".equals(severity);
    }


    private static String normalizeSeverity(
            String value) {

        String normalized = requireText(value, "severity").toUpperCase();

        if ("ERROR".equals(normalized)) {

            return normalized;
        }

        if ("WARNING".equals(normalized)) {

            return normalized;
        }

        throw new IllegalArgumentException(
                "Unsupported EPUB structure validation severity: " + value);
    }


    private static String requireText(
            String value,
            String name) {

        String normalized = trimToNull(value);

        if (normalized == null) {

            throw new IllegalArgumentException(
                    name + " must not be empty.");
        }

        return normalized;
    }


    private static String trimToNull(
            String value) {

        if (value == null) {

            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }


    @Override
    public boolean equals(
            Object object) {

        if (this == object) {

            return true;
        }

        if (!(object instanceof EpubStructureValidationIssue other)) {

            return false;
        }

        return Objects.equals(code, other.code)
                && Objects.equals(severity, other.severity)
                && Objects.equals(message, other.message)
                && Objects.equals(target, other.target);
    }


    @Override
    public int hashCode() {

        return Objects.hash(code, severity, message, target);
    }


    @Override
    public String toString() {

        return "EpubStructureValidationIssue{"
                + "code='" + code + '\''
                + ", severity='" + severity + '\''
                + ", message='" + message + '\''
                + ", target='" + target + '\''
                + '}';
    }
}