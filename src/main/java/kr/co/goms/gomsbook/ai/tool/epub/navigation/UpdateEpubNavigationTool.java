/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.navigation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubNavigationApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.updater.navigation.EpubNavigationInsertPosition;
import kr.co.goms.gomsbook.ai.epub.updater.navigation.EpubNavigationUpdateItem;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class UpdateEpubNavigationTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_navigation";

    private static final String APPROVAL_TITLE = "EPUB 목차 수정";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public UpdateEpubNavigationTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new Gson());
    }

    public UpdateEpubNavigationTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {
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
        return "현재 EPUB 프로젝트의 nav.xhtml 목차 항목을 추가, 수정, 삭제하거나 불필요한 빈 li, ol, ul 요소를 정리합니다. 실제 수정 전에는 사용자 승인이 필요합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("operation", enumProperty(List.of("ADD", "UPDATE", "REMOVE", "CLEANUP"), "Navigation 작업 유형입니다."));
        properties.put("href", stringProperty("목차 항목의 href입니다. ADD, UPDATE, REMOVE 작업에서 사용합니다."));
        properties.put("label", stringProperty("목차에 표시할 제목입니다. ADD 또는 UPDATE 작업에서 사용합니다."));
        properties.put("id", stringProperty("목차 li 요소의 선택적 id입니다."));
        properties.put("position", enumProperty(List.of("FIRST", "LAST", "BEFORE", "AFTER"), "목차 항목의 삽입 또는 이동 위치입니다."));
        properties.put("referenceHref", stringProperty("BEFORE 또는 AFTER 작업에서 기준이 되는 href입니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("operation"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {
        try {
            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();
            Map<String, Object> arguments = request.getArguments();

            if (arguments == null) arguments = Collections.emptyMap();

            Operation operation = Operation.from(requireString(arguments, "operation"));

            validateArguments(operation, arguments);

            String fileName = project.getNavigationFile().getFileName().toString();
            String href = resolvePayloadHref(operation, arguments);
            List<EpubNavigationUpdateItem> items = createUpdateItems(operation, arguments);

            UpdateEpubNavigationApprovalPayload payload = new UpdateEpubNavigationApprovalPayload(operation.name(), fileName, href, items);

            String content = gson.toJson(payload);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String approvalMessage = createApprovalMessage(operation, arguments, fileName);

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    approvalMessage,
                    fileName,
                    content);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("operation", operation.name());

            if (href != null) data.put("href", href);

            String label = optionalString(arguments, "label");

            if (label != null) data.put("label", label);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB navigation update approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB navigation update: " + safeMessage(exception);

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

    private List<EpubNavigationUpdateItem> createUpdateItems(Operation operation, Map<String, Object> arguments) {
        if (operation == Operation.REMOVE || operation == Operation.CLEANUP) return List.of();

        String href = requireString(arguments, "href");
        String label = requireString(arguments, "label");
        String id = optionalString(arguments, "id");
        String referenceHref = optionalString(arguments, "referenceHref");
        EpubNavigationInsertPosition position = resolvePosition(arguments);

        EpubNavigationItem.Builder builder = EpubNavigationItem.builder().href(href).label(label);

        if (id != null) builder.id(id);

        EpubNavigationItem item = builder.build();
        EpubNavigationUpdateItem updateItem = new EpubNavigationUpdateItem(item, position, referenceHref);

        return List.of(updateItem);
    }

    private String resolvePayloadHref(Operation operation, Map<String, Object> arguments) {
        if (operation == Operation.CLEANUP) return null;

        return requireString(arguments, "href");
    }

    private void validateArguments(Operation operation, Map<String, Object> arguments) {
        if (operation == Operation.CLEANUP) return;

        requireString(arguments, "href");

        if (operation == Operation.REMOVE) return;

        requireString(arguments, "label");

        EpubNavigationInsertPosition position = resolvePosition(arguments);

        if (position == EpubNavigationInsertPosition.BEFORE || position == EpubNavigationInsertPosition.AFTER) requireString(arguments, "referenceHref");
    }

    private EpubNavigationInsertPosition resolvePosition(Map<String, Object> arguments) {
        String value = optionalString(arguments, "position");

        if (value == null) return EpubNavigationInsertPosition.LAST;

        try {
            return EpubNavigationInsertPosition.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported navigation position: " + value);
        }
    }

    private String createApprovalMessage(Operation operation, Map<String, Object> arguments, String fileName) {
        if (operation == Operation.CLEANUP) return fileName + "에서 불필요한 빈 li, ol, ul 요소를 정리하시겠습니까?";

        String href = requireString(arguments, "href");

        if (operation == Operation.REMOVE) return fileName + "에서 '" + href + "' 목차 항목을 삭제하시겠습니까?";
        if (operation == Operation.ADD) return fileName + "에 '" + href + "' 목차 항목을 추가하시겠습니까?";

        return fileName + "의 '" + href + "' 목차 항목을 수정하시겠습니까?";
    }

    private EpubProjectContext requireCurrentProject() {
        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getNavigationFile() == null) throw new IllegalStateException("Current EPUB navigation file is not available.");

        return project;
    }

    private String resolveRunId(ToolRequest request, ToolContext context) {
        if (context != null && context.getRequestId() != null && !context.getRequestId().isBlank()) return context.getRequestId().trim();
        if (request != null && request.getRequestId() != null && !request.getRequestId().isBlank()) return request.getRequestId().trim();

        throw new IllegalStateException("runId is not available from ToolContext or ToolRequest.");
    }

    private String resolveProjectId(EpubProjectContext project) {
        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private String requireString(Map<String, Object> arguments, String name) {
        String value = optionalString(arguments, name);

        if (value == null) throw new IllegalArgumentException(name + " must not be blank.");

        return value;
    }

    private String optionalString(Map<String, Object> arguments, String name) {
        if (arguments == null || !arguments.containsKey(name)) return null;

        Object value = arguments.get(name);

        if (value == null) return null;

        String text = String.valueOf(value).trim();

        return text.isEmpty() ? null : text;
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

    private String safeMessage(Throwable throwable) {
        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }

    private enum Operation {

        ADD,
        UPDATE,
        REMOVE,
        CLEANUP;

        private static Operation from(String value) {
            try {
                return Operation.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported navigation operation: " + value);
            }
        }
    }
}