/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.release;

public class EpubReleaseRepositoryItem {

    private String releaseId;

    private String projectId;

    private String version;

    private String epubPath;

    private String sha256;

    private long fileSize;

    private String publishedAt;

    private String releasedAt;

    private String status;

    public EpubReleaseRepositoryItem() {

    }

    public EpubReleaseRepositoryItem(
            String releaseId,
            String projectId,
            String version,
            String epubPath,
            String sha256,
            long fileSize,
            String publishedAt,
            String releasedAt,
            String status) {

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

    public String getEpubPath() {

        return epubPath;

    }

    public String getSha256() {

        return sha256;

    }

    public long getFileSize() {

        return fileSize;

    }

    public String getPublishedAt() {

        return publishedAt;

    }

    public String getReleasedAt() {

        return releasedAt;

    }

    public String getStatus() {

        return status;

    }

}