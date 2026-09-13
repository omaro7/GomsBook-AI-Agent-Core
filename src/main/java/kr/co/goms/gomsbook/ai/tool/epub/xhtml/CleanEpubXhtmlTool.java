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
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CleanEpubXhtmlApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

/**
 * Text 폴더 XHTML 소스 정리해줘
 *
 * {
 *   "scope": "TEXT"
 * }
 *
 * chapter01_01.xhtml 소스 정리해줘
 *
 * {
 *   "scope": "FILE",
 *   "fileName": "chapter01_01.xhtml"
 * }
 */
public final class CleanEpubXhtmlTool implements AgentTool {

    public static final String TOOL_NAME = "clean_epub_xhtml";

    private static final String DESCRIPTION = """
            Cleans and normalizes XHTML source files in the current EPUB project without intentionally changing their semantic content.
            Use this tool when the user asks to clean, cleanup, normalize, tidy, format, reformat, organize, or standardize EPUB XHTML source code.
            It can process either all XHTML files in the OEBPS/Text directory or a single specified XHTML file.
            The cleanup operation normalizes XHTML serialization, removes unnecessary formatting whitespace, and rewrites the document using the standard GomsBook XHTML output format.
            The generated XHTML preserves the required XML declaration and <!DOCTYPE html> declaration according to the GomsBook XHTML serialization policy.
            This tool is intended for source-level cleanup and normalization only.
            It must not intentionally rewrite body text, change headings, alter document semantics, modify EPUB semantic attributes, change ARIA accessibility information, add or remove content, or repair validation errors that require semantic decisions.
            Use Update tools when the user explicitly requests a content, attribute, element, metadata, or structural modification.
            Use Fix tools when the purpose is to repair a detected EPUB validation, accessibility, reference, packaging, or structural error.
            Use this Clean tool when the XHTML is semantically correct but its source formatting or serialization should be normalized.
            The operation modifies project files and therefore requires user approval before changes are applied.
            """;

    private static final String APPROVAL_TITLE = "EPUB XHTML 코드 정리";

    private final CurrentProjectProvider currentProjectProvider;

    private final AgentApprovalService approvalService;

    private final Gson gson;

    public CleanEpubXhtmlTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new Gson());
    }

    public CleanEpubXhtmlTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {
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

        properties.put("scope", enumProperty(List.of("TEXT", "FILE"), "Cleanup scope. TEXT cleans all XHTML files in OEBPS/Text, while FILE cleans one specified XHTML file."));
        properties.put("fileName", stringProperty("XHTML file name to clean when scope is FILE. Example: chapter01_01.xhtml"));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("scope"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {
        try {
            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();
            Map<String, Object> arguments = request.getArguments();

            if (arguments == null) arguments = Collections.emptyMap();

            Scope scope = Scope.from(requireString(arguments, "scope"));

            validateArguments(project, scope, arguments);

            String fileName = resolveFileName(scope, arguments);
            CleanEpubXhtmlApprovalPayload payload = createPayload(scope, fileName);
            String content = gson.toJson(payload);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String approvalFileName = resolveApprovalFileName(scope, fileName);
            String approvalMessage = createApprovalMessage(scope, fileName);

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    approvalMessage,
                    approvalFileName,
                    content);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("scope", scope.name());

            if (fileName != null) data.put("targetFileName", fileName);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB XHTML cleanup approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {
            String errorMessage = "Failed to prepare EPUB XHTML cleanup: " + safeMessage(exception);

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

    private CleanEpubXhtmlApprovalPayload createPayload(Scope scope, String fileName) {
        if (scope == Scope.TEXT) return CleanEpubXhtmlApprovalPayload.cleanText();

        return CleanEpubXhtmlApprovalPayload.cleanFile(fileName);
    }

    private void validateArguments(EpubProjectContext project, Scope scope, Map<String, Object> arguments) {
        Path textDirectory = resolveTextDirectory(project);

        if (!Files.isDirectory(textDirectory)) throw new IllegalStateException("EPUB Text directory was not found: " + textDirectory);

        if (scope == Scope.TEXT) return;

        String fileName = requireString(arguments, "fileName");

        validateFileName(fileName);

        Path file = textDirectory.resolve(fileName).normalize();

        if (!file.startsWith(textDirectory)) throw new IllegalArgumentException("XHTML file must be inside the EPUB Text directory.");
        if (!Files.isRegularFile(file)) throw new IllegalStateException("XHTML file was not found: " + file);
    }

    private String resolveFileName(Scope scope, Map<String, Object> arguments) {
        if (scope == Scope.TEXT) return null;

        return requireString(arguments, "fileName");
    }

    private void validateFileName(String fileName) {
        if (fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("fileName must contain only an XHTML file name.");
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xhtml")) throw new IllegalArgumentException("fileName must be an XHTML file: " + fileName);
    }

    private String resolveApprovalFileName(Scope scope, String fileName) {
        if (scope == Scope.FILE) return fileName;

        return "OEBPS/Text";
    }

    private String createApprovalMessage(Scope scope, String fileName) {
        if (scope == Scope.TEXT) return "OEBPS/Text 폴더의 XHTML 소스를 전체 정리하시겠습니까? (승인하시면 실제 파일에 반영됩니다.)";

        return fileName + " XHTML 소스를 정리하시겠습니까? (승인하시면 실제 파일에 반영됩니다.)";
    }

    private Path resolveTextDirectory(EpubProjectContext project) {
        Path projectRoot = project.getProjectRoot();

        if (projectRoot == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return projectRoot.resolve("OEBPS").resolve("Text").normalize();
    }

    private EpubProjectContext requireCurrentProject() {
        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;
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

    private String requireString(Map<String, Object> arguments, String name) {
        String value = optionalString(arguments, name);

        if (value == null) throw new IllegalArgumentException(name + " must not be blank.");

        return value;
    }

    private String optionalString(Map<String, Object> arguments, String name) {
        if (arguments == null || !arguments.containsKey(name)) return null;

        Object value = arguments.get(name);

        if (value == null) return null;

        String text = String.valueOf(value).trim();

        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }

    private Map<String, Object> enumProperty(List<String> values, String description) {
        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("enum", values);
        property.put("description", description);

        return property;
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }

    private enum Scope {

        TEXT,
        FILE;

        private static Scope from(String value) {
            try {
                return Scope.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (Exception exception) {
                throw new IllegalArgumentException("Unsupported XHTML cleanup scope: " + value);
            }
        }
    }
}