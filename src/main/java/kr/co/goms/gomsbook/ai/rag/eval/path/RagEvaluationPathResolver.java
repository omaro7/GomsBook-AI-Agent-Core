/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.rag.eval.path;

import java.nio.file.Path;

public interface RagEvaluationPathResolver {

    Path resolveEvalDirectory(String projectId);

    Path resolveDatasetDirectory(String projectId);

    Path resolveGoldenDataset(String projectId);

    Path resolveResultDirectory(String projectId);

    Path resolveGoldenReport(String projectId);

    Path resolveBaselineDirectory(String projectId);

    Path resolveGoldenBaseline(String projectId);

    Path resolveRegressionDirectory(String projectId);

    Path resolveGoldenRegression(String projectId);
}