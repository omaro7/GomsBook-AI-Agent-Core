/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation.fix;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubCheckResult;
import kr.co.goms.gomsbook.ai.epub.validation.EpubFileCheckIssue;
import kr.co.goms.gomsbook.ai.epub.validation.EpubFileCheckIssueAnalyzer;

public final class DefaultEpubFileCheckFixService implements EpubFileCheckFixService {

    private final EpubFileCheckIssueAnalyzer issueAnalyzer;
    private final EpubFileCheckFixPlan fixPlan;
    private final EpubFileCheckFixResolver fixResolver;

    public DefaultEpubFileCheckFixService(
            EpubFileCheckIssueAnalyzer issueAnalyzer,
            EpubFileCheckFixPlan fixPlan,
            EpubFileCheckFixResolver fixResolver) {

        this.issueAnalyzer = Objects.requireNonNull(issueAnalyzer, "issueAnalyzer must not be null.");
        this.fixPlan = Objects.requireNonNull(fixPlan, "fixPlan must not be null.");
        this.fixResolver = Objects.requireNonNull(fixResolver, "fixResolver must not be null.");
    }

    @Override
    public List<EpubFileCheckFixAction> createFixActions(Path projectRoot, EpubCheckResult checkResult) {

        Path normalizedProjectRoot = requireProjectRoot(projectRoot);
        Objects.requireNonNull(checkResult, "checkResult must not be null.");

        List<EpubFileCheckIssue> issues = issueAnalyzer.analyze(checkResult);

        if (issues.isEmpty()) return List.of();

        List<EpubFileCheckFixAction> plannedActions = fixPlan.plan(issues);

        if (plannedActions.isEmpty()) return List.of();

        return fixResolver.resolve(normalizedProjectRoot, plannedActions);
    }

    private Path requireProjectRoot(Path projectRoot) {

        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");

        Path normalized = projectRoot.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) throw new IllegalStateException("EPUB project root does not exist: " + normalized);
        if (!Files.isDirectory(normalized)) throw new IllegalStateException("EPUB project root is not a directory: " + normalized);

        return normalized;
    }
}