/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.nio.file.Path;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CreateEpubLotApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.model.EpubLotResult;
import kr.co.goms.gomsbook.ai.epub.service.EpubLotService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class CreateEpubLotApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubLotService lotService;
    private final Gson gson;

    public CreateEpubLotApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubLotService lotService) {
        this(currentProjectProvider, lotService, new Gson());
    }

    public CreateEpubLotApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubLotService lotService, Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (lotService == null) throw new IllegalArgumentException("lotService must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.lotService = lotService;
        this.gson = gson;
    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        CreateEpubLotApprovalPayload payload = parsePayload(approval);
        EpubProjectContext project = requireCurrentProject();

        validateProject(approval, project);
        validatePayload(payload);

        Path projectRoot = project.getProjectRoot();

        if (projectRoot == null) throw new IllegalStateException("Current EPUB project root is not available.");

        lotService.create(projectRoot);
    }

    private CreateEpubLotApprovalPayload parsePayload(AgentApproval approval) {

        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Create EPUB LOT approval content is empty.");

        try {

            CreateEpubLotApprovalPayload payload = gson.fromJson(content, CreateEpubLotApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("Create EPUB LOT approval payload is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse create EPUB LOT approval payload.", exception);
        }
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");

        return project;
    }

    private void validateProject(AgentApproval approval, EpubProjectContext project) {

        String approvalProjectId = normalize(approval.getProjectId());
        String currentProjectId = normalize(project.getProjectId());

        if (approvalProjectId == null) throw new IllegalStateException("Approval projectId is not available.");
        if (currentProjectId == null) throw new IllegalStateException("Current EPUB projectId is not available.");
        if (!approvalProjectId.equals(currentProjectId)) throw new IllegalStateException("Approval project does not match current EPUB project.");
    }

    private void validatePayload(CreateEpubLotApprovalPayload payload) {

        String fileName = normalize(payload.fileName());

        if (fileName == null) throw new IllegalStateException("LOT fileName is not available.");
        if (!"lot.xhtml".equalsIgnoreCase(fileName)) throw new IllegalStateException("Invalid LOT fileName: " + fileName);
    }

    private String normalize(String value) {

        if (value == null || value.isBlank()) return null;

        return value.trim();
    }

}