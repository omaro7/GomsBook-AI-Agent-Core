/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.util.Objects;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubNavigationApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdater;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class UpdateEpubNavigationApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubNavigationUpdater navigationUpdater;
    private final Gson gson;

    public UpdateEpubNavigationApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubNavigationUpdater navigationUpdater,
            Gson gson) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null.");
        this.navigationUpdater = Objects.requireNonNull(navigationUpdater, "navigationUpdater must not be null.");
        this.gson = Objects.requireNonNull(gson, "gson must not be null.");
    }

    @Override
    public void execute(AgentApproval approval) {
        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();
        UpdateEpubNavigationApprovalPayload payload = parsePayload(approval);

        validateFileName(approval, payload, project);

        navigationUpdater.update(project.getNavigationFile(), payload.toUpdateItems());
    }

    private EpubProjectContext requireCurrentProject() {
        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getNavigationFile() == null) throw new IllegalStateException("Current EPUB navigation file is not available.");

        return project;
    }

    private UpdateEpubNavigationApprovalPayload parsePayload(AgentApproval approval) {
        if (approval.getContent() == null || approval.getContent().isBlank()) throw new IllegalStateException("Approval content is not available.");

        try {
            UpdateEpubNavigationApprovalPayload payload = gson.fromJson(approval.getContent(), UpdateEpubNavigationApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB navigation update approval payload is empty.");

            return payload;

        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse EPUB navigation update approval payload.", exception);
        }
    }

    private void validateFileName(
            AgentApproval approval,
            UpdateEpubNavigationApprovalPayload payload,
            EpubProjectContext project) {

        String currentFileName = project.getNavigationFile().getFileName().toString();

        if (approval.getFileName() == null || !currentFileName.equals(approval.getFileName().trim())) {
            throw new IllegalStateException(
                    "Approval navigation file mismatch. approvalFileName="
                            + approval.getFileName()
                            + ", currentNavigationFile="
                            + currentFileName);
        }

        if (payload.getFileName() == null || !currentFileName.equals(payload.getFileName().trim())) {
            throw new IllegalStateException(
                    "Navigation payload file mismatch. payloadFileName="
                            + payload.getFileName()
                            + ", currentNavigationFile="
                            + currentFileName);
        }
    }
}