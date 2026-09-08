/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.spine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubSpineApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;


/**
 * 현재 EPUB 프로젝트 content.opf의 spine itemref 수정 승인을 준비합니다.
 *
 * 지원 작업:
 * ADD    - itemref 추가
 * DELETE - itemref 삭제
 * MOVE   - itemref 이동
 *
 * 현재는 itemref의 idref 속성만 처리합니다.
 */
public final class UpdateEpubSpineTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_spine";

    private static final String DESCRIPTION = "Prepares a modification to the current EPUB project's spine in content.opf. "
            + "Use this tool only for adding, deleting, or moving <itemref> elements in the current project's spine. "
            + "The supported operations are ADD, DELETE, and MOVE. "
            + "Only the itemref idref attribute is currently supported. "
            + "ADD and MOVE require targetIndex, which is a zero-based spine position. "
            + "DELETE does not require targetIndex. "
            + "This tool DOES NOT modify a published .epub file and DOES NOT replace the entire spine. "
            + "If the current spine order is needed, call read_epub_spine first. "
            + "The update requires explicit user approval before content.opf is changed.";

    private static final String APPROVAL_TITLE = "EPUB Spine 수정";

    private static final String OPERATION_ADD = "ADD";
    private static final String OPERATION_DELETE = "DELETE";
    private static final String OPERATION_MOVE = "MOVE";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;


    public UpdateEpubSpineTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {

        this(currentProjectProvider, approvalService, new Gson());
    }


    public UpdateEpubSpineTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

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

        properties.put("operation", operationProperty());
        properties.put("idref", stringProperty("수정할 spine itemref의 idref입니다. 예: chapter02_01"));
        properties.put("targetIndex", targetIndexProperty());

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("operation", "idref"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }


    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();
            Path packageDocument = requirePackageDocument(project);

            String operation = requireOperation(request);
            String idref = requireString(request, "idref");
            Integer targetIndex = getInteger(request, "targetIndex");

            validateArguments(operation, idref, targetIndex);

            UpdateEpubSpineApprovalPayload payload = new UpdateEpubSpineApprovalPayload(operation, idref, targetIndex);

            String approvalContent = gson.toJson(payload);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String fileName = packageDocument.getFileName().toString();

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    createApprovalMessage(operation, idref, targetIndex),
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
            data.put("previewTitle", "Spine 변경");
            data.put("preview", createPreview(operation, idref, targetIndex));

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB spine update approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB spine update: " + safeMessage(exception);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.FAILED)
                    .message(errorMessage)
                    .errorCode("EPUB_SPINE_UPDATE_PREPARE_FAILED")
                    .errorMessage(errorMessage)
                    .cause(exception)
                    .build();
        }
    }


    private Map<String, Object> operationProperty() {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("enum", List.of(OPERATION_ADD, OPERATION_DELETE, OPERATION_MOVE));
        property.put("description", "Spine 수정 작업입니다. ADD=추가, DELETE=삭제, MOVE=이동.");

        return property;
    }


    private Map<String, Object> stringProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("minLength", 1);
        property.put("description", description);

        return property;
    }


    private Map<String, Object> targetIndexProperty() {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "integer");
        property.put("minimum", 0);
        property.put("description", "ADD 또는 MOVE 대상 위치의 0-based index입니다. DELETE에서는 사용하지 않습니다.");

        return property;
    }


    private void validateArguments(String operation, String idref, Integer targetIndex) {

        if (idref == null || idref.isBlank()) throw new IllegalArgumentException("idref must not be blank.");

        if (OPERATION_ADD.equals(operation) || OPERATION_MOVE.equals(operation)) {

            if (targetIndex == null) throw new IllegalArgumentException("targetIndex is required for " + operation + ".");
            if (targetIndex.intValue() < 0) throw new IllegalArgumentException("targetIndex must be greater than or equal to 0.");

            return;
        }

        if (OPERATION_DELETE.equals(operation)) return;

        throw new IllegalArgumentException("Unsupported spine operation: " + operation);
    }


    private String requireOperation(ToolRequest request) {

        String operation = requireString(request, "operation").toUpperCase();

        if (OPERATION_ADD.equals(operation)) return OPERATION_ADD;
        if (OPERATION_DELETE.equals(operation)) return OPERATION_DELETE;
        if (OPERATION_MOVE.equals(operation)) return OPERATION_MOVE;

        throw new IllegalArgumentException("operation must be one of ADD, DELETE, MOVE.");
    }


    private String requireString(ToolRequest request, String name) {

        String value = request.getArgument(name, String.class);

        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");

        return value.trim();
    }


    private Integer getInteger(ToolRequest request, String name) {

        return request.getArgument(name, Integer.class);
    }


    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;
    }


    private Path requirePackageDocument(EpubProjectContext project) {

        Path packageDocument = project.getPackageDocument();

        if (packageDocument == null) throw new IllegalStateException("Current EPUB package document is not available.");
        if (!Files.isRegularFile(packageDocument)) throw new IllegalStateException("Current EPUB package document does not exist: " + packageDocument);

        return packageDocument;
    }


    private String createApprovalMessage(String operation, String idref, Integer targetIndex) {

        if (OPERATION_ADD.equals(operation)) return "content.opf spine에 " + idref + "을 index " + targetIndex + " 위치에 추가하시겠습니까?";
        if (OPERATION_DELETE.equals(operation)) return "content.opf spine에서 " + idref + "을 삭제하시겠습니까?";
        if (OPERATION_MOVE.equals(operation)) return "content.opf spine의 " + idref + "을 index " + targetIndex + " 위치로 이동하시겠습니까?";

        return "content.opf spine을 수정하시겠습니까?";
    }


    private String createPreview(String operation, String idref, Integer targetIndex) {

        if (OPERATION_ADD.equals(operation)) return "ADD    <itemref idref=\"" + escapeXml(idref) + "\"/> -> index " + targetIndex;
        if (OPERATION_DELETE.equals(operation)) return "DELETE <itemref idref=\"" + escapeXml(idref) + "\"/>";
        if (OPERATION_MOVE.equals(operation)) return "MOVE   <itemref idref=\"" + escapeXml(idref) + "\"/> -> index " + targetIndex;

        return "";
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


    private String escapeXml(String value) {

        if (value == null) return "";

        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }


    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}