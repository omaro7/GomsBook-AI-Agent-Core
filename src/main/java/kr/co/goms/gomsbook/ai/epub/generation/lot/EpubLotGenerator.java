/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.lot;

import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubLotItem;

public interface EpubLotGenerator {

    String generate(List<EpubLotItem> items, String stylesheetHref);

}