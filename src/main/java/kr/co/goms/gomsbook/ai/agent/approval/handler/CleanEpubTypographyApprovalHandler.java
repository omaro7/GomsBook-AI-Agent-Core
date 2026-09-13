/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.nio.file.Files;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CleanEpubTypographyApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.updater.xhtml.EpubTypographyUpdater;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class CleanEpubTypographyApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;

    private final EpubTypographyUpdater typographyUpdater;

    private final Gson gson;

    public CleanEpubTypographyApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubTypographyUpdater typographyUpdater) {
        this(currentProjectProvider, typographyUpdater, new Gson());
    }

    public CleanEpubTypographyApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubTypographyUpdater typographyUpdater, Gson gson) {
        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (typographyUpdater == null) throw new IllegalArgumentException("typographyUpdater must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.typographyUpdater = typographyUpdater;
        this.gson = gson;
    }

    @Override
    public void execute(AgentApproval approval) {
        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();

        validateProject(approval, project);
        validateApproval(approval);

        CleanEpubTypographyApprovalPayload payload = parsePayload(approval);

        validatePayload(approval, payload);

        typographyUpdater.update(project.getProjectRoot(), payload.operation(), payload.fileName());
    }

    private EpubProjectContext requireCurrentProject() {
        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");
        if (!Files.isDirectory(project.getTextDirectory())) throw new IllegalStateException("Current EPUB Text directory does not exist: " + project.getTextDirectory());

        return project;
    }

    private void validateProject(AgentApproval approval, EpubProjectContext project) {
        String approvalProjectId = trimToNull(approval.getProjectId());

        if (approvalProjectId == null) return;

        String currentProjectId = resolveProjectId(project);

        if (!approvalProjectId.equals(currentProjectId)) throw new IllegalStateException("Approval project mismatch. approvalProjectId=" + approvalProjectId + ", currentProjectId=" + currentProjectId);
    }

    private void validateApproval(AgentApproval approval) {
        if (isBlank(approval.getContent())) throw new IllegalStateException("Approval content is not available.");

        String fileName = trimToNull(approval.getFileName());

        if (fileName != null) validateFileName(fileName);
    }

    private CleanEpubTypographyApprovalPayload parsePayload(AgentApproval approval) {
        try {
            CleanEpubTypographyApprovalPayload payload = gson.fromJson(approval.getContent(), CleanEpubTypographyApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB typography approval content is empty.");

            return payload;

        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse EPUB typography approval content.", exception);
        }
    }

    private void validatePayload(AgentApproval approval, CleanEpubTypographyApprovalPayload payload) {
        if (payload.operation() == null) throw new IllegalArgumentException("operation must not be null.");

        String approvalFileName = trimToNull(approval.getFileName());
        String payloadFileName = trimToNull(payload.fileName());

        if (payloadFileName != null) validateFileName(payloadFileName);

        if (approvalFileName == null && payloadFileName == null) return;
        if (approvalFileName == null || payloadFileName == null) throw new IllegalStateException("Approval fileName does not match payload fileName.");
        if (!approvalFileName.equals(payloadFileName)) throw new IllegalStateException("Approval fileName does not match payload fileName: approval=" + approvalFileName + ", payload=" + payloadFileName);
    }

    private void validateFileName(String fileName) {
        if (isBlank(fileName)) throw new IllegalArgumentException("fileName must not be blank.");

        String normalized = fileName.trim();

        if (normalized.contains("/") || normalized.contains("\\")) throw new IllegalArgumentException("fileName must contain only the XHTML file name.");
        if (!normalized.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("EPUB typography target file must use the .xhtml extension.");
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