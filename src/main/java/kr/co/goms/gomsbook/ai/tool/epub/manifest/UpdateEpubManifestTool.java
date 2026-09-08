/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.manifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubManifestApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;


public final class UpdateEpubManifestTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_manifest";

    private static final String OPERATION_ADD = "ADD";
    private static final String OPERATION_REMOVE = "REMOVE";

    private static final String PACKAGE_FILE_NAME = "content.opf";
    private static final String APPROVAL_TITLE = "EPUB Manifest 수정";

    private static final String DESCRIPTION = "Prepares an update to the current EPUB content.opf manifest. "
    		+ "Use ADD to add a new manifest item and REMOVE to remove an existing manifest item. "
    		+ "For ADD, id, href and mediaType are required and properties is optional. For REMOVE, only id is required. "
    		+ "The actual content.opf modification requires explicit user approval.";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;


    public UpdateEpubManifestTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {

        this(currentProjectProvider, approvalService, new Gson());
    }


    public UpdateEpubManifestTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

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

        properties.put("operation", enumStringProperty("Manifest 수정 작업입니다. ADD 또는 REMOVE를 사용합니다.", List.of(OPERATION_ADD, OPERATION_REMOVE)));
        properties.put("id", stringProperty("Manifest item id입니다. ADD와 REMOVE 모두 필수입니다. 예: chapter02_05"));
        properties.put("href", stringProperty("content.opf 기준 EPUB 리소스 상대 경로입니다. ADD에서 사용합니다. 예: Text/chapter02_05.xhtml"));
        properties.put("mediaType", stringProperty("EPUB 리소스 MIME type입니다. ADD에서 사용합니다. 예: application/xhtml+xml"));
        properties.put("properties", stringArrayProperty("Manifest item properties 목록입니다. ADD에서 선택적으로 사용합니다. 예: nav, cover-image, scripted"));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("operation", "id"));
        schema.put("additionalProperties", false);

        return Map.copyOf(schema);
    }


    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            String operation = requireOperation(request);
            String id = requireString(request, "id");
            String href = getString(request, "href");
            String mediaType = getString(request, "mediaType");
            List<String> properties = getStringList(request, "properties");

            if (OPERATION_ADD.equals(operation)) validateAddArguments(href, mediaType);

            UpdateEpubManifestApprovalPayload payload = new UpdateEpubManifestApprovalPayload(operation, id, href, mediaType, properties);

            String approvalContent = gson.toJson(payload);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String approvalMessage = createApprovalMessage(payload);

            AgentApproval approval = approvalService.create(runId, projectId, AgentApprovalAction.PREFIX + TOOL_NAME, APPROVAL_TITLE, approvalMessage, PACKAGE_FILE_NAME, approvalContent);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("operation", operation);
            data.put("id", id);

            if (href != null) data.put("href", href);
            if (mediaType != null) data.put("mediaType", mediaType);
            if (!properties.isEmpty()) data.put("properties", properties);

            return ToolResult.builder().toolName(TOOL_NAME).requestId(request.getRequestId()).toolCallId(request.getToolCallId()).status(ToolStatus.SUCCESS).message("EPUB manifest update approval is required.").data(data).build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB manifest update: " + safeMessage(exception);

            return ToolResult.builder().toolName(TOOL_NAME).requestId(request != null ? request.getRequestId() : null).toolCallId(request != null ? request.getToolCallId() : null).status(ToolStatus.FAILED).message(errorMessage).errorMessage(errorMessage).cause(exception).build();
        }
    }


    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");

        return project;
    }


    private String requireOperation(ToolRequest request) {

        String operation = requireString(request, "operation").toUpperCase(Locale.ROOT);

        if (!OPERATION_ADD.equals(operation) && !OPERATION_REMOVE.equals(operation)) throw new IllegalArgumentException("Unsupported EPUB manifest operation: " + operation);

        return operation;
    }


    private void validateAddArguments(String href, String mediaType) {

        if (href == null || href.isBlank()) throw new IllegalArgumentException("href is required for ADD operation.");
        if (mediaType == null || mediaType.isBlank()) throw new IllegalArgumentException("mediaType is required for ADD operation.");
    }


    private String createApprovalMessage(UpdateEpubManifestApprovalPayload payload) {

        if (OPERATION_ADD.equals(payload.getOperation())) return "다음 Manifest 항목을 content.opf에 추가하시겠습니까? id=" + payload.getId() + ", href=" + payload.getHref() + ", mediaType=" + payload.getMediaType();

        return "다음 Manifest 항목을 content.opf에서 삭제하시겠습니까? id=" + payload.getId();
    }


    private String requireString(ToolRequest request, String name) {

        String value = getString(request, name);

        if (value == null) throw new IllegalArgumentException(name + " must not be blank.");

        return value;
    }


    private String getString(ToolRequest request, String name) {

        if (request == null) return null;

        Object arguments = request.getArguments();

        if (!(arguments instanceof Map<?, ?> map)) return null;

        Object value = map.get(name);

        if (value == null) return null;

        String text = String.valueOf(value).trim();

        return text.isEmpty() ? null : text;
    }


    private List<String> getStringList(ToolRequest request, String name) {

        if (request == null) return List.of();

        Object arguments = request.getArguments();

        if (!(arguments instanceof Map<?, ?> map)) return List.of();

        Object value = map.get(name);

        if (value == null) return List.of();

        if (value instanceof List<?> list) {

            List<String> result = new ArrayList<>();

            for (Object item : list) {

                if (item == null) continue;

                String text = String.valueOf(item).trim();

                if (!text.isEmpty()) result.add(text);
            }

            return List.copyOf(result);
        }

        String text = String.valueOf(value).trim();

        if (text.isEmpty()) return List.of();

        List<String> result = new ArrayList<>();

        for (String token : text.split("\\s+")) {

            String normalized = token.trim();

            if (!normalized.isEmpty()) result.add(normalized);
        }

        return List.copyOf(result);
    }


    private String resolveRunId(ToolRequest request, ToolContext context) {

        if (context != null && context.getRequestId() != null && !context.getRequestId().isBlank()) return context.getRequestId().trim();
        if (request != null && request.getRequestId() != null && !request.getRequestId().isBlank()) return request.getRequestId().trim();

        throw new IllegalStateException("Agent runId is not available.");
    }


    private String resolveProjectId(EpubProjectContext project) {

        String projectId = project.getProjectName();

        if (projectId == null || projectId.isBlank()) throw new IllegalStateException("Current EPUB projectId is not available.");

        return projectId.trim();
    }


    private Map<String, Object> stringProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return Map.copyOf(property);
    }


    private Map<String, Object> enumStringProperty(String description, List<String> values) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);
        property.put("enum", values);

        return Map.copyOf(property);
    }


    private Map<String, Object> stringArrayProperty(String description) {

        Map<String, Object> itemSchema = new LinkedHashMap<>();

        itemSchema.put("type", "string");

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "array");
        property.put("description", description);
        property.put("items", Map.copyOf(itemSchema));

        return Map.copyOf(property);
    }


    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();

        return message.trim();
    }
}