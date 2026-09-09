/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.validation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubProjectValidationIssue;
import kr.co.goms.gomsbook.ai.epub.model.EpubProjectValidationResult;
import kr.co.goms.gomsbook.ai.epub.validation.EpubProjectValidator;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.util.ToolUtil;

/**
 * EPUB 파일 생성 전 현재 EPUB 프로젝트의 구조와 정합성을 검증합니다.
 */
public final class ValidateEpubProjectTool implements AgentTool {

    public static final String TOOL_NAME = "validate_epub_project";
    public static final String DESCRIPTION =
            "Validates the structural integrity and consistency of the current EPUB project before the .epub file is created, including container.xml, content.opf, manifest, spine, "
            + "navigation document registration, file existence, and resource references. "
            + "Do not use this tool for accessibility validation.";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubProjectValidator projectValidator;

    public ValidateEpubProjectTool(CurrentProjectProvider currentProjectProvider, EpubProjectValidator projectValidator) {
        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null.");
        this.projectValidator = Objects.requireNonNull(projectValidator, "projectValidator must not be null.");
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
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("required", List.of());
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        String requestId = request == null ? null : request.getRequestId();

        try {

            EpubProjectContext project = currentProjectProvider.getCurrentProject();

            if (project == null) return ToolUtil.validationFailed(requestId, TOOL_NAME, "Current EPUB project is not available.");

            Path projectRoot = project.getProjectRoot();

            if (projectRoot == null) return ToolUtil.validationFailed(requestId, TOOL_NAME, "Current EPUB project root is not available.");

            EpubProjectValidationResult validationResult = projectValidator.validate(projectRoot);

            return success(requestId, validationResult);

        } catch (IllegalArgumentException exception) {

            return ToolUtil.validationFailed(requestId, TOOL_NAME, ToolUtil.safeMessage(exception));

        } catch (Exception exception) {

            return ToolUtil.failed(requestId, TOOL_NAME, "EPUB_PROJECT_VALIDATION_FAILED", "Failed to validate current EPUB project: " + ToolUtil.safeMessage(exception), exception);
        }
    }

    private ToolResult success(String requestId, EpubProjectValidationResult validationResult) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("valid", validationResult.isValid());
        data.put("projectRoot", validationResult.getProjectRoot() == null ? null : validationResult.getProjectRoot().toString());
        data.put("packagePath", validationResult.getPackagePath() == null ? null : validationResult.getPackagePath().toString());
        data.put("errorCount", validationResult.getErrorCount());
        data.put("warningCount", validationResult.getWarningCount());
        data.put("issueCount", validationResult.getIssueCount());
        data.put("summary", validationResult.createSummary());
        data.put("issues", validationResult.getIssues().stream().map(this::toIssueData).toList());

        return ToolResult.builder()
                .requestId(requestId)
                .toolName(TOOL_NAME)
                .status(ToolStatus.SUCCESS)
                .message(validationResult.createSummary())
                .data(data)
                .build();
    }

    private Map<String, Object> toIssueData(EpubProjectValidationIssue issue) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("severity", issue.getSeverity().name());
        data.put("code", issue.getCode());
        data.put("message", issue.getMessage());
        return data;
    }

}