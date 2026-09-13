/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.release;

import java.util.List;
import java.util.Optional;

import kr.co.goms.gomsbook.ai.epub.model.EpubReleaseItem;

public interface EpubReleaseRepository {

    EpubReleaseItem save(EpubReleaseItem item);

    Optional<EpubReleaseItem> findByReleaseId(String releaseId);

    Optional<EpubReleaseItem> findByProjectIdAndVersion(String projectId, String version);

    Optional<EpubReleaseItem> findLatestByProjectId(String projectId);

    List<EpubReleaseItem> findAllByProjectId(String projectId);

}