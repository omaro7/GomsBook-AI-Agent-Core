/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CreateEpubChapterApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.chapter.EpubChapterService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class CreateEpubChapterApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubChapterService chapterService;
    private final Gson gson;

    public CreateEpubChapterApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubChapterService chapterService) {
        this(currentProjectProvider, chapterService, new Gson());
    }

    public CreateEpubChapterApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubChapterService chapterService, Gson gson) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null.");
        this.chapterService = Objects.requireNonNull(chapterService, "chapterService must not be null.");
        this.gson = Objects.requireNonNull(gson, "gson must not be null.");
    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();
        CreateEpubChapterApprovalPayload payload = parsePayload(approval);

        validateProject(approval, project);
        validateApproval(approval, payload);
        validateChapterNotExists(project, payload);

        chapterService.create(
                payload.getPartNumber(),
                payload.getChapterNumber(),
                payload.getFileName(),
                payload.getTitle(),
                payload.getContent(),
                project.getPackageDocument(),
                project.getNavigationFile(),
                project.getTextDirectory());
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");
        if (project.getNavigationFile() == null) throw new IllegalStateException("Current EPUB navigation file is not available.");

        return project;
    }

    private CreateEpubChapterApprovalPayload parsePayload(AgentApproval approval) {

        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Approval content is not available.");

        try {

            CreateEpubChapterApprovalPayload payload = gson.fromJson(content, CreateEpubChapterApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB chapter creation approval payload is empty.");

            validatePayload(payload);

            return payload;

        } catch (RuntimeException exception) {

            if (exception instanceof IllegalStateException) throw exception;

            throw new IllegalStateException("Failed to parse EPUB chapter creation approval payload.", exception);
        }
    }

    private void validatePayload(CreateEpubChapterApprovalPayload payload) {

        if (payload.getPartNumber() <= 0) throw new IllegalStateException("Approval payload partNumber must be greater than 0.");
        if (payload.getChapterNumber() <= 0) throw new IllegalStateException("Approval payload chapterNumber must be greater than 0.");
        if (isBlank(payload.getFileName())) throw new IllegalStateException("Approval payload fileName is not available.");
        if (isBlank(payload.getTitle())) throw new IllegalStateException("Approval payload title is not available.");
        if (isBlank(payload.getContent())) throw new IllegalStateException("Approval payload content is not available.");
    }

    private void validateProject(AgentApproval approval, EpubProjectContext project) {

        String approvalProjectId = trimToNull(approval.getProjectId());

        if (approvalProjectId == null) return;

        String currentProjectId = resolveProjectId(project);

        if (!approvalProjectId.equals(currentProjectId)) {
            throw new IllegalStateException(
                    "Approval project mismatch. approvalProjectId="
                            + approvalProjectId
                            + ", currentProjectId="
                            + currentProjectId);
        }
    }

    private void validateApproval(AgentApproval approval, CreateEpubChapterApprovalPayload payload) {

        if (isBlank(approval.getFileName())) throw new IllegalStateException("Approval fileName is not available.");

        String approvalFileName = approval.getFileName().trim();
        String payloadFileName = payload.getFileName().trim();

        if (!approvalFileName.equals(payloadFileName)) {
            throw new IllegalStateException(
                    "Approval chapter file mismatch. approvalFileName="
                            + approvalFileName
                            + ", payloadFileName="
                            + payloadFileName);
        }

        validateFileName(payloadFileName);
    }

    private void validateChapterNotExists(EpubProjectContext project, CreateEpubChapterApprovalPayload payload) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(payload.getFileName()).toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalArgumentException("Chapter XHTML path escapes Text directory: " + payload.getFileName());
        if (Files.exists(targetFile)) throw new IllegalStateException("EPUB chapter already exists: " + targetFile + ". Use update_epub_chapter instead.");
    }

    private void validateFileName(String fileName) {

        if (isBlank(fileName)) throw new IllegalArgumentException("Chapter fileName must not be blank.");

        String value = fileName.trim();

        if (value.contains("/") || value.contains("\\")) throw new IllegalArgumentException("Chapter fileName must not contain path separators: " + value);
        if (!value.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("Chapter file must be XHTML: " + value);

        Path path = Path.of(value).normalize();

        if (path.isAbsolute() || path.getNameCount() != 1) throw new IllegalArgumentException("Invalid chapter XHTML fileName: " + value);
    }

    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

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