/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.rag.graph.epub;

import java.util.Locale;
import java.util.Set;

import kr.co.goms.gomsbook.ai.rag.util.RagUtil;

/**
 * EPUB Graph 구조 문서 제외 기본 정책.
 *
 * <p>
 * RAG 전체 검색 대상 여부와 Graph 참여 여부는 서로 다른 정책이다.
 * </p>
 */
public final class DefaultEpubGraphDocumentPolicy implements EpubGraphDocumentPolicy {

	private static final Set<String> EXCLUDED_DOCUMENTS = Set.of("cover.xhtml", "nav.xhtml", "author.xhtml", "copyright.xhtml");

	@Override
	public boolean isEligible(String sourcePath) {

		String normalized = RagUtil.normalizeDocumentPath(sourcePath);

		if (normalized == null || RagUtil.isExcludedDocument(normalized)) return false;

		String fileName = extractFileName(normalized).toLowerCase(Locale.ROOT);

		if (EXCLUDED_DOCUMENTS.contains(fileName)) return false;
		if (fileName.startsWith("part") && fileName.endsWith(".xhtml")) return false;

		return true;
	}

	private String extractFileName(String sourcePath) {

		int index = sourcePath.lastIndexOf('/');

		return index >= 0 ? sourcePath.substring(index + 1) : sourcePath;
	}
}