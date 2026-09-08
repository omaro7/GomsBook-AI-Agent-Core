/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.metadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubMetadataApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class UpdateEpubMetadataTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_metadata";

    private static final String OPERATION_ADD = "ADD";
    private static final String OPERATION_UPDATE = "UPDATE";
    private static final String OPERATION_REMOVE = "REMOVE";

    private static final String APPROVAL_TITLE = "EPUB Metadata 수정";

    private static final String DESCRIPTION =
            "Prepares an ADD, UPDATE, or REMOVE operation for metadata in the current EPUB package document(content.opf). "
                    + "Use ADD to append a new metadata entry, UPDATE to change an existing metadata entry, and REMOVE to remove an existing metadata entry if present. "
                    + "targetValue identifies the existing metadata value for UPDATE or REMOVE and is important when multiple meta elements use the same property, such as schema:accessMode or schema:accessibilityFeature. "
                    + "The operation requires explicit user approval before content.opf is changed.";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public UpdateEpubMetadataTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new Gson());
    }

    public UpdateEpubMetadataTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (approvalService == null) throw new IllegalArgumentException("approvalService must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.approvalService = approvalService;
        this.gson = gson;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put(
                "operation",
                enumProperty(
                        List.of(OPERATION_ADD, OPERATION_UPDATE, OPERATION_REMOVE),
                        "Metadata 작업입니다. ADD는 신규 추가, UPDATE는 기존 항목 수정, REMOVE는 기존 항목 삭제입니다."));

        properties.put(
                "name",
                stringProperty(
                        "Metadata 요소 이름입니다. 예: dc:title, dc:creator, dc:language, dc:publisher, dc:identifier, meta."));

        properties.put(
                "value",
                stringProperty(
                        "새 metadata 값입니다. ADD와 UPDATE에서는 필수입니다."));

        properties.put(
                "targetValue",
                stringProperty(
                        "UPDATE 또는 REMOVE 시 현재 content.opf에서 찾을 기존 metadata 값입니다. 동일 property가 반복되는 metadata를 정확히 식별할 때 사용합니다."));

        properties.put(
                "id",
                stringProperty(
                        "선택적인 metadata id 속성입니다."));

        properties.put(
                "property",
                stringProperty(
                        "선택적인 EPUB 3 meta property 속성입니다. 예: dcterms:modified, schema:accessMode, schema:accessibilityFeature."));

        properties.put(
                "refines",
                stringProperty(
                        "선택적인 EPUB 3 refines 속성입니다. 예: #creator01."));

        properties.put(
                "scheme",
                stringProperty(
                        "선택적인 EPUB 3 scheme 속성입니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("operation", "name"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            String operation = normalizeOperation(requireString(request, "operation"));
            String name = requireString(request, "name");
            String value = getString(request, "value");
            String targetValue = getString(request, "targetValue");
            String id = getString(request, "id");
            String property = getString(request, "property");
            String refines = normalizeRefines(getString(request, "refines"));
            String scheme = getString(request, "scheme");

            validateOperation(operation);
            validateMetadata(
                    operation,
                    name,
                    value,
                    targetValue,
                    property,
                    refines,
                    scheme);

            Path packageDocument = project.getPackageDocument().toAbsolutePath().normalize();

            validatePackageDocument(packageDocument);

            UpdateEpubMetadataApprovalPayload payload = createPayload(
                    operation,
                    name,
                    value,
                    targetValue,
                    id,
                    property,
                    refines,
                    scheme);

            String approvalContent = gson.toJson(payload);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String fileName = packageDocument.getFileName().toString();

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    createApprovalMessage(payload),
                    fileName,
                    approvalContent);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("previewTitle", "Metadata");
            data.put("preview", createPreview(payload));

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB metadata update approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB metadata update: " + safeMessage(exception);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.FAILED)
                    .message(errorMessage)
                    .errorMessage(errorMessage)
                    .cause(exception)
                    .build();
        }
    }

    private UpdateEpubMetadataApprovalPayload createPayload(
            String operation,
            String name,
            String value,
            String targetValue,
            String id,
            String property,
            String refines,
            String scheme) {

        UpdateEpubMetadataApprovalPayload payload = new UpdateEpubMetadataApprovalPayload();

        payload.setOperation(operation);
        payload.setName(name);
        payload.setValue(value);
        payload.setTargetValue(targetValue);
        payload.setId(id);
        payload.setProperty(property);
        payload.setRefines(refines);
        payload.setScheme(scheme);

        return payload;
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");

        return project;
    }

    private void validatePackageDocument(Path packageDocument) {

        if (!Files.exists(packageDocument)) throw new IllegalStateException("EPUB package document does not exist: " + packageDocument);
        if (!Files.isRegularFile(packageDocument)) throw new IllegalStateException("EPUB package document is not a file: " + packageDocument);
    }

    private void validateOperation(String operation) {

        if (OPERATION_ADD.equals(operation)) return;
        if (OPERATION_UPDATE.equals(operation)) return;
        if (OPERATION_REMOVE.equals(operation)) return;

        throw new IllegalArgumentException(
                "Unsupported metadata operation: "
                        + operation
                        + ". Supported operations: ADD, UPDATE, REMOVE.");
    }

    private void validateMetadata(
            String operation,
            String name,
            String value,
            String targetValue,
            String property,
            String refines,
            String scheme) {

        if (!name.startsWith("dc:") && !"meta".equals(name)) {
            throw new IllegalArgumentException(
                    "Unsupported metadata element name: "
                            + name
                            + ". Use a dc:* element or meta.");
        }

        if (OPERATION_ADD.equals(operation) && value == null) {
            throw new IllegalArgumentException("value must not be blank when operation is ADD.");
        }

        if (OPERATION_UPDATE.equals(operation) && value == null) {
            throw new IllegalArgumentException("value must not be blank when operation is UPDATE.");
        }

        if (name.startsWith("dc:") && property != null) {
            throw new IllegalArgumentException("Dublin Core metadata must not define property.");
        }

        if (name.startsWith("dc:") && refines != null) {
            throw new IllegalArgumentException("Dublin Core metadata must not define refines.");
        }

        if (name.startsWith("dc:") && scheme != null) {
            throw new IllegalArgumentException("Dublin Core metadata must not define scheme.");
        }

        if ("meta".equals(name) && property == null) {
            throw new IllegalArgumentException("meta metadata requires property.");
        }

        if (scheme != null && refines == null) {
            throw new IllegalArgumentException("scheme should be used with refines.");
        }

        if (OPERATION_UPDATE.equals(operation) && isRepeatableAccessibilityProperty(property) && targetValue == null) {
            throw new IllegalArgumentException(
                    "targetValue must not be blank when updating repeatable accessibility metadata property: "
                            + property);
        }

        if (OPERATION_REMOVE.equals(operation) && isRepeatableAccessibilityProperty(property) && targetValue == null) {
            throw new IllegalArgumentException(
                    "targetValue must not be blank when removing repeatable accessibility metadata property: "
                            + property);
        }
    }

    private boolean isRepeatableAccessibilityProperty(String property) {

        if (property == null) return false;

        if ("schema:accessMode".equals(property)) return true;
        if ("schema:accessibilityFeature".equals(property)) return true;
        if ("schema:accessibilityHazard".equals(property)) return true;

        return false;
    }

    private String createApprovalMessage(UpdateEpubMetadataApprovalPayload payload) {

        if (OPERATION_ADD.equals(payload.getOperation())) {
            return "content.opf에 metadata 항목 "
                    + describeMetadata(payload)
                    + "을 추가하시겠습니까?";
        }

        if (OPERATION_UPDATE.equals(payload.getOperation())) {
            return "content.opf의 metadata 항목 "
                    + describeMetadata(payload)
                    + "을 수정하시겠습니까?";
        }

        return "content.opf에서 metadata 항목 "
                + describeMetadata(payload)
                + "을 삭제하시겠습니까?";
    }

    private String describeMetadata(UpdateEpubMetadataApprovalPayload payload) {

        if ("meta".equals(payload.getName()) && payload.getProperty() != null) {
            return payload.getProperty();
        }

        return payload.getName();
    }

    private String createPreview(UpdateEpubMetadataApprovalPayload payload) {

        StringBuilder builder = new StringBuilder();

        builder.append("operation = ").append(payload.getOperation()).append("\n");
        builder.append("name = ").append(payload.getName()).append("\n");

        if (payload.getTargetValue() != null) builder.append("targetValue = ").append(payload.getTargetValue()).append("\n");
        if (payload.getValue() != null) builder.append("value = ").append(payload.getValue()).append("\n");
        if (payload.getId() != null) builder.append("id = ").append(payload.getId()).append("\n");
        if (payload.getProperty() != null) builder.append("property = ").append(payload.getProperty()).append("\n");
        if (payload.getRefines() != null) builder.append("refines = ").append(payload.getRefines()).append("\n");
        if (payload.getScheme() != null) builder.append("scheme = ").append(payload.getScheme()).append("\n");

        return builder.toString().trim();
    }

    private Map<String, Object> stringProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }

    private Map<String, Object> enumProperty(List<String> values, String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("enum", values);
        property.put("description", description);

        return property;
    }

    private String requireString(ToolRequest request, String name) {

        String value = getString(request, name);

        if (value == null) throw new IllegalArgumentException(name + " must not be blank.");

        return value;
    }

    private String getString(ToolRequest request, String name) {
        return trimToNull(request.getArgument(name, String.class));
    }

    private String normalizeOperation(String value) {

        if (value == null) return null;

        return value.toUpperCase(Locale.ROOT);
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

    private String resolveRunId(ToolRequest request, ToolContext context) {

        if (context != null && context.getRequestId() != null && !context.getRequestId().isBlank()) {
            return context.getRequestId().trim();
        }

        if (request != null && request.getRequestId() != null && !request.getRequestId().isBlank()) {
            return request.getRequestId().trim();
        }

        throw new IllegalStateException("runId is not available from ToolContext or ToolRequest.");
    }

    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) {
            return project.getProjectName().trim();
        }

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";

        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }
}