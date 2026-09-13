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
import kr.co.goms.gomsbook.ai.agent.approval.payload.CreateEpubLoiApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.service.EpubLoiService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class CreateEpubLoiApprovalHandler implements AgentApprovalHandler {

    private static final String LOI_FILE_NAME = "loi.xhtml";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubLoiService loiService;
    private final Gson gson;

    public CreateEpubLoiApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubLoiService loiService) {
        this(currentProjectProvider, loiService, new Gson());
    }

    public CreateEpubLoiApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubLoiService loiService, Gson gson) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null.");
        this.loiService = Objects.requireNonNull(loiService, "loiService must not be null.");
        this.gson = Objects.requireNonNull(gson, "gson must not be null.");
    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();
        CreateEpubLoiApprovalPayload payload = parsePayload(approval);

        validateProject(approval, project);
        validateApproval(approval, payload);
        validateLoiNotExists(project, payload);

        loiService.create(project.getProjectRoot());
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");

        return project;
    }

    private CreateEpubLoiApprovalPayload parsePayload(AgentApproval approval) {

        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Approval content is not available.");

        try {

            CreateEpubLoiApprovalPayload payload = gson.fromJson(content, CreateEpubLoiApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB LOI creation approval payload is empty.");

            validatePayload(payload);

            return payload;

        } catch (RuntimeException exception) {

            if (exception instanceof IllegalStateException) throw exception;

            throw new IllegalStateException("Failed to parse EPUB LOI creation approval payload.", exception);
        }
    }

    private void validatePayload(CreateEpubLoiApprovalPayload payload) {

        if (isBlank(payload.getFileName())) throw new IllegalStateException("Approval payload fileName is not available.");

        validateFileName(payload.getFileName());
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

    private void validateApproval(AgentApproval approval, CreateEpubLoiApprovalPayload payload) {

        if (isBlank(approval.getFileName())) throw new IllegalStateException("Approval fileName is not available.");

        String approvalFileName = approval.getFileName().trim();
        String payloadFileName = payload.getFileName().trim();

        if (!approvalFileName.equals(payloadFileName)) {
            throw new IllegalStateException(
                    "Approval LOI file mismatch. approvalFileName="
                            + approvalFileName
                            + ", payloadFileName="
                            + payloadFileName);
        }

        validateFileName(payloadFileName);
    }

    private void validateLoiNotExists(EpubProjectContext project, CreateEpubLoiApprovalPayload payload) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(payload.getFileName()).toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalArgumentException("LOI XHTML path escapes Text directory: " + payload.getFileName());
        if (Files.exists(targetFile)) throw new IllegalStateException("EPUB LOI already exists: " + targetFile + ". Use update_epub_loi instead.");
    }

    private void validateFileName(String fileName) {

        if (isBlank(fileName)) throw new IllegalArgumentException("LOI fileName must not be blank.");

        String value = fileName.trim();

        if (value.contains("/") || value.contains("\\")) throw new IllegalArgumentException("LOI fileName must not contain path separators: " + value);
        if (!LOI_FILE_NAME.equalsIgnoreCase(value)) throw new IllegalArgumentException("LOI fileName must be " + LOI_FILE_NAME + ": " + value);

        Path path = Path.of(value).normalize();

        if (path.isAbsolute() || path.getNameCount() != 1) throw new IllegalArgumentException("Invalid LOI XHTML fileName: " + value);
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