/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

/**
 * EPUB spine 수정 작업 유형입니다.
 */
public enum UpdateEpubSpineOperation {

    ADD,
    DELETE,
    MOVE
}