/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EPUB 프로젝트 구조 및 정합성 검증 결과를 나타냅니다.
 */
public final class EpubProjectValidationResult {

    private final Path projectRoot;
    private final Path packagePath;
    private final List<EpubProjectValidationIssue> issues;

    private EpubProjectValidationResult(Builder builder) {
        this.projectRoot = builder.projectRoot;
        this.packagePath = builder.packagePath;
        this.issues = Collections.unmodifiableList(new ArrayList<>(builder.issues));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public Path getPackagePath() {
        return packagePath;
    }

    public List<EpubProjectValidationIssue> getIssues() {
        return issues;
    }

    public boolean isValid() {
        return getErrorCount() == 0;
    }

    public int getIssueCount() {
        return issues.size();
    }

    public int getErrorCount() {
        return (int) issues.stream().filter(EpubProjectValidationIssue::isError).count();
    }

    public int getWarningCount() {
        return (int) issues.stream().filter(EpubProjectValidationIssue::isWarning).count();
    }

    public String createSummary() {
        return "EPUB project validation completed: valid=" + isValid()
                + ", errors=" + getErrorCount()
                + ", warnings=" + getWarningCount()
                + ", issues=" + getIssueCount();
    }

    public static final class Builder {

        private Path projectRoot;
        private Path packagePath;
        private final List<EpubProjectValidationIssue> issues = new ArrayList<>();

        private Builder() {
        }

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder packagePath(Path packagePath) {
            this.packagePath = packagePath;
            return this;
        }

        public Builder issue(EpubProjectValidationIssue issue) {
            if (issue != null) issues.add(issue);
            return this;
        }

        public Builder issues(List<EpubProjectValidationIssue> issues) {
            if (issues != null) for (EpubProjectValidationIssue issue : issues) if (issue != null) this.issues.add(issue);
            return this;
        }

        public EpubProjectValidationResult build() {
            if (projectRoot == null) throw new IllegalStateException("projectRoot must not be null.");
            return new EpubProjectValidationResult(this);
        }
    }
}