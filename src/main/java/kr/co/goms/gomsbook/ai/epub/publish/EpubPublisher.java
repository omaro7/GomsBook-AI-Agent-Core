/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.publish;

import java.io.IOException;
import java.nio.file.Path;

public interface EpubPublisher {

	Path publish() throws IOException;
}