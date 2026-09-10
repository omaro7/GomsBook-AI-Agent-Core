/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation.fix;

import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubCheckResult;

public interface EpubFileCheckFixService {

    List<EpubFileCheckFixAction> createFixActions(Path projectRoot, EpubCheckResult checkResult);
}