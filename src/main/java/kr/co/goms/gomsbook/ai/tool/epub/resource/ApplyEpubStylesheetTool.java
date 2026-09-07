/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.ApplyEpubStylesheetApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class ApplyEpubStylesheetTool implements AgentTool {

    public static final String TOOL_NAME = "apply_epub_stylesheet";

    private static final String APPROVAL_TITLE = "EPUB 스타일시트 적용";
    private static final String STYLES_DIRECTORY = "Styles";
    private static final String STYLESHEET_FILE_NAME = "style1.css";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public ApplyEpubStylesheetTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {

        this(currentProjectProvider, approvalService, new Gson());
    }

    public ApplyEpubStylesheetTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

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

        return "사용자가 지정한 style1.css 파일을 현재 EPUB 프로젝트의 Styles 폴더에 적용합니다. "
                + "사용자가 style1.css 파일 경로를 제공하고 현재 EPUB에 적용, 복사, 교체해 달라고 요청할 때 사용합니다. "
                + "sourcePath는 사용자가 제공한 실제 CSS 파일 경로를 사용해야 합니다. "
                + "대상 Styles 경로와 stylesheetHref는 사용자나 LLM에게 받지 않고 현재 EPUB 프로젝트 구조에서 결정합니다. "
                + "실제 파일 복사 또는 교체 전에는 반드시 사용자 승인이 필요합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("sourcePath", stringProperty("사용자가 제공한 style1.css 원본 파일의 절대 경로입니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("sourcePath"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            Path sourceFile = resolveSourceFile(request);
            Path targetFile = resolveTargetFile(project);

            String fileAction = Files.exists(targetFile) ? "UPDATE" : "CREATE";
            String content = createApprovalContent(sourceFile);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String approvalMessage = createApprovalMessage(sourceFile, targetFile, fileAction);

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    approvalMessage,
                    STYLESHEET_FILE_NAME,
                    content);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("sourcePath", sourceFile.toString());
            data.put("targetPath", targetFile.toString());
            data.put("fileAction", fileAction);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB stylesheet application approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB stylesheet application: " + safeMessage(exception);

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

    private Path resolveSourceFile(ToolRequest request) {

        String sourcePath = getString(request, "sourcePath");

        if (sourcePath == null) throw new IllegalArgumentException("sourcePath must not be blank.");

        Path sourceFile = Path.of(sourcePath).toAbsolutePath().normalize();

        if (!Files.exists(sourceFile)) throw new IllegalStateException("Stylesheet source file does not exist: " + sourceFile);
        if (!Files.isRegularFile(sourceFile)) throw new IllegalStateException("Stylesheet source path is not a file: " + sourceFile);

        String fileName = sourceFile.getFileName().toString();

        if (!STYLESHEET_FILE_NAME.equalsIgnoreCase(fileName)) {
            throw new IllegalArgumentException("Stylesheet source file must be named " + STYLESHEET_FILE_NAME + ": " + sourceFile);
        }

        return sourceFile;
    }

    private Path resolveTargetFile(EpubProjectContext project) {

        Path packageDocument = project.getPackageDocument().toAbsolutePath().normalize();
        Path contentRoot = packageDocument.getParent();

        if (contentRoot == null) throw new IllegalStateException("Unable to resolve EPUB content root from package document: " + packageDocument);

        return contentRoot.resolve(STYLES_DIRECTORY).resolve(STYLESHEET_FILE_NAME).toAbsolutePath().normalize();
    }

    private String createApprovalContent(Path sourceFile) {

        ApplyEpubStylesheetApprovalPayload payload = new ApplyEpubStylesheetApprovalPayload(sourceFile.toString(), STYLESHEET_FILE_NAME);

        return gson.toJson(payload);
    }

    private String createApprovalMessage(Path sourceFile, Path targetFile, String fileAction) {

        if ("UPDATE".equals(fileAction)) {
            return sourceFile + " 파일을 현재 EPUB의 " + targetFile + " 파일로 교체하시겠습니까?";
        }

        return sourceFile + " 파일을 현재 EPUB의 " + targetFile + " 위치에 적용하시겠습니까?";
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");
        if (!Files.isRegularFile(project.getPackageDocument())) throw new IllegalStateException("Current EPUB package document does not exist: " + project.getPackageDocument());

        return project;
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

    private Map<String, Object> stringProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }

    private String getString(ToolRequest request, String name) {

        String value = request.getArgument(name, String.class);

        return trimToNull(value);
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";

        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }
}