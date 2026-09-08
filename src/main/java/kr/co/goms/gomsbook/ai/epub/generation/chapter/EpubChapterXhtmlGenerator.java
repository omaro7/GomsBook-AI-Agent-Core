/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.chapter;

public interface EpubChapterXhtmlGenerator {

    String generate(int partNumber, int chapterNumber, String title, String content, String stylesheetHref);
}