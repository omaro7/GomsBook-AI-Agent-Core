/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidationResult;

/**
 * EPUB 파일 생성 전 현재 EPUB 프로젝트의 접근성을 검증합니다.
 */
public interface EpubProjectAccessibilityValidator {

    /**
     * 지정된 EPUB 프로젝트의 접근성을 검증합니다.
     *
     * @param projectRoot EPUB 프로젝트 루트 디렉터리
     * @return EPUB 프로젝트 접근성 검증 결과
     */
     AccessibilityValidationResult validate(Path projectRoot);
}