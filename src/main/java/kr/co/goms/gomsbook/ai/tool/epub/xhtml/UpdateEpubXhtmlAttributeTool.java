/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.xhtml;

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
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubXhtmlAttributeApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class UpdateEpubXhtmlAttributeTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_xhtml_attribute";

    private static final String DESCRIPTION = "Prepares an update to an attribute of an element in an existing EPUB XHTML file. "
    		+ "Use this tool when the user asks to add or change an XHTML element attribute such as role, id, class, epub:type, aria-label, aria-labelledby, lang, or xml:lang. "
    		+ "The update requires explicit user approval before the XHTML file is changed.";

    private static final String APPROVAL_TITLE = "EPUB XHTML 속성 수정";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public UpdateEpubXhtmlAttributeTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {

        this(currentProjectProvider, approvalService, new Gson());
    }

    public UpdateEpubXhtmlAttributeTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

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

        properties.put("fileName", stringProperty("수정할 EPUB XHTML 파일명입니다. 예: nav.xhtml"));
        properties.put("elementName", stringProperty("수정할 XHTML 요소 이름입니다. 예: nav"));
        properties.put("matchAttributeName", stringProperty("대상 요소를 식별할 속성 이름입니다. namespace prefix를 사용할 수 있습니다. 예: epub:type"));
        properties.put("matchAttributeValue", stringProperty("대상 요소를 식별할 속성 값입니다. 예: toc"));
        properties.put("attributeName", stringProperty("추가하거나 수정할 속성 이름입니다. 예: role"));
        properties.put("attributeValue", stringProperty("추가하거나 수정할 속성 값입니다. 예: doc-toc"));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("fileName", "elementName", "matchAttributeName", "matchAttributeValue", "attributeName", "attributeValue"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            String fileName = requireString(request, "fileName");
            String elementName = requireString(request, "elementName");
            String matchAttributeName = requireString(request, "matchAttributeName");
            String matchAttributeValue = requireString(request, "matchAttributeValue");
            String attributeName = requireString(request, "attributeName");
            String attributeValue = requireString(request, "attributeValue");

            validateFileName(fileName);

            Path targetFile = resolveTargetFile(project, fileName);

            if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB XHTML file does not exist: " + targetFile);

            UpdateEpubXhtmlAttributeApprovalPayload payload = new UpdateEpubXhtmlAttributeApprovalPayload(fileName, elementName, matchAttributeName, matchAttributeValue, attributeName, attributeValue);

            String approvalContent = gson.toJson(payload);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);

            AgentApproval approval = approvalService.create(runId, projectId, AgentApprovalAction.PREFIX + TOOL_NAME, APPROVAL_TITLE, createApprovalMessage(payload), fileName, approvalContent);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("previewTitle", "내용");
            data.put("preview", createPreview(payload));

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB XHTML attribute update approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB XHTML attribute update: " + safeMessage(exception);

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

    private String createApprovalMessage(UpdateEpubXhtmlAttributeApprovalPayload payload) {

        return payload.getFileName() + "의 <" + payload.getElementName() + "> 요소에 " + payload.getAttributeName() + "=\"" + payload.getAttributeValue() + "\" 속성을 적용하시겠습니까?";
    }

    private String createPreview(UpdateEpubXhtmlAttributeApprovalPayload payload) {

        return "파일: " + payload.getFileName()
                + "\n대상: <" + payload.getElementName() + " " + payload.getMatchAttributeName() + "=\"" + payload.getMatchAttributeValue() + "\">"
                + "\n속성: " + payload.getAttributeName() + "=\"" + payload.getAttributeValue() + "\"";
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");
        if (!Files.isDirectory(project.getTextDirectory())) throw new IllegalStateException("Current EPUB Text directory does not exist: " + project.getTextDirectory());

        return project;
    }

    private Path resolveTargetFile(EpubProjectContext project, String fileName) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(fileName).toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalArgumentException("Invalid EPUB XHTML file path: " + fileName);

        return targetFile;
    }

    private void validateFileName(String fileName) {

        if (fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("fileName must contain only the XHTML file name.");
        if (!fileName.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("EPUB XHTML file must use the .xhtml extension.");
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

    private String requireString(ToolRequest request, String name) {

        String value = request.getArgument(name, String.class);

        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");

        return value.trim();
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}