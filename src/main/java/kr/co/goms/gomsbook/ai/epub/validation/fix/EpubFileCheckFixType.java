/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation.fix;

public enum EpubFileCheckFixType {

    UPDATE_METADATA,

    UPDATE_MANIFEST,

    UPDATE_SPINE,

    UPDATE_NAVIGATION,

    UPDATE_XHTML,

    UPDATE_STYLESHEET,

    UPDATE_RESOURCE,

    INSPECT_REQUIRED,

    MANUAL_REQUIRED,

    UNKNOWN
}