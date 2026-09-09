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
import java.util.Locale;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubManifestApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.updater.pkg.EpubPackageUpdater;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;


public final class UpdateEpubManifestApprovalHandler implements AgentApprovalHandler {

    private static final String OPERATION_ADD = "ADD";
    private static final String OPERATION_REMOVE = "REMOVE";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubPackageUpdater packageUpdater;
    private final Gson gson;


    public UpdateEpubManifestApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubPackageUpdater packageUpdater) {

        this(currentProjectProvider, packageUpdater, new Gson());
    }


    public UpdateEpubManifestApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubPackageUpdater packageUpdater, Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (packageUpdater == null) throw new IllegalArgumentException("packageUpdater must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.packageUpdater = packageUpdater;
        this.gson = gson;
    }


    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();

        validateProject(approval, project);
        validateApproval(approval);

        UpdateEpubManifestApprovalPayload payload = parsePayload(approval);

        validatePayload(payload);

        Path packagePath = requirePackageDocument(project);

        System.out.println("[GomsBook EPUB] Manifest Operation = " + payload.getOperation());
        System.out.println("[GomsBook EPUB] Manifest ID        = " + payload.getId());
        System.out.println("[GomsBook EPUB] Package Document  = " + packagePath);

        executeOperation(packagePath, payload);

        System.out.println("[GomsBook EPUB] Manifest Updated   = " + packagePath);
    }


    private void executeOperation(Path packagePath, UpdateEpubManifestApprovalPayload payload) {

        switch (payload.getOperation()) {

            case OPERATION_ADD -> addManifestItem(packagePath, payload);

            case OPERATION_REMOVE -> packageUpdater.removeManifestItem(packagePath, payload.getId());

            default -> throw new IllegalArgumentException("Unsupported EPUB manifest operation: " + payload.getOperation());
        }
    }


    private void addManifestItem(Path packagePath, UpdateEpubManifestApprovalPayload payload) {

        EpubManifestItem item = EpubManifestItem.builder(payload.getId(), payload.getHref()).mediaType(payload.getMediaType()).properties(payload.getProperties()).build();

        packageUpdater.addManifestItem(packagePath, item);
    }


    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");

        return project;
    }


    private Path requirePackageDocument(EpubProjectContext project) {

        Path packagePath = project.getPackageDocument().toAbsolutePath().normalize();

        if (!Files.exists(packagePath)) throw new IllegalStateException("EPUB package document does not exist: " + packagePath);
        if (!Files.isRegularFile(packagePath)) throw new IllegalStateException("EPUB package document is not a file: " + packagePath);

        return packagePath;
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

        String fileName = approval.getFileName().trim();

        if (!"content.opf".equalsIgnoreCase(fileName)) throw new IllegalArgumentException("Invalid EPUB package fileName: " + fileName);
    }


    private UpdateEpubManifestApprovalPayload parsePayload(AgentApproval approval) {

        try {

            UpdateEpubManifestApprovalPayload payload = gson.fromJson(approval.getContent(), UpdateEpubManifestApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB manifest approval content is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse EPUB manifest approval content.", exception);
        }
    }


    private void validatePayload(UpdateEpubManifestApprovalPayload payload) {

        String operation = normalizeOperation(payload.getOperation());

        if (!operation.equals(payload.getOperation())) throw new IllegalArgumentException("operation must use uppercase ADD or REMOVE.");
        if (isBlank(payload.getId())) throw new IllegalArgumentException("id must not be blank.");

        if (OPERATION_ADD.equals(operation)) validateAddPayload(payload);
    }


    private void validateAddPayload(UpdateEpubManifestApprovalPayload payload) {

        if (isBlank(payload.getHref())) throw new IllegalArgumentException("href must not be blank for ADD operation.");
        if (isBlank(payload.getMediaType())) throw new IllegalArgumentException("mediaType must not be blank for ADD operation.");
    }


    private String normalizeOperation(String operation) {

        if (isBlank(operation)) throw new IllegalArgumentException("operation must not be blank.");

        String normalized = operation.trim().toUpperCase(Locale.ROOT);

        if (!OPERATION_ADD.equals(normalized) && !OPERATION_REMOVE.equals(normalized)) throw new IllegalArgumentException("Unsupported EPUB manifest operation: " + operation);

        return normalized;
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