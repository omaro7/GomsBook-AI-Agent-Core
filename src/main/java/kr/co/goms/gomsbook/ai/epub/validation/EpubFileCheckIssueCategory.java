/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

public enum EpubFileCheckIssueCategory {

    PACKAGE,

    METADATA,

    MANIFEST,

    SPINE,

    NAVIGATION,

    XHTML,

    STYLESHEET,

    IMAGE,

    RESOURCE,

    UNKNOWN
}