/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.rag.eval.path;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.rag.util.RagUtil;

/**
 * RAG Evaluation 경로 기본 Resolver.
 
	DefaultRagEvaluationPathResolver
	        │
	        └─ resolveLatestGoldenVersion()
	              ↓
	             1, 2, 3 ...
	
	RagEvaluationPathResolver default
	        │
	        └─ resolveLatestGoldenDataset()
	              ↓
	        resolveGoldenDataset(projectId, latestVersion)
         
 
	dataset/
	├─ rag-lunchwork_seoul-golden-v1.json
	├─ rag-lunchwork_seoul-golden-v2.json
	├─ rag-lunchwork_seoul-golden-v3.json
	└─ rag-lunchwork_seoul-golden-v10.json  >> 최신
	
	pathResolver.resolveGoldenDataset("lunchwork_seoul", 2);
	
	pathResolver.resolveLatestGoldenVersion("lunchwork_seoul"); >>> 10

 */
public final class DefaultRagEvaluationPathResolver implements RagEvaluationPathResolver {

	private static final String EVAL_DIRECTORY = "eval";
	private static final String DATASET_DIRECTORY = "dataset";
	private static final String RESULT_DIRECTORY = "result";
	private static final String BASELINE_DIRECTORY = "baseline";
	private static final String REGRESSION_DIRECTORY = "regression";

	private final PublishDirectoryProvider publishDirectoryProvider;

	public DefaultRagEvaluationPathResolver(PublishDirectoryProvider publishDirectoryProvider) {
		this.publishDirectoryProvider = Objects.requireNonNull(publishDirectoryProvider, "publishDirectoryProvider must not be null");
	}

	@Override
	public Path resolveEvalDirectory(String projectId) {
		return resolveProjectDirectory(projectId).resolve(EVAL_DIRECTORY);
	}

	@Override
	public Path resolveDatasetDirectory(String projectId) {
		return resolveEvalDirectory(projectId).resolve(DATASET_DIRECTORY);
	}

	@Override
	public Path resolveGoldenDataset(String projectId, int version) {
		String normalizedProjectId = RagUtil.requireText(projectId, "projectId");
		int normalizedVersion = requireVersion(version);
		return resolveDatasetDirectory(normalizedProjectId).resolve("rag-" + normalizedProjectId + "-golden-v" + normalizedVersion + ".json");
	}

	@Override
	public int resolveLatestGoldenVersion(String projectId) {
		String normalizedProjectId = RagUtil.requireText(projectId, "projectId");
		Path datasetDirectory = resolveDatasetDirectory(normalizedProjectId);

		if (!Files.isDirectory(datasetDirectory)) throw new IllegalStateException("RAG evaluation dataset directory does not exist: " + datasetDirectory);

		Pattern pattern = Pattern.compile("^rag-" + Pattern.quote(normalizedProjectId) + "-golden-v(\\d+)\\.json$");

		try (Stream<Path> paths = Files.list(datasetDirectory)) {
			return paths.filter(Files::isRegularFile).map(Path::getFileName).map(Path::toString).map(pattern::matcher).filter(Matcher::matches).mapToInt(matcher -> parseVersion(matcher.group(1))).max().orElseThrow(() -> new IllegalStateException("RAG Golden Dataset not found: " + datasetDirectory));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to resolve latest RAG Golden Dataset version: " + datasetDirectory, e);
		}
	}

	@Override
	public Path resolveResultDirectory(String projectId) {
		return resolveEvalDirectory(projectId).resolve(RESULT_DIRECTORY);
	}

	@Override
	public Path resolveGoldenReport(String projectId, int version) {
		String normalizedProjectId = RagUtil.requireText(projectId, "projectId");
		int normalizedVersion = requireVersion(version);
		return resolveResultDirectory(normalizedProjectId).resolve("rag-" + normalizedProjectId + "-golden-v" + normalizedVersion + "-report.json");
	}

	@Override
	public Path resolveBaselineDirectory(String projectId) {
		return resolveEvalDirectory(projectId).resolve(BASELINE_DIRECTORY);
	}

	@Override
	public Path resolveGoldenBaseline(String projectId, int version) {
		String normalizedProjectId = RagUtil.requireText(projectId, "projectId");
		int normalizedVersion = requireVersion(version);
		return resolveBaselineDirectory(normalizedProjectId).resolve("rag-" + normalizedProjectId + "-baseline-v" + normalizedVersion + ".json");
	}

	@Override
	public Path resolveRegressionDirectory(String projectId) {
		return resolveEvalDirectory(projectId).resolve(REGRESSION_DIRECTORY);
	}

	@Override
	public Path resolveGoldenRegression(String projectId, int version) {
		String normalizedProjectId = RagUtil.requireText(projectId, "projectId");
		int normalizedVersion = requireVersion(version);
		return resolveRegressionDirectory(normalizedProjectId).resolve("rag-" + normalizedProjectId + "-regression-v" + normalizedVersion + ".json");
	}

	private Path resolveProjectDirectory(String projectId) {
		String normalizedProjectId = RagUtil.requireText(projectId, "projectId");
		Path publishRoot = publishDirectoryProvider.getPublishDirectory();

		if (publishRoot == null) throw new IllegalStateException("Publish directory is not available.");

		return publishRoot.resolve(normalizedProjectId).normalize();
	}

	private static int requireVersion(int version) {
		if (version <= 0) throw new IllegalArgumentException("version must be greater than zero");
		return version;
	}

	private static int parseVersion(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalStateException("Invalid RAG Golden Dataset version: " + value, e);
		}
	}
}