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
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubPartApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.part.DefaultEpubPartXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartPage;
import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.resource.stylesheet.EpubStylesheetResolver;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class UpdateEpubPartTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_part";

    private static final String DESCRIPTION =
            "Updates an existing EPUB Part XHTML file. "
                    + "Use this tool whenever the user asks to modify, edit, append, rewrite, replace, or otherwise change an existing EPUB Part. "
                    + "If the existing Part content is required to preserve or modify current content, call read_epub_part first. "
                    + "The content argument must contain the complete XHTML fragment that will be placed inside the part-description element. "
                    + "When preserving existing paragraphs, include all paragraphs that must remain in the final content. "
                    + "Do not include html, head, body, section, h1, or part-description wrapper elements in content. "
                    + "This update requires explicit user approval before the EPUB file is changed.";

    private static final String APPROVAL_TITLE = "EPUB 부 수정";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final EpubPartXhtmlGenerator xhtmlGenerator;
    private final EpubStylesheetResolver stylesheetResolver;
    private final Gson gson;

    public UpdateEpubPartTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new DefaultEpubPartXhtmlGenerator(), new EpubStylesheetResolver(), new Gson());
    }

    public UpdateEpubPartTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, EpubPartXhtmlGenerator xhtmlGenerator, EpubStylesheetResolver stylesheetResolver, Gson gson) {

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
        return DESCRIPTION;
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("partNumber", integerProperty("수정할 EPUB 부 번호입니다. 예: 2"));
        properties.put("fileName", stringProperty("수정할 기존 EPUB 부 XHTML 파일명입니다. 예: part02.xhtml"));
        properties.put("title", stringProperty("수정 후 사용할 EPUB 부 제목입니다. '2부' 같은 번호 접두어는 제외합니다."));
        properties.put("content", stringProperty("part-description 내부에 들어갈 최종 XHTML Fragment입니다. 예: <p id=\"part02_p_1\">내용</p>. 기존 문단을 유지해야 한다면 최종적으로 남아야 할 기존 문단도 모두 포함합니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("partNumber", "fileName", "title", "content"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            int partNumber = requireInteger(request, "partNumber");
            String fileName = requireString(request, "fileName");
            String title = requireString(request, "title");
            String content = requireString(request, "content");

            validatePartNumber(partNumber);
            validateFileName(fileName);

            Path targetFile = resolveTargetFile(project, fileName);

            if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB part does not exist: " + targetFile + ". Use create_epub_part instead.");

            String stylesheetHref = stylesheetResolver.resolveHref(targetFile);
            String xhtml = xhtmlGenerator.generate(partNumber, title, content, stylesheetHref);

            EpubPartPage page = new EpubPartPage(partNumber, fileName, title, xhtml);

            String approvalContent = createApprovalContent(page);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    createApprovalMessage(fileName),
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
            data.put("previewTitle", "내용");
            data.put("preview", page.getXhtml());

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB part update approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB part update: " + safeMessage(exception);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.FAILED)
                    .message(errorMessage)
                    .errorCode("EPUB_PART_UPDATE_FAILED")
                    .errorMessage(errorMessage)
                    .cause(exception)
                    .build();
        }
    }

    private String createApprovalContent(EpubPartPage page) {

        UpdateEpubPartApprovalPayload payload = new UpdateEpubPartApprovalPayload(page.getPartNumber(), page.getFileName(), page.getTitle(), page.getXhtml());

        return gson.toJson(payload);
    }

    private Path resolveTargetFile(EpubProjectContext project, String fileName) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(fileName).toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalArgumentException("Invalid EPUB part file path: " + fileName);

        return targetFile;
    }

    private String createApprovalMessage(String fileName) {
        return "다음 내용으로 " + fileName + "을 수정하시겠습니까?";
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");
        if (!Files.isDirectory(project.getTextDirectory())) throw new IllegalStateException("Current EPUB Text directory does not exist: " + project.getTextDirectory());

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

    private void validatePartNumber(int partNumber) {

        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");
    }

    private void validateFileName(String fileName) {

        if (fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("fileName must contain only the XHTML file name.");
        if (!fileName.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("EPUB part file must use the .xhtml extension.");
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

    private String requireString(ToolRequest request, String name) {

        String value = getString(request, name);

        if (value == null) throw new IllegalArgumentException(name + " must not be blank.");

        return value;
    }

    private int requireInteger(ToolRequest request, String name) {

        Integer value = request.getArgument(name, Integer.class);

        if (value == null) throw new IllegalArgumentException(name + " must not be null.");

        return value.intValue();
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
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}