/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.release;

import java.util.Optional;

import kr.co.goms.gomsbook.ai.epub.model.EpubReleaseItem;

public final class DefaultEpubReleasePolicy implements EpubReleasePolicy {

    private final EpubReleaseRepository releaseRepository;

    public DefaultEpubReleasePolicy(EpubReleaseRepository releaseRepository) {

        if (releaseRepository == null) throw new IllegalArgumentException("releaseRepository must not be null.");

        this.releaseRepository = releaseRepository;

    }

    @Override
    public void validate(String projectId, EpubReleaseVersion requestedVersion) {

        String normalizedProjectId = requireProjectId(projectId);

        if (requestedVersion == null) throw new IllegalArgumentException("requestedVersion must not be null.");

        validateDuplicateVersion(
                normalizedProjectId,
                requestedVersion
        );

        validateVersionOrder(
                normalizedProjectId,
                requestedVersion
        );

    }

    private void validateDuplicateVersion(
            String projectId,
            EpubReleaseVersion requestedVersion) {

        Optional<EpubReleaseItem> existingRelease = releaseRepository.findByProjectIdAndVersion(
                projectId,
                requestedVersion.toString()
        );

        if (existingRelease.isEmpty()) return;

        EpubReleaseItem existingItem = existingRelease.get();

        throw new IllegalStateException(
                "EPUB release version already exists. "
                        + "projectId="
                        + projectId
                        + ", version="
                        + requestedVersion
                        + ", releaseId="
                        + existingItem.getReleaseId()
                        + ", status="
                        + existingItem.getStatus()
        );

    }

    private void validateVersionOrder(
            String projectId,
            EpubReleaseVersion requestedVersion) {

        Optional<EpubReleaseItem> latestRelease = releaseRepository.findLatestByProjectId(projectId);

        if (latestRelease.isEmpty()) return;

        EpubReleaseItem latestItem = latestRelease.get();

        EpubReleaseVersion latestVersion = EpubReleaseVersion.parse(latestItem.getVersion());

        if (requestedVersion.isGreaterThan(latestVersion)) return;

        throw new IllegalStateException(
                "EPUB release version must be greater than the latest release version. "
                        + "projectId="
                        + projectId
                        + ", latestVersion="
                        + latestVersion
                        + ", requestedVersion="
                        + requestedVersion
                        + ", latestReleaseId="
                        + latestItem.getReleaseId()
        );

    }

    private String requireProjectId(String projectId) {

        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank.");

        return projectId.trim();

    }

}