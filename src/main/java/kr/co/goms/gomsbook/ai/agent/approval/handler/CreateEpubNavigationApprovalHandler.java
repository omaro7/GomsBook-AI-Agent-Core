/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
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
import com.google.gson.JsonParseException;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.EpubNavigationApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.navigation.EpubNavigationService;
import kr.co.goms.gomsbook.ai.epub.generation.navigation.EpubNavigationXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class CreateEpubNavigationApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubNavigationService navigationService;
    private final Gson gson;

    public CreateEpubNavigationApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubNavigationService navigationService) {
        this(currentProjectProvider, navigationService, new Gson());
    }

    public CreateEpubNavigationApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubNavigationService navigationService, Gson gson) {
        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService must not be null");
        this.gson = Objects.requireNonNull(gson, "gson must not be null");
    }
    
    @Override
    public void execute(AgentApproval approval) {
        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();

        validateProject(approval, project);
        validateApproval(approval, project);
        validateNavigationNotExists(project);

        EpubNavigationApprovalPayload payload = parsePayload(approval);
        EpubNavigation navigation = payload.toNavigation();

        validatePayload(payload, project);
        navigation.validate();

        navigationService.generate(navigation, project.getNavigationFile());
    }

    private EpubProjectContext requireCurrentProject() {
        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getNavigationFile() == null) throw new IllegalStateException("Current EPUB navigation file is not available.");

        return project;
    }

    private void validateProject(AgentApproval approval, EpubProjectContext project) {
        String approvalProjectId = trimToNull(approval.getProjectId());

        if (approvalProjectId == null) return;

        String currentProjectId = resolveProjectId(project);

        if (!approvalProjectId.equals(currentProjectId)) {
            throw new IllegalStateException(
                    "Approval project mismatch. approvalProjectId=" + approvalProjectId
                            + ", currentProjectId=" + currentProjectId);
        }
    }

    private void validateApproval(AgentApproval approval, EpubProjectContext project) {
        if (isBlank(approval.getFileName())) throw new IllegalStateException("Approval fileName is not available.");
        if (isBlank(approval.getContent())) throw new IllegalStateException("Approval content is not available.");

        String approvalFileName = approval.getFileName().trim();
        String navigationFileName = project.getNavigationFile().getFileName().toString();

        if (!approvalFileName.equals(navigationFileName)) {
            throw new IllegalStateException(
                    "Approval navigation file mismatch. approvalFileName=" + approvalFileName
                            + ", navigationFileName=" + navigationFileName);
        }
    }

    private void validateNavigationNotExists(EpubProjectContext project) {
        if (project.hasNavigationFile()) throw new IllegalStateException("EPUB navigation already exists: " + project.getNavigationFile());
    }

    private EpubNavigationApprovalPayload parsePayload(AgentApproval approval) {
        try {
            EpubNavigationApprovalPayload payload = gson.fromJson(approval.getContent(), EpubNavigationApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB navigation approval payload is empty.");

            return payload;

        } catch (JsonParseException exception) {
            throw new IllegalStateException("Failed to parse EPUB navigation approval payload.", exception);
        }
    }

    private void validatePayload(EpubNavigationApprovalPayload payload, EpubProjectContext project) {
        String payloadFileName = trimToNull(payload.getFileName());

        if (payloadFileName == null) throw new IllegalStateException("Navigation payload fileName is not available.");

        String navigationFileName = project.getNavigationFile().getFileName().toString();

        if (!payloadFileName.equals(navigationFileName)) {
            throw new IllegalStateException(
                    "Navigation payload file mismatch. payloadFileName=" + payloadFileName
                            + ", navigationFileName=" + navigationFileName);
        }
    }

    private String resolveProjectId(EpubProjectContext project) {
        if (!isBlank(project.getProjectName())) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private String trimToNull(String value) {
        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}