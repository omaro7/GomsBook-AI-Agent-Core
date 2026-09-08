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
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubMetadataApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.model.EpubMetadataItem;
import kr.co.goms.gomsbook.ai.epub.pkg.updater.EpubPackageUpdater;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class UpdateEpubMetadataApprovalHandler implements AgentApprovalHandler {

    private static final String OPERATION_ADD = "ADD";
    private static final String OPERATION_UPDATE = "UPDATE";
    private static final String OPERATION_REMOVE = "REMOVE";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubPackageUpdater packageUpdater;
    private final Gson gson;

    public UpdateEpubMetadataApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubPackageUpdater packageUpdater,
            Gson gson) {

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
        UpdateEpubMetadataApprovalPayload payload = parsePayload(approval);
        Path packageDocument = project.getPackageDocument().toAbsolutePath().normalize();

        validatePackageDocument(packageDocument);
        validateFileName(approval, packageDocument);
        validatePayload(payload);

        executeOperation(packageDocument, payload);
    }

    private void executeOperation(
            Path packageDocument,
            UpdateEpubMetadataApprovalPayload payload) {

        String operation = normalizeOperation(payload.getOperation());

        if (OPERATION_ADD.equals(operation)) {

            packageUpdater.addMetadata(
                    packageDocument,
                    toMetadataEntry(payload));

            return;
        }

        if (OPERATION_UPDATE.equals(operation)) {

            packageUpdater.updateMetadata(
                    packageDocument,
                    trimToNull(payload.getName()),
                    trimToNull(payload.getTargetValue()),
                    trimToNull(payload.getId()),
                    trimToNull(payload.getProperty()),
                    normalizeRefines(payload.getRefines()),
                    trimToNull(payload.getScheme()),
                    toMetadataEntry(payload));

            return;
        }

        if (OPERATION_REMOVE.equals(operation)) {

            packageUpdater.removeMetadataIfExists(
                    packageDocument,
                    trimToNull(payload.getName()),
                    trimToNull(payload.getTargetValue()),
                    trimToNull(payload.getId()),
                    trimToNull(payload.getProperty()),
                    normalizeRefines(payload.getRefines()),
                    trimToNull(payload.getScheme()));

            return;
        }

        throw new IllegalArgumentException("Unsupported EPUB metadata operation: " + operation);
    }

    private EpubMetadataItem toMetadataEntry(UpdateEpubMetadataApprovalPayload payload) {

        String name = trimToNull(payload.getName());
        String value = trimToNull(payload.getValue());
        String id = trimToNull(payload.getId());
        String property = trimToNull(payload.getProperty());
        String refines = normalizeRefines(payload.getRefines());
        String scheme = trimToNull(payload.getScheme());

        if ("meta".equals(name)) {

            EpubMetadataItem.Builder builder = EpubMetadataItem.meta(property, value);

            if (id != null) builder.id(id);
            if (refines != null) builder.refines(refines);
            if (scheme != null) builder.scheme(scheme);

            return builder.build();
        }

        EpubMetadataItem.Builder builder = EpubMetadataItem.dc(name, value);

        if (id != null) builder.id(id);

        return builder.build();
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");

        return project;
    }

    private UpdateEpubMetadataApprovalPayload parsePayload(AgentApproval approval) {

        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Approval content is empty.");

        try {

            UpdateEpubMetadataApprovalPayload payload = gson.fromJson(content, UpdateEpubMetadataApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("Approval payload is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse EPUB metadata approval payload.", exception);
        }
    }

    private void validatePayload(UpdateEpubMetadataApprovalPayload payload) {

        if (payload == null) throw new IllegalArgumentException("payload must not be null.");

        String operation = normalizeOperation(payload.getOperation());
        String name = trimToNull(payload.getName());
        String value = trimToNull(payload.getValue());
        String targetValue = trimToNull(payload.getTargetValue());
        String property = trimToNull(payload.getProperty());
        String refines = normalizeRefines(payload.getRefines());
        String scheme = trimToNull(payload.getScheme());

        if (name == null) throw new IllegalArgumentException("name must not be blank.");
        if (!OPERATION_ADD.equals(operation) && !OPERATION_UPDATE.equals(operation) && !OPERATION_REMOVE.equals(operation)) throw new IllegalArgumentException("Unsupported metadata operation: " + operation + ".");
        if (!name.startsWith("dc:") && !"meta".equals(name)) throw new IllegalArgumentException("Unsupported metadata element name: " + name + ".");
        if (OPERATION_ADD.equals(operation) && value == null) throw new IllegalArgumentException("value must not be blank when operation is ADD.");
        if (OPERATION_UPDATE.equals(operation) && value == null) throw new IllegalArgumentException("value must not be blank when operation is UPDATE.");
        if (name.startsWith("dc:") && property != null) throw new IllegalArgumentException("Dublin Core metadata must not define property.");
        if (name.startsWith("dc:") && refines != null) throw new IllegalArgumentException("Dublin Core metadata must not define refines.");
        if (name.startsWith("dc:") && scheme != null) throw new IllegalArgumentException("Dublin Core metadata must not define scheme.");
        if ("meta".equals(name) && property == null) throw new IllegalArgumentException("meta metadata requires property.");
        if (scheme != null && refines == null) throw new IllegalArgumentException("scheme should be used with refines.");

        if (OPERATION_UPDATE.equals(operation) && isRepeatableAccessibilityProperty(property) && targetValue == null) throw new IllegalArgumentException("targetValue must not be blank when updating repeatable accessibility metadata property: " + property);
        if (OPERATION_REMOVE.equals(operation) && isRepeatableAccessibilityProperty(property) && targetValue == null) throw new IllegalArgumentException("targetValue must not be blank when removing repeatable accessibility metadata property: " + property);
    }

    private boolean isRepeatableAccessibilityProperty(String property) {

        if (property == null) return false;
        if ("schema:accessMode".equals(property)) return true;
        if ("schema:accessModeSufficient".equals(property)) return true;
        if ("schema:accessibilityFeature".equals(property)) return true;
        if ("schema:accessibilityHazard".equals(property)) return true;

        return false;
    }

    private void validatePackageDocument(Path packageDocument) {

        if (!Files.exists(packageDocument)) throw new IllegalStateException("EPUB package document does not exist: " + packageDocument);
        if (!Files.isRegularFile(packageDocument)) throw new IllegalStateException("EPUB package document is not a file: " + packageDocument);
    }

    private void validateFileName(
            AgentApproval approval,
            Path packageDocument) {

        String approvalFileName = trimToNull(approval.getFileName());
        String packageFileName = packageDocument.getFileName().toString();

        if (approvalFileName == null) throw new IllegalStateException("Approval fileName is empty.");
        if (!packageFileName.equals(approvalFileName)) throw new IllegalStateException("Approval package file mismatch. approvalFileName=" + approvalFileName + ", currentPackageFile=" + packageFileName);
    }

    private String normalizeOperation(String value) {

        String operation = trimToNull(value);

        if (operation == null) throw new IllegalArgumentException("operation must not be blank.");

        return operation.toUpperCase(Locale.ROOT);
    }

    private String normalizeRefines(String value) {

        String refines = trimToNull(value);

        if (refines == null) return null;

        return refines.startsWith("#") ? refines : "#" + refines;
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}