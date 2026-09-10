/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.publish;

public class EpubArtifactFingerprint {

    private final String sha256;
    private final long fileSize;

    public EpubArtifactFingerprint(String sha256, long fileSize) {

        this.sha256 = sha256;
        this.fileSize = fileSize;
    }

    public String getSha256() {

        return sha256;
    }

    public long getFileSize() {

        return fileSize;
    }
}