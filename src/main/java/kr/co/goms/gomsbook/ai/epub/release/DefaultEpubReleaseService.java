/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import kr.co.goms.gomsbook.ai.epub.model.EpubReleaseItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubReleaseStatus;

public final class DefaultEpubReleaseService implements EpubReleaseService {

    private static final DateTimeFormatter RELEASE_ID_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final EpubReleaseRepository releaseRepository;

    private final EpubReleasePolicy releasePolicy;

    public DefaultEpubReleaseService(
            EpubReleaseRepository releaseRepository,
            EpubReleasePolicy releasePolicy) {

        if (releaseRepository == null) throw new IllegalArgumentException("releaseRepository must not be null.");

        if (releasePolicy == null) throw new IllegalArgumentException("releasePolicy must not be null.");

        this.releaseRepository = releaseRepository;

        this.releasePolicy = releasePolicy;

    }

    @Override
    public EpubReleaseItem release(
            String projectId,
            String version,
            Path epubPath,
            String sha256,
            long fileSize,
            Instant publishedAt) {

        String normalizedProjectId = requireProjectId(projectId);

        EpubReleaseVersion releaseVersion = EpubReleaseVersion.parse(version);

        String normalizedVersion = releaseVersion.toString();

        Path normalizedEpubPath = validateEpubPath(epubPath);

        String normalizedSha256 = validateSha256(sha256);

        validateFileSize(fileSize);

        validatePublishedAt(publishedAt);

        releasePolicy.validate(
                normalizedProjectId,
                releaseVersion
        );

        Instant releasedAt = Instant.now();

        String releaseId = createReleaseId(
                normalizedProjectId,
                normalizedVersion,
                releasedAt
        );

        EpubReleaseItem item = new EpubReleaseItem(
                releaseId,
                normalizedProjectId,
                normalizedVersion,
                normalizedEpubPath,
                normalizedSha256,
                fileSize,
                publishedAt,
                releasedAt,
                EpubReleaseStatus.RELEASED
        );

        return releaseRepository.save(item);

    }

    private String requireProjectId(String projectId) {

        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank.");

        return projectId.trim();

    }

    private Path validateEpubPath(Path epubPath) {

        if (epubPath == null) throw new IllegalArgumentException("epubPath must not be null.");

        Path normalizedPath = epubPath.toAbsolutePath().normalize();

        if (!Files.exists(normalizedPath)) throw new IllegalArgumentException("EPUB file does not exist: " + normalizedPath);

        if (!Files.isRegularFile(normalizedPath)) throw new IllegalArgumentException("EPUB path is not a file: " + normalizedPath);

        if (!normalizedPath.getFileName().toString().toLowerCase().endsWith(".epub")) throw new IllegalArgumentException("EPUB file extension is invalid: " + normalizedPath);

        return normalizedPath;

    }

    private String validateSha256(String sha256) {

        if (sha256 == null || sha256.isBlank()) throw new IllegalArgumentException("sha256 must not be blank.");

        String normalizedSha256 = sha256.trim().toLowerCase();

        if (!normalizedSha256.matches("^[a-f0-9]{64}$")) throw new IllegalArgumentException("sha256 must be a 64-character hexadecimal value.");

        return normalizedSha256;

    }

    private void validateFileSize(long fileSize) {

        if (fileSize <= 0) throw new IllegalArgumentException("fileSize must be greater than zero.");

    }

    private void validatePublishedAt(Instant publishedAt) {

        if (publishedAt == null) throw new IllegalArgumentException("publishedAt must not be null.");

    }

    private String createReleaseId(
            String projectId,
            String version,
            Instant releasedAt) {

        String timestamp = RELEASE_ID_DATE_FORMATTER.format(releasedAt);

        return sanitize(projectId) + "-" + sanitize(version) + "-" + timestamp;

    }

    private String sanitize(String value) {

        return value.trim().replaceAll("[^A-Za-z0-9._-]", "-");

    }

}