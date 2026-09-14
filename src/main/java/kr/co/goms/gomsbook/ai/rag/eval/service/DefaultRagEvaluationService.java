/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.rag.eval.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.rag.eval.path.RagEvaluationPathResolver;
import kr.co.goms.gomsbook.ai.rag.eval.report.RagEvaluationReport;
import kr.co.goms.gomsbook.ai.rag.eval.runtime.RagEvaluationRuntime;

public final class DefaultRagEvaluationService implements RagEvaluationService {

    private final CurrentProjectProvider projectProvider;
    private final RagEvaluationPathResolver pathResolver;
    private final RagEvaluationRuntime runtime;

    public DefaultRagEvaluationService(CurrentProjectProvider projectProvider, RagEvaluationPathResolver pathResolver, RagEvaluationRuntime runtime) {
        this.projectProvider = Objects.requireNonNull(projectProvider, "projectProvider must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    @Override
    public RagEvaluationReport evaluateGolden() throws IOException {
        String projectId = resolveProjectId();
        Path datasetPath = pathResolver.resolveGoldenDataset(projectId);

        if (!Files.isRegularFile(datasetPath)) {
            throw new IllegalStateException("RAG Golden Dataset not found: " + datasetPath);
        }

        Path reportPath = pathResolver.resolveGoldenReport(projectId);
        Path parent = reportPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        return runtime.evaluate(datasetPath, reportPath);
    }

    private String resolveProjectId() {
        var project = projectProvider.getCurrentProject();

        if (project == null) {
            throw new IllegalStateException("Current project is not available.");
        }

        String projectId = project.getProjectId();

        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("Current projectId is not available.");
        }

        return projectId.trim();
    }
}