/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.loi;

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
import kr.co.goms.gomsbook.ai.agent.approval.payload.CreateEpubLoiApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class CreateEpubLoiTool implements AgentTool {

    public static final String TOOL_NAME = "create_epub_loi";

    private static final String APPROVAL_TITLE = "EPUB 이미지 목차 생성";
    private static final String LOI_FILE_NAME = "loi.xhtml";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public CreateEpubLoiTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new Gson());
    }

    public CreateEpubLoiTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

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

        return "Creates the EPUB List of Illustrations (LOI) document named loi.xhtml for the current EPUB project. "
                + "Use this tool when the user asks to create, generate, add, or build an image table of contents, image index, List of Illustrations, or LOI. "
                + "The LOI is generated automatically by scanning image references in the current EPUB XHTML reading order. "
                + "Images that provide both an id and alt text are used as LOI navigation entries, while referenced image resources may also be synchronized with the EPUB manifest. "
                + "The stylesheet is resolved automatically from the current EPUB project and must not be requested from the user. "
                + "The generated loi.xhtml is registered in the EPUB manifest and spine, and its reading position is determined by the configured EpubSpineOrderPolicy. "
                + "The target file name is always loi.xhtml and must not be chosen or inferred by the model. "
                + "This tool only prepares creation and requires explicit user approval before any EPUB file or package document is modified. "
                + "Do not use this tool when loi.xhtml already exists; use update_epub_loi when an existing LOI must be regenerated or modified. "
                + "Do not use this tool to create the main navigation document, table list, author page, part page, chapter page, or other EPUB components.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());
        schema.put("required", List.of());
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();
            Path targetFile = resolveTargetFile(project);

            validateLoiNotExists(targetFile);

            String approvalContent = createApprovalContent();
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String approvalMessage = "현재 EPUB 프로젝트의 이미지 정보를 분석하여 " + LOI_FILE_NAME + "을 생성하시겠습니까?";

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    approvalMessage,
                    LOI_FILE_NAME,
                    approvalContent);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("preview", createPreview());
            data.put("previewTitle", AgentToolResultDisplayConstant.PREVIEW_TITLE_CONTENT);
            data.put("displayInstruction", AgentToolResultDisplayConstant.PREVIEW_DISPLAY_INSTRUCTION);
            data.put("targetPath", targetFile.toString());

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB LOI creation approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB LOI creation: " + safeMessage(exception);

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
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");

        Path projectRoot = project.getProjectRoot().toAbsolutePath().normalize();
        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path packageDocument = project.getPackageDocument().toAbsolutePath().normalize();

        if (!Files.isDirectory(projectRoot)) throw new IllegalStateException("Current EPUB project root does not exist: " + projectRoot);
        if (!Files.isDirectory(textDirectory)) throw new IllegalStateException("Current EPUB Text directory does not exist: " + textDirectory);
        if (!Files.isRegularFile(packageDocument)) throw new IllegalStateException("Current EPUB package document does not exist: " + packageDocument);

        return project;
    }

    private Path resolveTargetFile(EpubProjectContext project) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(LOI_FILE_NAME).toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalStateException("LOI XHTML must be inside the EPUB Text directory.");

        return targetFile;
    }

    private void validateLoiNotExists(Path targetFile) {

        if (Files.exists(targetFile)) throw new IllegalStateException("EPUB LOI already exists: " + targetFile + ". Use update_epub_loi instead.");
    }

    private String createApprovalContent() {

        CreateEpubLoiApprovalPayload payload = new CreateEpubLoiApprovalPayload(LOI_FILE_NAME);

        return gson.toJson(payload);
    }

    private String createPreview() {

        return "이미지 목차(" + LOI_FILE_NAME + ")를 생성합니다.\n"
                + "현재 EPUB의 spine 읽기 순서에 따라 XHTML 문서를 분석하고,\n"
                + "id와 alt가 지정된 이미지를 이미지 목차 항목으로 구성합니다.";
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

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }

}