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

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubChapterApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.chapter.EpubChapterService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class UpdateEpubChapterApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubChapterService chapterService;
    private final Gson gson;

    public UpdateEpubChapterApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubChapterService chapterService) {
        this(currentProjectProvider, chapterService, new Gson());
    }

    public UpdateEpubChapterApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubChapterService chapterService, Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (chapterService == null) throw new IllegalArgumentException("chapterService must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.chapterService = chapterService;
        this.gson = gson;
    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();
        UpdateEpubChapterApprovalPayload payload = parsePayload(approval);

        validatePayload(payload);
        validateApprovalFileName(approval, payload);

        chapterService.update(
                payload.getFileName(),
                payload.getXhtml(),
                project.getTextDirectory());
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

        return project;
    }

    private UpdateEpubChapterApprovalPayload parsePayload(AgentApproval approval) {

        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Approval content is empty.");

        try {

            UpdateEpubChapterApprovalPayload payload = gson.fromJson(content, UpdateEpubChapterApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("Approval payload is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse EPUB chapter update approval payload.", exception);
        }
    }

    private void validatePayload(UpdateEpubChapterApprovalPayload payload) {

        if (payload.getFileName() == null || payload.getFileName().isBlank()) throw new IllegalStateException("Approval payload fileName must not be blank.");
        if (!payload.getFileName().trim().toLowerCase().endsWith(".xhtml")) throw new IllegalStateException("Approval payload fileName must have the .xhtml extension.");
        if (payload.getXhtml() == null || payload.getXhtml().isBlank()) throw new IllegalStateException("Approval payload xhtml must not be blank.");
    }

    private void validateApprovalFileName(AgentApproval approval, UpdateEpubChapterApprovalPayload payload) {

        String approvalFileName = approval.getFileName();

        if (approvalFileName == null || approvalFileName.isBlank()) return;

        if (!approvalFileName.trim().equals(payload.getFileName().trim())) {
            throw new IllegalStateException(
                    "Approval fileName does not match payload fileName: approval="
                            + approvalFileName
                            + ", payload="
                            + payload.getFileName());
        }
    }
}