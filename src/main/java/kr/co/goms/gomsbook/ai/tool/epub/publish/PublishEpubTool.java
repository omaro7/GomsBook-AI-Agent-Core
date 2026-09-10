/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.publish;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.publish.DefaultEpubPublisher;
import kr.co.goms.gomsbook.ai.epub.publish.EpubPublisher;
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
import kr.co.goms.gomsbook.ai.epub.publish.EpubArtifactFingerprint;
import kr.co.goms.gomsbook.ai.epub.publish.EpubArtifactFingerprintService;
import kr.co.goms.gomsbook.ai.epub.publish.PublishEpubResult;

public final class PublishEpubTool implements AgentTool {

    public static final String TOOL_NAME = "publish_epub";

    private static final String DESCRIPTION = "현재 EPUB 프로젝트를 패키징하여 Publish 디렉터리에 EPUB 파일을 생성합니다.";

    private final CurrentProjectProvider currentProjectProvider;
    private final PublishDirectoryProvider publishDirectoryProvider;
    private final EpubArtifactFingerprintService fingerprintService;
    
    public PublishEpubTool(
            CurrentProjectProvider currentProjectProvider,
            PublishDirectoryProvider publishDirectoryProvider,
            EpubArtifactFingerprintService fingerprintService) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
        this.publishDirectoryProvider = Objects.requireNonNull(publishDirectoryProvider, "publishDirectoryProvider must not be null");
        this.fingerprintService = Objects.requireNonNull(fingerprintService, "fingerprintService must not be null");
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

        return Map.copyOf(schema);
    }

    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) {

            return invalid(
                    "EPUB_PUBLISH_PROJECT_MISSING",
                    "Current EPUB project is not available.");
        }

        Path projectRoot = project.getProjectRoot();

        if (projectRoot == null) {

            return invalid(
                    "EPUB_PUBLISH_PROJECT_ROOT_MISSING",
                    "Current EPUB project root is not available.");
        }

        if (!Files.isDirectory(projectRoot)) {

            return invalid(
                    "EPUB_PUBLISH_PROJECT_ROOT_INVALID",
                    "Current EPUB project root does not exist: " + projectRoot);
        }

        Path publishDirectory = publishDirectoryProvider.getPublishDirectory();

        if (publishDirectory == null) {

            return invalid(
                    "EPUB_PUBLISH_DIRECTORY_MISSING",
                    "Publish directory is not configured.");
        }

        return ToolValidationResult.valid();
    }

    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        ToolValidationResult validation = validate(request, context);

        if (!validation.isValid()) {

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.VALIDATION_FAILED)
                    .validationResult(validation)
                    .message("EPUB publish request is invalid.")
                    .build();
        }

        try {

            EpubProjectContext project = requireCurrentProject();
            Path projectRoot = project.getProjectRoot().toAbsolutePath().normalize();
            Path publishDirectory = requirePublishDirectory();

            EpubPublisher epubPublisher = new DefaultEpubPublisher(projectRoot, publishDirectory, fingerprintService);
            PublishEpubResult publishResult = epubPublisher.publish();
            Path epubFile = publishResult.getEpubPath();
            EpubArtifactFingerprint fingerprint = publishResult.getFingerprint();

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB 파일이 생성되었습니다.")
                    .data("projectName", project.getProjectName())
                    .data("projectRoot", normalizePath(projectRoot))
                    .data("publishDirectory", normalizePath(publishDirectory))
                    .data("fileName", epubFile.getFileName().toString())
                    .data("epubFile", normalizePath(epubFile))
                    .data("fileSize", fingerprint.getFileSize())
                    .data("sha256", fingerprint.getSha256())
                    .data("publishedAt", publishResult.getPublishedAt().toString())
                    .build();

        } catch (Exception exception) {

            return failure(
                    request,
                    "EPUB_PUBLISH_FAILED",
                    "Failed to publish EPUB: " + safeMessage(exception),
                    exception);
        }
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;
    }

    private Path requirePublishDirectory() {

        Path publishDirectory = publishDirectoryProvider.getPublishDirectory();

        if (publishDirectory == null) throw new IllegalStateException("Publish directory is not configured.");

        return publishDirectory.toAbsolutePath().normalize();
    }

    private ToolValidationResult invalid(
            String code,
            String message) {

        return ToolValidationResult.builder()
                .valid(false)
                .issue(errorIssue(code, message))
                .build();
    }

    private ToolResult failure(
            ToolRequest request,
            String errorCode,
            String errorMessage,
            Throwable cause) {

        String code = errorCode == null || errorCode.isBlank() ? "EPUB_PUBLISH_FAILED" : errorCode.trim();
        String message = errorMessage == null || errorMessage.isBlank() ? "EPUB publish failed." : errorMessage.trim();

        ToolResult.Builder builder = ToolResult.builder()
                .toolName(TOOL_NAME)
                .requestId(request != null ? request.getRequestId() : null)
                .toolCallId(request != null ? request.getToolCallId() : null)
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

    private ToolIssue errorIssue(
            String code,
            String message) {

        return ToolIssue.builder()
                .severity(ToolIssueSeverity.ERROR)
                .code(code)
                .message(message)
                .build();
    }

    private String normalizePath(
            Path path) {

        if (path == null) return "";

        return path.toAbsolutePath().normalize().toString();
    }

    private String safeMessage(
            Throwable throwable) {

        if (throwable == null) return "Unknown EPUB publish error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage().trim();
    }
}