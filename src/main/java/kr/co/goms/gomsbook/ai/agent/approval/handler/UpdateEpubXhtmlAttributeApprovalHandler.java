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

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubXhtmlAttributeApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.updater.xhtml.EpubXhtmlUpdater;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

/**
 * {
  "fileName": "nav.xhtml",
  "elementName": "nav",
  "matchAttributeName": "epub:type",
  "matchAttributeValue": "toc",
  "attributeName": "role",
  "attributeValue": "doc-toc"
}

<nav epub:type="toc" id="toc"> -> <nav epub:type="toc" id="toc" role="doc-toc">
 */
public final class UpdateEpubXhtmlAttributeApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubXhtmlUpdater epubXhtmlUpdater;
    private final Gson gson;

    public UpdateEpubXhtmlAttributeApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubXhtmlUpdater epubXhtmlUpdater) {

        this(currentProjectProvider, epubXhtmlUpdater, new Gson());
    }

    public UpdateEpubXhtmlAttributeApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubXhtmlUpdater epubXhtmlUpdater, Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (epubXhtmlUpdater == null) throw new IllegalArgumentException("epubXhtmlUpdater must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.epubXhtmlUpdater = epubXhtmlUpdater;
        this.gson = gson;
    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();

        validateProject(approval, project);
        validateApproval(approval);

        UpdateEpubXhtmlAttributeApprovalPayload payload = parsePayload(approval);

        validatePayload(approval, payload);

        Path targetFile = resolveTargetFile(project, payload.getFileName());

        validateTargetFile(targetFile);

        System.out.println("[GomsBook EPUB] Updating XHTML Attribute = " + targetFile);

        epubXhtmlUpdater.setAttribute(payload.getFileName(), payload.getElementName(), payload.getMatchAttributeName(), payload.getMatchAttributeValue(), payload.getAttributeName(), payload.getAttributeValue(), project.getTextDirectory());

        System.out.println("[GomsBook EPUB] XHTML Attribute Updated  = " + targetFile);
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

        if (isBlank(approval.getFileName())) throw new IllegalStateException("Approval fileName is not available.");
        if (isBlank(approval.getContent())) throw new IllegalStateException("Approval content is not available.");

        validateFileName(approval.getFileName());
    }

    private UpdateEpubXhtmlAttributeApprovalPayload parsePayload(AgentApproval approval) {

        try {

            UpdateEpubXhtmlAttributeApprovalPayload payload = gson.fromJson(approval.getContent(), UpdateEpubXhtmlAttributeApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB XHTML attribute approval content is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse EPUB XHTML attribute approval content.", exception);
        }
    }

    private void validatePayload(AgentApproval approval, UpdateEpubXhtmlAttributeApprovalPayload payload) {

        if (isBlank(payload.getFileName())) throw new IllegalArgumentException("fileName must not be blank.");
        if (isBlank(payload.getElementName())) throw new IllegalArgumentException("elementName must not be blank.");
        if (isBlank(payload.getMatchAttributeName())) throw new IllegalArgumentException("matchAttributeName must not be blank.");
        if (isBlank(payload.getMatchAttributeValue())) throw new IllegalArgumentException("matchAttributeValue must not be blank.");
        if (isBlank(payload.getAttributeName())) throw new IllegalArgumentException("attributeName must not be blank.");
        if (payload.getAttributeValue() == null) throw new IllegalArgumentException("attributeValue must not be null.");

        validateFileName(payload.getFileName());

        String approvalFileName = approval.getFileName().trim();
        String payloadFileName = payload.getFileName().trim();

        if (!approvalFileName.equals(payloadFileName)) throw new IllegalStateException("Approval fileName does not match payload fileName: approval=" + approvalFileName + ", payload=" + payloadFileName);
    }

    private Path resolveTargetFile(EpubProjectContext project, String fileName) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(fileName.trim()).toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalStateException("EPUB XHTML must be inside the EPUB Text directory.");

        return targetFile;
    }

    private void validateTargetFile(Path targetFile) {

        if (!Files.exists(targetFile)) throw new IllegalStateException("EPUB XHTML does not exist: " + targetFile);
        if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB XHTML path is not a file: " + targetFile);
    }

    private void validateFileName(String fileName) {

        if (isBlank(fileName)) throw new IllegalArgumentException("fileName must not be blank.");

        String normalized = fileName.trim();

        if (normalized.contains("/") || normalized.contains("\\")) throw new IllegalArgumentException("fileName must contain only the XHTML file name.");
        if (!normalized.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("EPUB XHTML file must use the .xhtml extension.");
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