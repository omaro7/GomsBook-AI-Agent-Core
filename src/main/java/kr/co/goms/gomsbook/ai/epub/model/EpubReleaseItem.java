/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.nio.file.Path;
import java.time.Instant;

public class EpubReleaseItem {

    private final String releaseId;
    private final String projectId;
    private final String version;
    private final Path epubPath;
    private final String sha256;
    private final long fileSize;
    private final Instant publishedAt;
    private final Instant releasedAt;
    private final EpubReleaseStatus status;

    public EpubReleaseItem(String releaseId, String projectId, String version, Path epubPath, String sha256, long fileSize, Instant publishedAt, Instant releasedAt, EpubReleaseStatus status) {

        this.releaseId = releaseId;

        this.projectId = projectId;

        this.version = version;

        this.epubPath = epubPath;

        this.sha256 = sha256;

        this.fileSize = fileSize;

        this.publishedAt = publishedAt;

        this.releasedAt = releasedAt;

        this.status = status;

    }

    public String getReleaseId() {

        return releaseId;

    }

    public String getProjectId() {

        return projectId;

    }

    public String getVersion() {

        return version;

    }

    public Path getEpubPath() {

        return epubPath;

    }

    public String getSha256() {

        return sha256;

    }

    public long getFileSize() {

        return fileSize;

    }

    public Instant getPublishedAt() {

        return publishedAt;

    }

    public Instant getReleasedAt() {

        return releasedAt;

    }

    public EpubReleaseStatus getStatus() {

        return status;

    }

}