/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;


/**
 * EPUBCheck가 반환한 개별 검증 메시지를 표현합니다.
 *
 * <p>메시지 ID, severity, message, location 정보를 보관합니다.</p>
 */
public final class EpubCheckMessage {


    private final String id;

    private final String severity;

    private final String message;

    private final String location;


    public EpubCheckMessage(
            String id,
            String severity,
            String message,
            String location) {

        this.id = trimToNull(id);

        this.severity = normalizeSeverity(severity);

        this.message = requireText(message, "message");

        this.location = trimToNull(location);
    }


    public String getId() {

        return id;
    }


    public String getSeverity() {

        return severity;
    }


    public String getMessage() {

        return message;
    }


    public Optional<String> getLocation() {

        return Optional.ofNullable(location);
    }


    public boolean isFatal() {

        return "FATAL".equals(severity);
    }


    public boolean isError() {

        return "ERROR".equals(severity);
    }


    public boolean isWarning() {

        return "WARNING".equals(severity);
    }


    public boolean isUsage() {

        return "USAGE".equals(severity);
    }


    public boolean isInfo() {

        return "INFO".equals(severity);
    }


    @Override
    public boolean equals(
            Object object) {

        if (this == object) {

            return true;
        }

        if (!(object instanceof EpubCheckMessage other)) {

            return false;
        }

        return Objects.equals(id, other.id)
                && Objects.equals(severity, other.severity)
                && Objects.equals(message, other.message)
                && Objects.equals(location, other.location);
    }


    @Override
    public int hashCode() {

        return Objects.hash(id, severity, message, location);
    }


    @Override
    public String toString() {

        return "EpubCheckMessage{"
                + "id='" + id + '\''
                + ", severity='" + severity + '\''
                + ", message='" + message + '\''
                + ", location='" + location + '\''
                + '}';
    }


    private static String normalizeSeverity(
            String value) {

        String severity = requireText(value, "severity").toUpperCase(Locale.ROOT);

        if ("FATAL".equals(severity)) {

            return severity;
        }

        if ("ERROR".equals(severity)) {

            return severity;
        }

        if ("WARNING".equals(severity)) {

            return severity;
        }

        if ("USAGE".equals(severity)) {

            return severity;
        }

        if ("INFO".equals(severity)) {

            return severity;
        }

        throw new IllegalArgumentException("Unsupported EPUBCheck severity: " + value);
    }


    private static String requireText(
            String value,
            String name) {

        String normalized = trimToNull(value);

        if (normalized == null) {

            throw new IllegalArgumentException(name + " must not be empty.");
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
}