/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.publish;

import java.nio.file.Path;
import java.time.Instant;

public class PublishEpubResult {

    private final Path epubPath;
    private final EpubArtifactFingerprint fingerprint;
    private final Instant publishedAt;

    public PublishEpubResult(Path epubPath, EpubArtifactFingerprint fingerprint, Instant publishedAt) {

        this.epubPath = epubPath;
        this.fingerprint = fingerprint;
        this.publishedAt = publishedAt;
    }

    public Path getEpubPath() {

        return epubPath;
    }

    public EpubArtifactFingerprint getFingerprint() {

        return fingerprint;
    }

    public Instant getPublishedAt() {

        return publishedAt;
    }
}