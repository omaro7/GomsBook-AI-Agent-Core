/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.part;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CreateEpubPartApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.part.DefaultEpubPartXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.resource.stylesheet.EpubStylesheetResolver;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class CreateEpubPartTool implements AgentTool {

    public static final String TOOL_NAME = "create_epub_part";

    private static final String APPROVAL_TITLE = "EPUB 부 생성";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final EpubPartXhtmlGenerator xhtmlGenerator;
    private final EpubStylesheetResolver stylesheetResolver;
    private final Gson gson;

    public CreateEpubPartTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService) {

        this(
                currentProjectProvider,
                approvalService,
                new DefaultEpubPartXhtmlGenerator(),
                new EpubStylesheetResolver(),
                new Gson());
    }

    public CreateEpubPartTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService,
            EpubPartXhtmlGenerator xhtmlGenerator,
            EpubStylesheetResolver stylesheetResolver,
            Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (approvalService == null) throw new IllegalArgumentException("approvalService must not be null.");
        if (xhtmlGenerator == null) throw new IllegalArgumentException("xhtmlGenerator must not be null.");
        if (stylesheetResolver == null) throw new IllegalArgumentException("stylesheetResolver must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.approvalService = approvalService;
        this.xhtmlGenerator = xhtmlGenerator;
        this.stylesheetResolver = stylesheetResolver;
        this.gson = gson;
    }

    @Override
    public String getName() {

        return TOOL_NAME;
    }

    @Override
    public String getDescription() {

        return "현재 EPUB 프로젝트에 새로운 부(Part) XHTML 페이지를 생성하기 위한 미리보기를 만들고 사용자 승인을 요청합니다. "
                + "사용자가 1부, 2부와 같은 새로운 부 또는 Part 구분 페이지를 생성해 달라고 요청할 때 사용합니다. "
                + "partNumber와 title만 사용자 요청에서 결정합니다. "
                + "파일명, 저장 위치, stylesheetHref는 현재 EPUB 프로젝트 구조에서 자동으로 결정합니다. "
                + "현재 EPUB에 적용된 스타일시트는 EpubStylesheetResolver를 통해 자동으로 연결합니다. "
                + "스타일시트가 없는 경우 임의로 생성하거나 복사하지 않습니다. "
                + "style1.css 적용은 apply_epub_stylesheet 도구를 사용합니다. "
                + "이미 동일한 Part XHTML이 존재하는 경우 덮어쓰지 않습니다. "
                + "기존 Part 수정에는 update_epub_part를 사용해야 합니다. "
                + "실제 XHTML 생성 전에는 반드시 사용자 승인이 필요합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("partNumber", integerProperty("생성할 부 번호입니다. 예: 1부는 1, 2부는 2입니다."));
        properties.put("title", stringProperty("생성할 부의 제목입니다. '1부' 등의 번호는 포함하지 않고 제목만 전달합니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("partNumber", "title"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            int partNumber = getPartNumber(request);
            String title = getRequiredString(request, "title");

            String fileName = createFileName(partNumber);
            Path targetFile = resolveTargetFile(project, fileName);

            validatePartNotExists(targetFile);

            String stylesheetHref = stylesheetResolver.resolveHref(targetFile);
            
            String xhtml = xhtmlGenerator.generate(partNumber, title, stylesheetHref);

            String content = createApprovalContent(fileName, partNumber, title, stylesheetHref, xhtml);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String approvalMessage = partNumber + "부 '" + title + "'을 생성하시겠습니까?";

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
            data.put("previewTitle", "내용");
            data.put("preview", xhtml);
            data.put("partNumber", partNumber);
            data.put("partTitle", title);
            data.put("stylesheetHref", stylesheetHref);
            data.put("targetPath", targetFile.toString());

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB part creation approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB part creation: " + safeMessage(exception);

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
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();

        if (!Files.isDirectory(textDirectory)) throw new IllegalStateException("Current EPUB Text directory does not exist: " + textDirectory);

        return project;
    }

    private int getPartNumber(ToolRequest request) {

        Integer partNumber = request.getArgument("partNumber", Integer.class);

        if (partNumber == null) throw new IllegalArgumentException("partNumber must not be null.");
        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");

        return partNumber;
    }

    private String getRequiredString(
            ToolRequest request,
            String name) {

        String value = request.getArgument(name, String.class);
        String normalized = trimToNull(value);

        if (normalized == null) throw new IllegalArgumentException(name + " must not be blank.");

        return normalized;
    }

    private String createFileName(int partNumber) {

        return String.format("part%02d.xhtml", partNumber);
    }

    private Path resolveTargetFile(
            EpubProjectContext project,
            String fileName) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(fileName).toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalStateException("Part XHTML must be inside the EPUB Text directory.");

        return targetFile;
    }

    private void validatePartNotExists(Path targetFile) {

        if (Files.exists(targetFile)) {
            throw new IllegalStateException("EPUB part already exists: " + targetFile + ". Use update_epub_part instead.");
        }
    }

    private String createApprovalContent(
            String fileName,
            int partNumber,
            String title,
            String stylesheetHref,
            String xhtml) {

        CreateEpubPartApprovalPayload payload = new CreateEpubPartApprovalPayload();

        payload.setFileName(fileName);
        payload.setPartNumber(partNumber);
        payload.setTitle(title);
        payload.setStylesheetHref(stylesheetHref);
        payload.setXhtml(xhtml);

        return gson.toJson(payload);
    }

    private String resolveRunId(
            ToolRequest request,
            ToolContext context) {

        if (context != null && context.getRequestId() != null && !context.getRequestId().isBlank()) return context.getRequestId().trim();
        if (request != null && request.getRequestId() != null && !request.getRequestId().isBlank()) return request.getRequestId().trim();

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

    private Map<String, Object> integerProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "integer");
        property.put("minimum", 1);
        property.put("description", description);

        return property;
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}