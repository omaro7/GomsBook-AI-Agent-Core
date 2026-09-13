/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.xhtml;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CleanEpubTypographyApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyChangeItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyOperation;
import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyUpdateResult;
import kr.co.goms.gomsbook.ai.epub.updater.xhtml.EpubTypographyUpdater;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class CleanEpubTypographyTool implements AgentTool {

    public static final String TOOL_NAME = "clean_epub_typography";

    private static final String DESCRIPTION = "Cleans and normalizes typography in EPUB XHTML text content. Use this tool when the user asks to standardize typographic punctuation, replace straight single quotes with typographic single quotes, replace straight double quotes with typographic double quotes, normalize apostrophes, or convert repeated three-dot sequences into ellipsis characters. Supported operations are SINGLE_QUOTES, DOUBLE_QUOTES, ELLIPSIS, and ALL. The optional fileName argument limits cleanup to a single XHTML file. When fileName is omitted, all XHTML files in the EPUB Text directory are processed. Only text nodes are normalized; XHTML elements, attributes, links, IDs, classes, ARIA attributes, scripts, and styles are not modified. This cleanup operation requires explicit user approval before any XHTML file is changed.";

    private static final String APPROVAL_TITLE = "EPUB 문장부호 정리";

    private static final int PREVIEW_LIMIT = 10;

    private final CurrentProjectProvider currentProjectProvider;

    private final AgentApprovalService approvalService;

    private final EpubTypographyUpdater typographyUpdater;

    private final Gson gson;

    public CleanEpubTypographyTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, EpubTypographyUpdater typographyUpdater) {
        this(currentProjectProvider, approvalService, typographyUpdater, new Gson());
    }

    public CleanEpubTypographyTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, EpubTypographyUpdater typographyUpdater, Gson gson) {
        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (approvalService == null) throw new IllegalArgumentException("approvalService must not be null.");
        if (typographyUpdater == null) throw new IllegalArgumentException("typographyUpdater must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.approvalService = approvalService;
        this.typographyUpdater = typographyUpdater;
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
        properties.put("fileName", stringProperty("정리할 XHTML 파일명입니다. 예: chapter01_01.xhtml. 생략하면 EPUB Text 폴더의 모든 XHTML 파일을 대상으로 합니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("operation"));
        schema.put("additionalProperties", false);

        return Map.copyOf(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {
        try {
            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();
            EpubTypographyOperation operation = requireOperation(request);
            String fileName = getString(request, "fileName");

            if (fileName != null) validateFileName(fileName);

            EpubTypographyUpdateResult previewResult = typographyUpdater.preview(project.getProjectRoot(), operation, fileName);

            if (!previewResult.hasChanges()) return noChangesResult(request, operation, fileName, previewResult);

            String approvalContent = createApprovalContent(operation, fileName);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            AgentApproval approval = approvalService.create(runId, projectId, AgentApprovalAction.PREFIX + TOOL_NAME, APPROVAL_TITLE, createApprovalMessage(fileName, previewResult), fileName, approvalContent);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("operation", operation.name());
            data.put("fileCount", previewResult.getFileCount());
            data.put("changeCount", previewResult.getChangeCount());
            data.put("singleQuoteCount", previewResult.getSingleQuoteCount());
            data.put("doubleQuoteCount", previewResult.getDoubleQuoteCount());
            data.put("ellipsisCount", previewResult.getEllipsisCount());
            data.put("previewTitle", "내용");
            data.put("preview", createPreview(previewResult));

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB typography cleanup approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {
            String errorMessage = "Failed to prepare EPUB typography cleanup: " + safeMessage(exception);

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

    private ToolResult noChangesResult(ToolRequest request, EpubTypographyOperation operation, String fileName, EpubTypographyUpdateResult result) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("approvalRequired", false);
        data.put("operation", operation.name());
        data.put("fileName", fileName);
        data.put("fileCount", result.getFileCount());
        data.put("changeCount", 0);
        data.put("singleQuoteCount", 0);
        data.put("doubleQuoteCount", 0);
        data.put("ellipsisCount", 0);

        return ToolResult.builder()
                .toolName(TOOL_NAME)
                .requestId(request.getRequestId())
                .toolCallId(request.getToolCallId())
                .status(ToolStatus.SUCCESS)
                .message("정리할 EPUB 문장부호가 없습니다.")
                .data(data)
                .build();
    }

    private String createApprovalContent(EpubTypographyOperation operation, String fileName) {
        CleanEpubTypographyApprovalPayload payload = new CleanEpubTypographyApprovalPayload(operation, fileName);

        return gson.toJson(payload);
    }

    private String createApprovalMessage(String fileName, EpubTypographyUpdateResult result) {
        String target = fileName == null ? "Text 폴더 전체 XHTML" : fileName;

        return target + "의 문장부호를 정리하시겠습니까? (승인하시면 실제 파일에 반영됩니다.) 작은따옴표 " + result.getSingleQuoteCount() + "건, 큰따옴표 " + result.getDoubleQuoteCount() + "건, 말줄임표 " + result.getEllipsisCount() + "건, 총 " + result.getChangeCount() + "건";
    }

    private String createPreview(EpubTypographyUpdateResult result) {
        StringBuilder preview = new StringBuilder();

        preview.append("변경 파일: ").append(result.getFileCount()).append("개\n");
        preview.append("작은따옴표: ").append(result.getSingleQuoteCount()).append("건\n");
        preview.append("큰따옴표: ").append(result.getDoubleQuoteCount()).append("건\n");
        preview.append("말줄임표: ").append(result.getEllipsisCount()).append("건\n");
        preview.append("총 변경: ").append(result.getChangeCount()).append("건");

        List<EpubTypographyChangeItem> items = result.getPreviewItems(PREVIEW_LIMIT);

        if (items.isEmpty()) return preview.toString();

        preview.append("\n\n");

        for (int index = 0; index < items.size(); index++) {
            EpubTypographyChangeItem item = items.get(index);

            if (index > 0) preview.append("\n\n");

            preview.append(item.fileName()).append("\n");
            preview.append(operationLabel(item.operation())).append(" ").append(item.changeCount()).append("건\n");
            preview.append(item.before()).append("\n");
            preview.append("→ ").append(item.after());
        }

        if (result.getChangedItems().size() > PREVIEW_LIMIT) preview.append("\n\n외 ").append(result.getChangedItems().size() - PREVIEW_LIMIT).append("개 변경 항목");

        return preview.toString();
    }

    private String operationLabel(EpubTypographyOperation operation) {
        if (operation == EpubTypographyOperation.SINGLE_QUOTES) return "작은따옴표";
        if (operation == EpubTypographyOperation.DOUBLE_QUOTES) return "큰따옴표";
        if (operation == EpubTypographyOperation.ELLIPSIS) return "말줄임표";

        return operation.name();
    }

    private EpubProjectContext requireCurrentProject() {
        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");
        if (!Files.isDirectory(project.getTextDirectory())) throw new IllegalStateException("Current EPUB Text directory does not exist: " + project.getTextDirectory());

        return project;
    }

    private EpubTypographyOperation requireOperation(ToolRequest request) {
        String value = requireString(request, "operation");

        try {
            return EpubTypographyOperation.from(value);

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("operation must be one of SINGLE_QUOTES, DOUBLE_QUOTES, ELLIPSIS, ALL.");
        }
    }

    private void validateFileName(String fileName) {
        if (fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("fileName must contain only the XHTML file name.");
        if (!fileName.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("EPUB typography target file must use the .xhtml extension.");
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

    private Map<String, Object> operationProperty() {
        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("enum", List.of("SINGLE_QUOTES", "DOUBLE_QUOTES", "ELLIPSIS", "ALL"));
        property.put("description", "정리할 문장부호 종류입니다. SINGLE_QUOTES=작은따옴표 및 apostrophe, DOUBLE_QUOTES=큰따옴표, ELLIPSIS=말줄임표, ALL=전체.");

        return Map.copyOf(property);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return Map.copyOf(property);
    }

    private String requireString(ToolRequest request, String name) {
        String value = getString(request, name);

        if (value == null) throw new IllegalArgumentException(name + " must not be blank.");

        return value;
    }

    private String getString(ToolRequest request, String name) {
        return trimToNull(request.getArgument(name, String.class));
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