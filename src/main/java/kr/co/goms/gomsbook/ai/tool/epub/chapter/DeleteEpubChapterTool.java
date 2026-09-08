/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.chapter;

import java.nio.charset.StandardCharsets;
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
import kr.co.goms.gomsbook.ai.agent.approval.payload.DeleteEpubChapterApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class DeleteEpubChapterTool implements AgentTool {

    public static final String TOOL_NAME = "delete_epub_chapter";

    private static final String APPROVAL_TITLE = "EPUB 장 삭제";
    private static final String ERROR_CODE = "EPUB_CHAPTER_DELETE_PREPARE_FAILED";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public DeleteEpubChapterTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new Gson());
    }

    public DeleteEpubChapterTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

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

    /**
     * Positive trigger: delete, remove an existing chapter
     * 현재 프로젝트 대상: current EPUB project
     * 승인 필수: 실제 파일 삭제 전에 explicit user approval
     * Tool 경계: Chapter 내용 수정 및 Part/Nav/Author 등 다른 구성요소 삭제 금지
     */
    @Override
    public String getDescription() {
        return "Deletes an existing chapter XHTML document from the current EPUB project. "
                + "Use this tool immediately when the user explicitly asks to delete or remove an existing chapter from the current EPUB project. "
                + "Use the requested or inferred part number and chapter number to identify the target chapter. "
                + "The chapter file name is automatically determined as chapter{partNumber}_{chapterNumber}.xhtml unless an explicit fileName is provided. "
                + "This tool only prepares the chapter deletion and requires explicit user approval before the XHTML file is deleted. "
                + "Do not use this tool to modify chapter content; use update_epub_chapter instead. "
                + "Do not use this tool to delete parts, navigation, metadata, author information, copyright information, or other EPUB components.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("partNumber", integerProperty("삭제할 장이 속한 부(part)의 번호입니다. 1 이상의 정수입니다."));
        properties.put("chapterNumber", integerProperty("삭제할 장의 해당 부 안에서의 장 번호입니다. 1 이상의 정수입니다."));
        properties.put("fileName", stringProperty("삭제할 XHTML 파일명입니다. 생략하면 chapter{부번호}_{장번호}.xhtml 형식으로 자동 결정합니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("partNumber", "chapterNumber"));
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

            validateArguments(partNumber, chapterNumber, fileName);

            Path targetFile = project.getTextDirectory().resolve(fileName).toAbsolutePath().normalize();

            validateTargetFile(project, targetFile);

            String preview = readPreview(targetFile);
            String projectId = resolveProjectId(project);

            DeleteEpubChapterApprovalPayload payload = new DeleteEpubChapterApprovalPayload(projectId, partNumber, chapterNumber, fileName);
            String approvalContent = gson.toJson(payload);

            String runId = resolveRunId(request, context);
            String approvalMessage = partNumber + "부 " + chapterNumber + "장 " + fileName + "을 삭제하시겠습니까?";

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
                    .message("EPUB chapter deletion approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String message = "Failed to prepare EPUB chapter deletion: " + safeMessage(exception);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.FAILED)
                    .message(message)
                    .errorCode(ERROR_CODE)
                    .errorMessage(message)
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
        if (!Files.exists(targetFile)) throw new IllegalStateException("EPUB chapter does not exist: " + targetFile + ".");
        if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB chapter path is not a file: " + targetFile);

        Path fileName = targetFile.getFileName();

        if (fileName == null || !fileName.toString().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("Chapter file must be XHTML.");
    }

    private void validateArguments(int partNumber, int chapterNumber, String fileName) {

        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");
        if (chapterNumber <= 0) throw new IllegalArgumentException("chapterNumber must be greater than 0.");
        if (isBlank(fileName)) throw new IllegalArgumentException("fileName must not be blank.");
        if (!fileName.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("fileName must have the .xhtml extension.");
    }

    private String readPreview(Path targetFile) {

        try {

            return Files.readString(targetFile, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to read EPUB chapter XHTML for deletion preview: " + targetFile, exception);
        }
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