/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.lot;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CreateEpubLotApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class CreateEpubLotTool implements AgentTool {

    public static final String TOOL_NAME = "create_epub_lot";

    private static final String APPROVAL_TITLE = "표 목록 생성";
    private static final String DEFAULT_FILE_NAME = "lot.xhtml";
    private static final String LOT_MANIFEST_ID = "lot";
    private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public CreateEpubLotTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new Gson());
    }

    public CreateEpubLotTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

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
        return "Prepares creation of the EPUB List of Tables (LOT) for the current EPUB project. "
                + "After user approval, the LOT service scans spine XHTML documents for tables, "
                + "generates Text/lot.xhtml, applies the navigation stylesheet, and registers the LOT document "
                + "in the EPUB manifest and spine. "
                + "This tool only prepares the approval request and does not modify EPUB files before approval.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("fileName", stringProperty("LOT XHTML file name. The supported value is lot.xhtml."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of());
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();
            String fileName = getString(request, "fileName", DEFAULT_FILE_NAME);

            validateFileName(fileName);

            String preview = createPreview(project, fileName);
            String content = createApprovalContent(fileName);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    "수정하시겠습니까? (승인하시면 실제 파일에 반영됩니다.)",
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
            data.put("preview", preview);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB LOT creation approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB LOT creation: " + safeMessage(exception);

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

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;
    }

    private void validateFileName(String fileName) {

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (!DEFAULT_FILE_NAME.equalsIgnoreCase(fileName.trim())) throw new IllegalArgumentException("LOT fileName must be lot.xhtml.");
    }

    private String createApprovalContent(String fileName) {

        CreateEpubLotApprovalPayload payload = new CreateEpubLotApprovalPayload(fileName.trim());

        return gson.toJson(payload);
    }

    private String createPreview(EpubProjectContext project, String fileName) {

        Path projectRoot = project.getProjectRoot().toAbsolutePath().normalize();
        Path targetFile = projectRoot.resolve("OEBPS").resolve("Text").resolve(fileName).normalize();

        StringBuilder builder = new StringBuilder();

        builder.append("내용\n\n");
        builder.append("- 생성 파일: ").append(targetFile).append("\n");
        builder.append("- 제목: 표 목록\n");
        builder.append("- Manifest ID: ").append(LOT_MANIFEST_ID).append("\n");
        builder.append("- Manifest href: Text/").append(fileName).append("\n");
        builder.append("- Media type: ").append(XHTML_MEDIA_TYPE).append("\n");
        builder.append("- Spine 등록: ").append(LOT_MANIFEST_ID).append("\n");
        builder.append("- Stylesheet: nav.css\n");
        builder.append("- 수집 대상: Spine 순서의 XHTML 문서에 포함된 table 요소\n");
        builder.append("- 실제 파일 생성 및 package 갱신은 승인 후 수행됩니다.\n");

        return builder.toString();
    }

    private String resolveRunId(ToolRequest request, ToolContext context) {

        if (context != null && context.getRequestId() != null && !context.getRequestId().isBlank()) return context.getRequestId().trim();
        if (request.getRequestId() != null && !request.getRequestId().isBlank()) return request.getRequestId().trim();

        throw new IllegalStateException("runId is not available from ToolContext or ToolRequest.");
    }

    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private Map<String, Object> stringProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }

    private String getString(ToolRequest request, String name, String defaultValue) {

        String value = request.getArgument(name, String.class);
        String normalized = normalize(value);

        return normalized != null ? normalized : defaultValue;
    }

    private String normalize(String value) {

        if (value == null || value.isBlank()) return null;

        return value.trim();
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }

}