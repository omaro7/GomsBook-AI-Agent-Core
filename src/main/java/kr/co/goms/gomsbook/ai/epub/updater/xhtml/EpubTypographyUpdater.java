/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.updater.xhtml;

import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyOperation;
import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyUpdateResult;

public interface EpubTypographyUpdater {

    EpubTypographyUpdateResult preview(Path projectRoot, EpubTypographyOperation operation, String fileName);

    EpubTypographyUpdateResult update(Path projectRoot, EpubTypographyOperation operation, String fileName);
}