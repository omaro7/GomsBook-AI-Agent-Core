/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.proofreading;

import java.nio.charset.StandardCharsets;
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
import kr.co.goms.gomsbook.ai.agent.approval.payload.FixEpubKoreanTypoApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class FixEpubKoreanTypoTool implements AgentTool {

    public static final String TOOL_NAME = "fix_epub_korean_typo";

    private static final String APPROVAL_TITLE = "한글 오타 수정";

    private static final String DESCRIPTION =
            "Prepares a correction for a Korean typo previously identified in an EPUB XHTML file. "
                    + "Use this tool only when the user explicitly asks to fix, correct, replace, or apply a Korean typo correction. "
                    + "The tool requires the XHTML file name, the exact original text, and the replacement text. "
                    + "It validates that the original text exists in the target EPUB XHTML file and prepares a preview before modification. "
                    + "This tool never modifies the EPUB file immediately. "
                    + "Explicit user approval is required before the correction is written to the XHTML file.";

    private final CurrentProjectProvider currentProjectProvider;

    private final AgentApprovalService approvalService;

    private final Gson gson;

    public FixEpubKoreanTypoTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {

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

        properties.put("fileName", stringProperty("수정할 EPUB XHTML 파일명입니다. 예: chapter01_01.xhtml"));
        properties.put("original", stringProperty("현재 XHTML에 존재하는 원본 오타 문자열입니다."));
        properties.put("suggestion", stringProperty("원본 문자열을 대체할 교정 문자열입니다."));
        properties.put("startOffset", integerProperty("검사 결과에서 반환된 원본 문자열의 시작 offset입니다. 동일 문자열이 여러 번 존재할 때 정확한 위치를 식별하는 데 사용합니다."));
        properties.put("endOffset", integerProperty("검사 결과에서 반환된 원본 문자열의 종료 offset입니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("fileName", "original", "suggestion", "startOffset", "endOffset"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            String fileName = requireString(request, "fileName");
            String original = requireString(request, "original");
            String suggestion = requireString(request, "suggestion");
            int startOffset = requireInteger(request, "startOffset");
            int endOffset = requireInteger(request, "endOffset");

            validateFileName(fileName);
            validateOffsets(startOffset, endOffset);

            Path targetFile = resolveTargetFile(project, fileName);

            if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB XHTML file was not found: " + targetFile);

            String content = readContent(targetFile);

            validateOriginal(content, original, startOffset, endOffset);

            String preview = createPreview(content, original, suggestion, startOffset, endOffset);

            FixEpubKoreanTypoApprovalPayload payload = new FixEpubKoreanTypoApprovalPayload(
                    fileName,
                    original,
                    suggestion,
                    startOffset,
                    endOffset);

            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String approvalContent = gson.toJson(payload);

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    "수정하시겠습니까? (승인하시면 실제 파일에 반영됩니다.)",
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
            data.put("preview", preview);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB Korean typo correction approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB Korean typo correction: " + safeMessage(exception);

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
        if (!Files.isDirectory(project.getTextDirectory())) throw new IllegalStateException("Current EPUB Text directory does not exist: " + project.getTextDirectory());

        return project;
    }

    private Path resolveTargetFile(EpubProjectContext project, String fileName) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(fileName).toAbsolutePath().normalize();

        if (!targetFile.startsWith(textDirectory)) throw new IllegalArgumentException("Invalid EPUB XHTML file path: " + fileName);

        return targetFile;
    }

    private String readContent(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read EPUB XHTML file: " + file, exception);
        }
    }

    private void validateOriginal(String content, String original, int startOffset, int endOffset) {

        if (startOffset < 0 || endOffset > content.length()) throw new IllegalArgumentException("Typo offsets are outside the XHTML content range.");

        String current = content.substring(startOffset, endOffset);

        if (!current.equals(original)) {
            throw new IllegalStateException(
                    "The XHTML content has changed since the typo was checked. "
                            + "Expected text at offset "
                            + startOffset
                            + "-"
                            + endOffset
                            + ": "
                            + original);
        }
    }

    private String createPreview(String content, String original, String suggestion, int startOffset, int endOffset) {

        int contextStart = Math.max(0, startOffset - 80);
        int contextEnd = Math.min(content.length(), endOffset + 80);

        String before = content.substring(contextStart, startOffset);
        String after = content.substring(endOffset, contextEnd);

        return before + "[" + original + " → " + suggestion + "]" + after;
    }

    private void validateFileName(String fileName) {

        if (fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("fileName must contain only the XHTML file name.");

        String normalized = fileName.toLowerCase(Locale.ROOT);

        if (!normalized.endsWith(".xhtml") && !normalized.endsWith(".html")) throw new IllegalArgumentException("fileName must use the .xhtml or .html extension.");
    }

    private void validateOffsets(int startOffset, int endOffset) {

        if (startOffset < 0) throw new IllegalArgumentException("startOffset must be greater than or equal to 0.");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset must be greater than startOffset.");
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

    private String requireString(ToolRequest request, String name) {

        String value = request.getArgument(name, String.class);

        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");

        return value.trim();
    }

    private int requireInteger(ToolRequest request, String name) {

        Integer value = request.getArgument(name, Integer.class);

        if (value == null) throw new IllegalArgumentException(name + " must not be null.");

        return value.intValue();
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
        property.put("minimum", 0);
        property.put("description", description);

        return property;
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}