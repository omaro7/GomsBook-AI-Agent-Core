/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */

package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.nio.file.Path;
import java.time.Instant;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.ReleaseEpubApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.release.EpubReleaseService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class ReleaseEpubApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;

    private final EpubReleaseService releaseService;

    private final Gson gson;

    public ReleaseEpubApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubReleaseService releaseService) {

        this(
                currentProjectProvider,
                releaseService,
                new Gson()
        );

    }

    public ReleaseEpubApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubReleaseService releaseService,
            Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");

        if (releaseService == null) throw new IllegalArgumentException("releaseService must not be null.");

        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;

        this.releaseService = releaseService;

        this.gson = gson;

    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        ReleaseEpubApprovalPayload payload = parsePayload(approval);

        EpubProjectContext project = requireCurrentProject();

        validateProject(payload, project);

        validatePayload(payload);

        Path epubPath = Path.of(payload.getEpubPath()).toAbsolutePath().normalize();

        Instant publishedAt = parsePublishedAt(payload.getPublishedAt());

        releaseService.release(
                payload.getProjectId(),
                payload.getVersion(),
                epubPath,
                payload.getSha256(),
                payload.getFileSize(),
                publishedAt
        );

    }

    private ReleaseEpubApprovalPayload parsePayload(AgentApproval approval) {

        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Release EPUB approval content is empty.");

        try {

            ReleaseEpubApprovalPayload payload = gson.fromJson(content, ReleaseEpubApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("Release EPUB approval payload is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse Release EPUB approval payload.", exception);

        }

    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");

        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;

    }

    private void validateProject(
            ReleaseEpubApprovalPayload payload,
            EpubProjectContext project) {

        String approvedProjectId = requireText(payload.getProjectId(), "projectId");

        String currentProjectId = resolveProjectId(project);

        if (!approvedProjectId.equals(currentProjectId)) {

            throw new IllegalStateException(
                    "Current EPUB project does not match the approved project. "
                            + "Approved projectId="
                            + approvedProjectId
                            + ", current projectId="
                            + currentProjectId
            );

        }

    }

    private void validatePayload(ReleaseEpubApprovalPayload payload) {

        requireText(payload.getProjectId(), "projectId");

        requireText(payload.getVersion(), "version");

        requireText(payload.getEpubPath(), "epubPath");

        requireText(payload.getSha256(), "sha256");

        requireText(payload.getPublishedAt(), "publishedAt");

        if (payload.getFileSize() <= 0) throw new IllegalArgumentException("fileSize must be greater than zero.");

    }

    private Instant parsePublishedAt(String publishedAt) {

        try {

            return Instant.parse(requireText(publishedAt, "publishedAt"));

        } catch (RuntimeException exception) {

            throw new IllegalArgumentException("publishedAt must be a valid ISO-8601 Instant: " + publishedAt, exception);

        }

    }

    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();

    }

    private String requireText(String value, String name) {

        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");

        return value.trim();

    }

}