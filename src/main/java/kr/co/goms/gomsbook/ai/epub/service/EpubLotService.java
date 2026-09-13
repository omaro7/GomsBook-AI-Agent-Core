/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.epub.model.EpubLotResult;

public interface EpubLotService {

    EpubLotResult create(Path projectRoot);

}