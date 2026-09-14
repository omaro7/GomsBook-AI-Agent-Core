/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.rag.eval.path;

import java.nio.file.Path;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;

public final class DefaultRagEvaluationPathResolver
        implements RagEvaluationPathResolver {

    private static final String EVAL_DIRECTORY = "eval";

    private static final String DATASET_DIRECTORY = "dataset";

    private static final String RESULT_DIRECTORY = "result";

    private static final String BASELINE_DIRECTORY = "baseline";

    private static final String REGRESSION_DIRECTORY = "regression";

    private final PublishDirectoryProvider publishDirectoryProvider;

    public DefaultRagEvaluationPathResolver(
            PublishDirectoryProvider publishDirectoryProvider) {

        this.publishDirectoryProvider =
                Objects.requireNonNull(
                        publishDirectoryProvider,
                        "publishDirectoryProvider must not be null"
                );
    }

    @Override
    public Path resolveEvalDirectory(
            String projectId) {

        return resolveProjectDirectory(
                projectId
        ).resolve(
                EVAL_DIRECTORY
        );
    }

    @Override
    public Path resolveDatasetDirectory(
            String projectId) {

        return resolveEvalDirectory(
                projectId
        ).resolve(
                DATASET_DIRECTORY
        );
    }

    @Override
    public Path resolveGoldenDataset(
            String projectId) {

        String normalizedProjectId =
                requireProjectId(
                        projectId
                );

        return resolveDatasetDirectory(
                normalizedProjectId
        ).resolve(
                "rag-"
                        + normalizedProjectId
                        + "-golden-v1.json"
        );
    }

    @Override
    public Path resolveResultDirectory(
            String projectId) {

        return resolveEvalDirectory(
                projectId
        ).resolve(
                RESULT_DIRECTORY
        );
    }

    @Override
    public Path resolveGoldenReport(
            String projectId) {

        String normalizedProjectId =
                requireProjectId(
                        projectId
                );

        return resolveResultDirectory(
                normalizedProjectId
        ).resolve(
                "rag-"
                        + normalizedProjectId
                        + "-golden-v1-report.json"
        );
    }

    @Override
    public Path resolveBaselineDirectory(
            String projectId) {

        return resolveEvalDirectory(
                projectId
        ).resolve(
                BASELINE_DIRECTORY
        );
    }

    @Override
    public Path resolveGoldenBaseline(
            String projectId) {

        String normalizedProjectId =
                requireProjectId(
                        projectId
                );

        return resolveBaselineDirectory(
                normalizedProjectId
        ).resolve(
                "rag-"
                        + normalizedProjectId
                        + "-baseline-v1.json"
        );
    }

    @Override
    public Path resolveRegressionDirectory(
            String projectId) {

        return resolveEvalDirectory(
                projectId
        ).resolve(
                REGRESSION_DIRECTORY
        );
    }

    @Override
    public Path resolveGoldenRegression(
            String projectId) {

        String normalizedProjectId =
                requireProjectId(
                        projectId
                );

        return resolveRegressionDirectory(
                normalizedProjectId
        ).resolve(
                "rag-"
                        + normalizedProjectId
                        + "-regression-v1.json"
        );
    }

    private Path resolveProjectDirectory(
            String projectId) {

        String normalizedProjectId =
                requireProjectId(
                        projectId
                );

        Path publishRoot =
                publishDirectoryProvider
                        .getPublishDirectory();

        if (publishRoot == null) {

            throw new IllegalStateException(
                    "Publish directory is not available."
            );
        }

        return publishRoot
                .resolve(
                        normalizedProjectId
                )
                .normalize();
    }

    private String requireProjectId(
            String projectId) {

        if (projectId == null
                || projectId.isBlank()) {

            throw new IllegalArgumentException(
                    "projectId must not be blank."
            );
        }

        return projectId.trim();
    }
}