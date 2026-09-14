/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.rag.eval.path;

import java.nio.file.Path;

/**
 * RAG Evaluation 경로 Resolver.
 *
 * 기존 버전 미지정 호출은 v1을 사용한다.
 * 버전 지정 호출은 v2, v3 등 향후 Golden Dataset 버전 확장에 사용한다.
 */
public interface RagEvaluationPathResolver {

	Path resolveEvalDirectory(String projectId);

	Path resolveDatasetDirectory(String projectId);

	default Path resolveGoldenDataset(String projectId) {
		return resolveGoldenDataset(projectId, 1);
	}

	Path resolveGoldenDataset(String projectId, int version);

	int resolveLatestGoldenVersion(String projectId);

	default Path resolveLatestGoldenDataset(String projectId) {
		return resolveGoldenDataset(projectId, resolveLatestGoldenVersion(projectId));
	}

	Path resolveResultDirectory(String projectId);

	default Path resolveGoldenReport(String projectId) {
		return resolveGoldenReport(projectId, 1);
	}

	Path resolveGoldenReport(String projectId, int version);

	Path resolveBaselineDirectory(String projectId);

	default Path resolveGoldenBaseline(String projectId) {
		return resolveGoldenBaseline(projectId, 1);
	}

	Path resolveGoldenBaseline(String projectId, int version);

	Path resolveRegressionDirectory(String projectId);

	default Path resolveGoldenRegression(String projectId) {
		return resolveGoldenRegression(projectId, 1);
	}

	Path resolveGoldenRegression(String projectId, int version);
}