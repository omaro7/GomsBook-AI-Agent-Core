/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.rag.graph.epub;

/**
 * EPUB Graph에 참여할 수 있는 문서를 판정하는 정책.
 */
public interface EpubGraphDocumentPolicy {

	boolean isEligible(String sourcePath);
}