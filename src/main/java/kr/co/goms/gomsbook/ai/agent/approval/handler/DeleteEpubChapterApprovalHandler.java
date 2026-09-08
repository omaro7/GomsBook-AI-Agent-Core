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
import kr.co.goms.gomsbook.ai.agent.approval.payload.DeleteEpubChapterApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.chapter.EpubChapterService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class DeleteEpubChapterApprovalHandler implements AgentApprovalHandler {

    public static final String TOOL_NAME = "delete_epub_chapter";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubChapterService chapterService;
    private final Gson gson;

    public DeleteEpubChapterApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubChapterService chapterService) {
        this(currentProjectProvider, chapterService, new Gson());
    }

    public DeleteEpubChapterApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubChapterService chapterService, Gson gson) {

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

        validateApproval(approval);

        DeleteEpubChapterApprovalPayload payload = parsePayload(approval);

        validatePayload(payload);
        validateApprovalPayload(approval, payload);

        EpubProjectContext project = requireCurrentProject();

        validateProject(payload, project);

        Path packageDocument = project.getPackageDocument();
        Path navigationFile = project.getNavigationFile();
        Path textDirectory = project.getTextDirectory();

        chapterService.delete(payload.getFileName(), packageDocument, navigationFile, textDirectory);

    }

    private DeleteEpubChapterApprovalPayload parsePayload(AgentApproval approval) {

        DeleteEpubChapterApprovalPayload payload =
                gson.fromJson(
                        approval.getContent(),
                        DeleteEpubChapterApprovalPayload.class);

        if (payload == null) throw new IllegalStateException("Failed to parse delete EPUB chapter approval payload.");

        return payload;
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project =
                currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

        return project;
    }

    private void validateApproval(AgentApproval approval) {

        if (isBlank(approval.getFileName())) throw new IllegalStateException("Approval fileName is not available.");
        if (isBlank(approval.getContent())) throw new IllegalStateException("Approval content is not available.");
    }

    private void validatePayload(DeleteEpubChapterApprovalPayload payload) {

        if (isBlank(payload.getProjectId())) throw new IllegalStateException("Approved projectId is not available.");
        if (payload.getPartNumber() <= 0) throw new IllegalStateException("Approved partNumber must be greater than 0.");
        if (payload.getChapterNumber() <= 0) throw new IllegalStateException("Approved chapterNumber must be greater than 0.");
        if (isBlank(payload.getFileName())) throw new IllegalStateException("Approved fileName is not available.");
        if (!payload.getFileName().toLowerCase().endsWith(".xhtml")) throw new IllegalStateException("Approved fileName must have the .xhtml extension.");
    }

    private void validateApprovalPayload(AgentApproval approval, DeleteEpubChapterApprovalPayload payload) {

        if (!approval.getFileName().trim().equals(payload.getFileName().trim())) {

            throw new IllegalStateException(
                    "Approval fileName does not match the approved payload. "
                            + "Approval fileName="
                            + approval.getFileName()
                            + ", payload fileName="
                            + payload.getFileName());
        }
    }

    private void validateProject(DeleteEpubChapterApprovalPayload payload, EpubProjectContext project) {

        String currentProjectId =
                resolveProjectId(project);

        if (!payload.getProjectId().equals(currentProjectId)) {

            throw new IllegalStateException(
                    "Current EPUB project does not match the approved project. "
                            + "Approved projectId="
                            + payload.getProjectId()
                            + ", current projectId="
                            + currentProjectId);
        }
    }

    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null
                && !project.getProjectName().isBlank()) {

            return project.getProjectName().trim();
        }

        return project
                .getProjectRoot()
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}