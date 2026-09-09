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
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubSpineApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.updater.pkg.EpubPackageUpdater;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;


/**
 * EPUB spine 수정 승인을 실행합니다.
 *
 * 지원 작업:
 * ADD    - itemref 추가
 * DELETE - itemref 삭제
 * MOVE   - itemref 이동
 *
 * 현재는 itemref의 idref 속성만 처리합니다.
 */
public final class UpdateEpubSpineApprovalHandler implements AgentApprovalHandler {

    private static final String OPERATION_ADD = "ADD";
    private static final String OPERATION_DELETE = "DELETE";
    private static final String OPERATION_MOVE = "MOVE";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubPackageUpdater packageUpdater;
    private final Gson gson;


    public UpdateEpubSpineApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubPackageUpdater packageUpdater) {

        this(currentProjectProvider, packageUpdater, new Gson());
    }


    public UpdateEpubSpineApprovalHandler(CurrentProjectProvider currentProjectProvider, EpubPackageUpdater packageUpdater, Gson gson) {

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
        validateApproval(approval, project);

        UpdateEpubSpineApprovalPayload payload = parsePayload(approval);

        validatePayload(payload);

        Path packageDocument = project.getPackageDocument();

        System.out.println("[GomsBook EPUB] Updating Spine      = " + packageDocument);
        System.out.println("[GomsBook EPUB] Spine Operation    = " + payload.getOperation());
        System.out.println("[GomsBook EPUB] Spine Idref        = " + payload.getIdref());

        executeOperation(packageDocument, payload);

        System.out.println("[GomsBook EPUB] Spine Updated       = " + packageDocument);
    }


    private void executeOperation(Path packageDocument, UpdateEpubSpineApprovalPayload payload) {

        String operation = normalizeOperation(payload.getOperation());
        String idref = payload.getIdref().trim();

        if (OPERATION_ADD.equals(operation)) {

            packageUpdater.addSpineItemref(packageDocument, idref, payload.getTargetIndex());

            return;
        }

        if (OPERATION_DELETE.equals(operation)) {

            boolean removed = packageUpdater.removeSpineItemrefIfExists(packageDocument, idref);

            System.out.println("[GomsBook EPUB] Spine Removed       = " + removed);

            return;
        }

        if (OPERATION_MOVE.equals(operation)) {

            packageUpdater.moveSpineItemref(packageDocument, idref, payload.getTargetIndex());

            return;
        }

        throw new IllegalArgumentException("Unsupported EPUB spine operation: " + operation);
    }


    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");
        if (!Files.isRegularFile(project.getPackageDocument())) throw new IllegalStateException("Current EPUB package document does not exist: " + project.getPackageDocument());

        return project;
    }


    private void validateProject(AgentApproval approval, EpubProjectContext project) {

        String approvalProjectId = trimToNull(approval.getProjectId());

        if (approvalProjectId == null) return;

        String currentProjectId = resolveProjectId(project);

        if (!approvalProjectId.equals(currentProjectId)) throw new IllegalStateException("Approval project mismatch. approvalProjectId=" + approvalProjectId + ", currentProjectId=" + currentProjectId);
    }


    private void validateApproval(AgentApproval approval, EpubProjectContext project) {

        if (isBlank(approval.getFileName())) throw new IllegalStateException("Approval fileName is not available.");
        if (isBlank(approval.getContent())) throw new IllegalStateException("Approval content is not available.");

        String approvalFileName = approval.getFileName().trim();
        String packageFileName = project.getPackageDocument().getFileName().toString();

        if (!approvalFileName.equals(packageFileName)) throw new IllegalStateException("Approval package file mismatch. approvalFileName=" + approvalFileName + ", currentPackageFile=" + packageFileName);
    }


    private UpdateEpubSpineApprovalPayload parsePayload(AgentApproval approval) {

        try {

            UpdateEpubSpineApprovalPayload payload = gson.fromJson(approval.getContent(), UpdateEpubSpineApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB spine approval content is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse EPUB spine approval content.", exception);
        }
    }


    private void validatePayload(UpdateEpubSpineApprovalPayload payload) {

        if (payload == null) throw new IllegalArgumentException("payload must not be null.");
        if (isBlank(payload.getOperation())) throw new IllegalArgumentException("operation must not be blank.");
        if (isBlank(payload.getIdref())) throw new IllegalArgumentException("idref must not be blank.");

        String operation = normalizeOperation(payload.getOperation());

        if (OPERATION_ADD.equals(operation) || OPERATION_MOVE.equals(operation)) {

            if (payload.getTargetIndex() == null) throw new IllegalArgumentException("targetIndex is required for " + operation + ".");
            if (payload.getTargetIndex() < 0) throw new IllegalArgumentException("targetIndex must be greater than or equal to 0.");

            return;
        }

        if (OPERATION_DELETE.equals(operation)) return;

        throw new IllegalArgumentException("operation must be one of ADD, DELETE, MOVE.");
    }


    private String normalizeOperation(String operation) {

        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation must not be blank.");

        return operation.trim().toUpperCase();
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