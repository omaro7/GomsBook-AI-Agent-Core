/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

/**
 * {
  "projectId": "epub-ai-agent",
  "version": "1.0.0",
  "epubPath": "C:\\1004.GomsBook\\02.Publish\\epub-ai-agent\\epub-ai-agent-20260913152030.epub",
  "sha256": "6f3a2c...",
  "fileSize": 1843921,
  "publishedAt": "2026-09-13T06:20:30Z"
}

 */
public class ReleaseEpubApprovalPayload {

    private final String projectId;

    private final String version;

    private final String epubPath;

    private final String sha256;

    private final long fileSize;

    private final String publishedAt;

    public ReleaseEpubApprovalPayload(
            String projectId,
            String version,
            String epubPath,
            String sha256,
            long fileSize,
            String publishedAt) {

        this.projectId = projectId;

        this.version = version;

        this.epubPath = epubPath;

        this.sha256 = sha256;

        this.fileSize = fileSize;

        this.publishedAt = publishedAt;

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

}