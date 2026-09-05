/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.validation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.epub.model.EpubStructureValidationIssue;
import kr.co.goms.gomsbook.ai.epub.model.EpubStructureValidationResult;
import kr.co.goms.gomsbook.ai.epub.service.EpubStructureValidator;
import kr.co.goms.gomsbook.ai.epub.service.LatestPublishedEpubResolver;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;


/**
 * 현재 EPUB 프로젝트의 최신 출판 EPUB 파일 구조를 검증합니다.
 */
public final class ValidateEpubStructureTool implements AgentTool {


    public static final String NAME = "validate_epub_structure";
    public static final String TOOL_NAME = NAME;
    public static final String DESCRIPTION = "Validates the structure of the latest published EPUB file for the current project and returns validation issues without treating an invalid EPUB as a tool execution failure.";


    private final CurrentProjectProvider projectProvider;

    private final PublishDirectoryProvider publishDirectoryProvider;

    private final LatestPublishedEpubResolver publishedEpubResolver;

    private final EpubStructureValidator structureValidator;


    public ValidateEpubStructureTool(
            CurrentProjectProvider projectProvider,
            PublishDirectoryProvider publishDirectoryProvider,
            LatestPublishedEpubResolver publishedEpubResolver,
            EpubStructureValidator structureValidator) {

        if (projectProvider == null) throw new IllegalArgumentException("projectProvider must not be null.");
        if (publishDirectoryProvider == null) throw new IllegalArgumentException("publishDirectoryProvider must not be null.");
        if (publishedEpubResolver == null) throw new IllegalArgumentException("publishedEpubResolver must not be null.");
        if (structureValidator == null) throw new IllegalArgumentException("structureValidator must not be null.");

        this.projectProvider = projectProvider;
        this.publishDirectoryProvider = publishDirectoryProvider;
        this.publishedEpubResolver = publishedEpubResolver;
        this.structureValidator = structureValidator;
    }


    @Override
    public String getName() { return TOOL_NAME; }


    @Override
    public String getDescription() { return DESCRIPTION; }


    @Override
    public ToolValidationResult validate(ToolRequest request, ToolContext context) {

        ToolValidationResult.Builder result = ToolValidationResult.builder();

        EpubProjectContext project = projectProvider.getCurrentProject();

        if (project == null) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_STRUCTURE_PROJECT_MISSING",
                            "Current EPUB project is not available."))
                    .build();
        }

        Path publishDirectory = publishDirectoryProvider.getPublishDirectory();

        if (publishDirectory == null) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_STRUCTURE_PUBLISH_DIRECTORY_MISSING",
                            "Publish directory is not configured."))
                    .build();
        }

        return result.valid(true).build();
    }


    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        ToolValidationResult validation = validate(request, context);

        if (!validation.isValid()) {

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.VALIDATION_FAILED)
                    .validationResult(validation)
                    .message("EPUB structure validation request is invalid.")
                    .build();
        }

        try {

            Path publishDirectory = publishDirectoryProvider.getPublishDirectory();
            Path epubFile = publishedEpubResolver.resolve(publishDirectory);

            EpubStructureValidationResult result = structureValidator.validate(epubFile);

            return convertResult(epubFile, result);

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_STRUCTURE_VALIDATION_FAILED",
                    "Failed to execute EPUB structure validation: " + safeMessage(exception),
                    exception);
        }
    }


    private ToolResult convertResult(Path epubFile, EpubStructureValidationResult result) {

        ToolResult.Builder builder = ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.SUCCESS)
                .message(result.createSummary())
                .data("epubFile", normalizePath(epubFile))
                .data("valid", result.isValid())
                .data("manifestItemCount", result.getManifestItemCount())
                .data("spineItemCount", result.getSpineItemCount())
                .data("issueCount", result.getIssueCount())
                .data("errorCount", result.getErrorCount())
                .data("warningCount", result.getWarningCount())
                .data("issues", result.getIssues());

        if (result.getPackagePath() != null) builder.data("packagePath", result.getPackagePath());

        result.getNavId().ifPresent(value -> builder.data("navId", value));
        result.getNavHref().ifPresent(value -> builder.data("navHref", value));

        appendIssues(result, builder);

        return builder.build();
    }


    private void appendIssues(EpubStructureValidationResult result, ToolResult.Builder builder) {

        for (EpubStructureValidationIssue issue : result.getIssues()) builder.issue(toToolIssue(issue));
    }


    private ToolIssue toToolIssue(EpubStructureValidationIssue issue) {

        ToolIssueSeverity severity = issue.isError() ? ToolIssueSeverity.ERROR : ToolIssueSeverity.WARNING;

        return ToolIssue.builder()
                .severity(severity)
                .code(issue.getCode())
                .message(issue.getMessage())
                .build();
    }


    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("required", List.of());
        schema.put("additionalProperties", false);

        return Map.copyOf(schema);
    }


    private ToolResult failure(String errorCode, String errorMessage, Throwable cause) {

        String code = errorCode == null || errorCode.isBlank() ? "EPUB_STRUCTURE_VALIDATION_FAILED" : errorCode.trim();
        String message = errorMessage == null || errorMessage.isBlank() ? "EPUB structure validation failed." : errorMessage.trim();

        ToolResult.Builder builder = ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.FAILED)
                .message(message)
                .errorCode(code)
                .errorMessage(message)
                .issue(errorIssue(code, message));

        if (cause != null) {

            builder.cause(cause);
            builder.data("exceptionType", cause.getClass().getName());
        }

        return builder.build();
    }


    private ToolIssue errorIssue(String code, String message) {

        return ToolIssue.builder()
                .severity(ToolIssueSeverity.ERROR)
                .code(code)
                .message(message)
                .build();
    }


    private String normalizePath(Path path) {

        if (path == null) return "";

        return path.toAbsolutePath().normalize().toString();
    }


    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown EPUB structure validation error.";

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();

        return message.trim();
    }
}