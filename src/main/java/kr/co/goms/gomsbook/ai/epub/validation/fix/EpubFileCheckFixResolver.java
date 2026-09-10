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

public interface EpubFileCheckFixResolver {

    List<EpubFileCheckFixAction> resolve(Path projectRoot, List<EpubFileCheckFixAction> actions);
}