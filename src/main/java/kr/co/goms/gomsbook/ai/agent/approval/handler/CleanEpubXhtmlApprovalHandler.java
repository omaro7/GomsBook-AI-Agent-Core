/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CleanEpubXhtmlApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.model.EpubXhtmlCleanupResult;
import kr.co.goms.gomsbook.ai.epub.service.EpubXhtmlCleanupService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public class CleanEpubXhtmlApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;

    private final EpubXhtmlCleanupService cleanupService;

    private final Gson gson;

    public CleanEpubXhtmlApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubXhtmlCleanupService cleanupService, Gson gson) {
        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (cleanupService == null) throw new IllegalArgumentException("cleanupService must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.cleanupService = cleanupService;
        this.gson = gson;
    }

    @Override
    public void execute(AgentApproval approval) {
        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();
        CleanEpubXhtmlApprovalPayload payload = parsePayload(approval);

        validatePayload(payload);

        EpubXhtmlCleanupResult result = executeCleanup(project, payload);

        validateResult(result);
    }

    private EpubProjectContext requireCurrentProject() {
        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;
    }

    private CleanEpubXhtmlApprovalPayload parsePayload(AgentApproval approval) {
        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Approval content is empty.");

        try {
            CleanEpubXhtmlApprovalPayload payload = gson.fromJson(content, CleanEpubXhtmlApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("Approval payload is empty.");

            return payload;

        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse EPUB XHTML approval payload.", exception);
        }
    }

    private void validatePayload(CleanEpubXhtmlApprovalPayload payload) {
        if (!payload.isTextScope() && !payload.isFileScope()) throw new IllegalArgumentException("Unsupported XHTML cleanup scope: " + payload.scope());
        if (payload.isFileScope() && (payload.fileName() == null || payload.fileName().isBlank())) throw new IllegalArgumentException("fileName must not be blank when scope is FILE.");
    }

    private EpubXhtmlCleanupResult executeCleanup(EpubProjectContext project, CleanEpubXhtmlApprovalPayload payload) {
        if (payload.isTextScope()) return cleanupService.cleanupTextDirectory(project.getProjectRoot());

        return cleanupService.cleanupFile(project.getProjectRoot(), payload.fileName().trim());
    }

    private void validateResult(EpubXhtmlCleanupResult result) {
        if (result == null) throw new IllegalStateException("EPUB XHTML cleanup result is empty.");
        if (!result.isSuccess()) throw new IllegalStateException("EPUB XHTML cleanup failed. failedCount=" + result.failedCount());
    }
}