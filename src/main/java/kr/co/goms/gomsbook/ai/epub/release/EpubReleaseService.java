/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.release;

import java.nio.file.Path;
import java.time.Instant;

import kr.co.goms.gomsbook.ai.epub.model.EpubReleaseItem;

public interface EpubReleaseService {

    EpubReleaseItem release(String projectId, String version, Path epubPath, String sha256, long fileSize, Instant publishedAt);

}