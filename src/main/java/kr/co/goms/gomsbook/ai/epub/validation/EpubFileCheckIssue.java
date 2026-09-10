/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.util.Objects;

public final class EpubFileCheckIssue {

    private final String id;
    private final String severity;
    private final String file;
    private final Integer line;
    private final Integer column;
    private final String message;
    private final EpubFileCheckIssueCategory category;

    public EpubFileCheckIssue(
            String id,
            String severity,
            String file,
            Integer line,
            Integer column,
            String message,
            EpubFileCheckIssueCategory category) {

        this.id = id;
        this.severity = severity;
        this.file = file;
        this.line = line;
        this.column = column;
        this.message = message;
        this.category = Objects.requireNonNull(category, "category must not be null.");
    }

    public String getId() {
        return id;
    }

    public String getSeverity() {
        return severity;
    }

    public String getFile() {
        return file;
    }

    public Integer getLine() {
        return line;
    }

    public Integer getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    public EpubFileCheckIssueCategory getCategory() {
        return category;
    }

    @Override
    public String toString() {

        return "EpubFileCheckIssue{"
                + "id='" + id + '\''
                + ", severity='" + severity + '\''
                + ", file='" + file + '\''
                + ", line=" + line
                + ", column=" + column
                + ", message='" + message + '\''
                + ", category=" + category
                + '}';
    }
}