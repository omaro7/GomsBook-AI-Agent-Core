/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.chapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.AgentToolResultDisplayConstant;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CreateEpubChapterApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.chapter.DefaultEpubChapterXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.generation.chapter.EpubChapterXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.resource.stylesheet.EpubStylesheetResolver;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class CreateEpubChapterTool implements AgentTool {

    public static final String TOOL_NAME = "create_epub_chapter";

    private static final String APPROVAL_TITLE = "EPUB 장 생성";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final EpubChapterXhtmlGenerator xhtmlGenerator;
    private final EpubStylesheetResolver stylesheetResolver;
    private final Gson gson;

    public CreateEpubChapterTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new DefaultEpubChapterXhtmlGenerator(), new EpubStylesheetResolver(), new Gson());
    }

    public CreateEpubChapterTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, EpubChapterXhtmlGenerator xhtmlGenerator, EpubStylesheetResolver stylesheetResolver, Gson gson) {

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

    /**
     * Positive trigger: create, write, add, or generate a new chapter
	 * 현재 프로젝트 대상: current EPUB project
	 * 승인 필수: 실제 파일 생성 전에 explicit user approval
	 * Tool 경계: 기존 Chapter 수정 및 Part/Nav/Author 등 다른 구성요소 생성 금지
     */
    @Override
    public String getDescription() {
        return "Creates a new chapter XHTML document in the current EPUB project. "
                + "Use this tool immediately when the user asks to create, write, add, or generate a new chapter for the current EPUB project. "
                + "Use the requested or inferred part number, chapter number, chapter title, and chapter body content to prepare the new chapter. "
                + "The chapter body must be provided as an XHTML fragment, not as a complete XHTML document. "
                + "The stylesheet path is determined automatically from the current EPUB project structure and must not be requested from the user. "
                + "This tool only prepares the chapter creation and requires explicit user approval before any EPUB file is created or modified. "
                + "Do not use this tool when the target chapter already exists; use update_epub_chapter instead. "
                + "Do not use this tool to create or modify parts, navigation, metadata, author information, copyright information, or other EPUB components.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("partNumber", integerProperty("장이 속한 부(part)의 번호입니다. 1 이상의 정수입니다."));
        properties.put("chapterNumber", integerProperty("해당 부 안에서의 장 번호입니다. 1 이상의 정수입니다."));
        properties.put("fileName", stringProperty("생성할 XHTML 파일명입니다. 생략하면 chapter01_01.xhtml과 같은 chapter{부번호 2자리}_{장번호 2자리}.xhtml 형식으로 자동 결정합니다."));
        properties.put("title", stringProperty("장의 제목입니다. 번호를 제외한 제목만 전달합니다."));
        properties.put("content", stringProperty("장 본문 XHTML fragment입니다. p, section 등의 XHTML 요소를 사용할 수 있습니다. XHTML 문서 전체를 전달하지 않습니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("partNumber", "chapterNumber", "title", "content"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            int partNumber = getInteger(request, "partNumber");
            int chapterNumber = getInteger(request, "chapterNumber");
            String fileName = getString(request, "fileName", createDefaultFileName(partNumber, chapterNumber));
            String title = getString(request, "title");
            String content = getString(request, "content");

            validateArguments(partNumber, chapterNumber, fileName, title, content);

            Path targetFile = project.getTextDirectory().resolve(fileName).toAbsolutePath().normalize();

            validateTargetFile(project, targetFile);

            String stylesheetHref = stylesheetResolver.resolveHref(targetFile);
            String preview = xhtmlGenerator.generate(partNumber, chapterNumber, title, content, stylesheetHref);

            CreateEpubChapterApprovalPayload payload = new CreateEpubChapterApprovalPayload(partNumber, chapterNumber, fileName, title, content);
            String approvalContent = gson.toJson(payload);

            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String approvalMessage = "다음 내용으로 " + fileName + "을 생성하시겠습니까?";

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    approvalMessage,
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
            data.put("preview", preview);
            data.put("previewTitle", AgentToolResultDisplayConstant.PREVIEW_TITLE_CONTENT);
            data.put("displayInstruction", AgentToolResultDisplayConstant.PREVIEW_DISPLAY_INSTRUCTION);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB chapter creation approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.FAILED)
                    .message("Failed to prepare EPUB chapter creation: " + safeMessage(exception))
                    .build();
        }
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

        return project;
    }

    private void validateTargetFile(EpubProjectContext project, Path targetFile) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalArgumentException("Chapter file must be located inside the EPUB Text directory.");
        if (Files.exists(targetFile)) throw new IllegalStateException("EPUB chapter already exists: " + targetFile + ". Use update_epub_chapter instead.");
    }

    private void validateArguments(int partNumber, int chapterNumber, String fileName, String title, String content) {

        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");
        if (chapterNumber <= 0) throw new IllegalArgumentException("chapterNumber must be greater than 0.");
        if (isBlank(fileName)) throw new IllegalArgumentException("fileName must not be blank.");
        if (!fileName.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("fileName must have the .xhtml extension.");
        if (isBlank(title)) throw new IllegalArgumentException("title must not be blank.");
        if (isBlank(content)) throw new IllegalArgumentException("content must not be blank.");
    }

    private String createDefaultFileName(int partNumber, int chapterNumber) {
        return String.format("chapter%02d_%02d.xhtml", partNumber, chapterNumber);
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

    private int getInteger(ToolRequest request, String name) {

        Integer value = request.getArgument(name, Integer.class);

        if (value == null) throw new IllegalArgumentException(name + " must not be null.");

        return value;
    }

    private String getString(ToolRequest request, String name) {

        String value = request.getArgument(name, String.class);

        return trimToNull(value);
    }

    private String getString(ToolRequest request, String name, String defaultValue) {

        String value = getString(request, name);

        return value != null ? value : defaultValue;
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

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}