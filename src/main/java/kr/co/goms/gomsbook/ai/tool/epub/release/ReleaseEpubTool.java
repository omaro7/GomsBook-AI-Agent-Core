/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */

package kr.co.goms.gomsbook.ai.tool.epub.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.AgentToolResultDisplayConstant;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.ReleaseEpubApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.publish.EpubArtifactFingerprint;
import kr.co.goms.gomsbook.ai.epub.publish.EpubArtifactFingerprintService;
import kr.co.goms.gomsbook.ai.epub.release.EpubReleasePolicy;
import kr.co.goms.gomsbook.ai.epub.release.EpubReleaseVersion;
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

public final class ReleaseEpubTool implements AgentTool {

    public static final String TOOL_NAME = "release_epub";

    private static final String DESCRIPTION =
            "Prepares the latest published EPUB artifact of the current project for an official release. "
                    + "Use this tool when the user asks to release, finalize, confirm, or create an official release of an already published EPUB file. "
                    + "The tool automatically resolves the latest published EPUB file, calculates its artifact fingerprint, including SHA-256 and file size, and prepares the release metadata. "
                    + "The caller must provide the release version using semantic version format such as 1.0.0, 1.0.1, or 1.1.0. "
                    + "The release version must not already exist and must be greater than the latest release version. "
                    + "The release version is validated before an approval request is created. "
                    + "This tool does not create or modify EPUB content and does not publish a new EPUB file. "
                    + "Use publish_epub first when no published EPUB artifact exists. "
                    + "This tool only prepares the release operation and requires explicit user approval before the EPUB artifact is confirmed as an official release.";

    private static final String APPROVAL_TITLE = "EPUB Release";

    private final CurrentProjectProvider currentProjectProvider;

    private final PublishDirectoryProvider publishDirectoryProvider;

    private final LatestPublishedEpubResolver latestPublishedEpubResolver;

    private final EpubArtifactFingerprintService fingerprintService;

    private final EpubReleasePolicy releasePolicy;

    private final AgentApprovalService approvalService;

    private final Gson gson;

    public ReleaseEpubTool(
            CurrentProjectProvider currentProjectProvider,
            PublishDirectoryProvider publishDirectoryProvider,
            LatestPublishedEpubResolver latestPublishedEpubResolver,
            EpubArtifactFingerprintService fingerprintService,
            EpubReleasePolicy releasePolicy,
            AgentApprovalService approvalService,
            Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");

        if (publishDirectoryProvider == null) throw new IllegalArgumentException("publishDirectoryProvider must not be null.");

        if (latestPublishedEpubResolver == null) throw new IllegalArgumentException("latestPublishedEpubResolver must not be null.");

        if (fingerprintService == null) throw new IllegalArgumentException("fingerprintService must not be null.");

        if (releasePolicy == null) throw new IllegalArgumentException("releasePolicy must not be null.");

        if (approvalService == null) throw new IllegalArgumentException("approvalService must not be null.");

        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;

        this.publishDirectoryProvider = publishDirectoryProvider;

        this.latestPublishedEpubResolver = latestPublishedEpubResolver;

        this.fingerprintService = fingerprintService;

        this.releasePolicy = releasePolicy;

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

        properties.put("version", stringProperty("Official EPUB release version using semantic version format such as 1.0.0, 1.0.1, or 1.1.0."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");

        schema.put("properties", properties);

        schema.put("required", List.of("version"));

        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);

    }

    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        if (request == null) {

            return invalid(
                    "EPUB_RELEASE_REQUEST_MISSING",
                    "ToolRequest must not be null."
            );

        }

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) {

            return invalid(
                    "EPUB_RELEASE_PROJECT_MISSING",
                    "Current EPUB project is not available."
            );

        }

        if (project.getProjectRoot() == null) {

            return invalid(
                    "EPUB_RELEASE_PROJECT_ROOT_MISSING",
                    "Current EPUB project root is not available."
            );

        }

        String projectId = resolveProjectId(project);

        Path publishRoot = publishDirectoryProvider.getPublishDirectory();

        if (publishRoot == null) {

            return invalid(
                    "EPUB_RELEASE_PUBLISH_DIRECTORY_MISSING",
                    "Publish directory is not configured."
            );

        }
        
        Path publishDirectory = publishRoot
                .resolve(projectId)
                .toAbsolutePath()
                .normalize();

        System.out.println("[ReleaseEpubTool] publishDirectory=" + publishDirectory);
        System.out.println("[ReleaseEpubTool] exists=" + Files.exists(publishDirectory));
        System.out.println("[ReleaseEpubTool] directory=" + Files.isDirectory(publishDirectory));
        
        
        if (publishDirectory == null) {

            return invalid(
                    "EPUB_RELEASE_PUBLISH_DIRECTORY_MISSING",
                    "Publish directory is not configured."
            );

        }

        if (!Files.isDirectory(publishDirectory)) {

            return invalid(
                    "EPUB_RELEASE_PUBLISH_DIRECTORY_INVALID",
                    "Publish directory does not exist: " + publishDirectory
            );

        }

        String version = getString(request, "version");

        if (version == null) {

            return invalid(
                    "EPUB_RELEASE_VERSION_MISSING",
                    "version must not be blank."
            );

        }

        EpubReleaseVersion releaseVersion;

        try {

            releaseVersion = EpubReleaseVersion.parse(version);

        } catch (IllegalArgumentException exception) {

            return invalid(
                    "EPUB_RELEASE_VERSION_INVALID",
                    safeMessage(exception)
            );

        }

        try {

            releasePolicy.validate(
                    projectId,
                    releaseVersion
            );

        } catch (IllegalStateException exception) {

            return invalid(
                    "EPUB_RELEASE_VERSION_POLICY_VIOLATION",
                    safeMessage(exception)
            );

        } catch (IllegalArgumentException exception) {

            return invalid(
                    "EPUB_RELEASE_VERSION_POLICY_INVALID",
                    safeMessage(exception)
            );

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
                    .message("EPUB release request is invalid.")
                    .build();

        }

        try {

            EpubProjectContext project = requireCurrentProject();

            String projectId = resolveProjectId(project);

            String version = requireVersion(request);

            Path publishDirectory = requirePublishDirectory(project);

            Path epubPath = latestPublishedEpubResolver.resolve(publishDirectory).toAbsolutePath().normalize();

            validateEpubFile(epubPath);

            EpubArtifactFingerprint fingerprint = fingerprintService.calculate(epubPath);

            Instant publishedAt = resolvePublishedAt(epubPath);

            ReleaseEpubApprovalPayload payload = new ReleaseEpubApprovalPayload(
                    projectId,
                    version,
                    epubPath.toString(),
                    fingerprint.getSha256(),
                    fingerprint.getFileSize(),
                    publishedAt.toString()
            );

            String approvalContent = gson.toJson(payload);

            String runId = resolveRunId(request, context);

            String fileName = epubPath.getFileName().toString();

            String approvalMessage = "현재 최신 EPUB 파일을 Version " + version + " 공식 Release로 확정하시겠습니까?";

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    approvalMessage,
                    fileName,
                    approvalContent
            );

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);

            data.put("approvalId", approval.getApprovalId());

            data.put("action", approval.getAction());

            data.put("title", approval.getTitle());

            data.put("message", approval.getMessage());

            data.put("fileName", approval.getFileName());

            data.put("content", approval.getContent());

            data.put("preview", createPreview(version, epubPath, fingerprint, publishedAt));

            data.put("previewTitle", AgentToolResultDisplayConstant.PREVIEW_TITLE_CONTENT);

            data.put("displayInstruction", AgentToolResultDisplayConstant.PREVIEW_DISPLAY_INSTRUCTION);

            data.put("version", version);

            data.put("epubPath", epubPath.toString());

            data.put("sha256", fingerprint.getSha256());

            data.put("fileSize", fingerprint.getFileSize());

            data.put("publishedAt", publishedAt.toString());

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB release approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            return failure(
                    request,
                    "EPUB_RELEASE_PREPARE_FAILED",
                    "Failed to prepare EPUB release: " + safeMessage(exception),
                    exception
            );

        } catch (Exception exception) {

            return failure(
                    request,
                    "EPUB_RELEASE_PREPARE_FAILED",
                    "Failed to prepare EPUB release: " + safeMessage(exception),
                    exception
            );

        }

    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");

        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;

    }

    private Path requirePublishDirectory(EpubProjectContext project) {

        Path publishRoot = publishDirectoryProvider.getPublishDirectory();

        if (publishRoot == null) throw new IllegalStateException("Publish directory is not configured.");

        publishRoot = publishRoot.toAbsolutePath().normalize();

        if (!Files.isDirectory(publishRoot)) throw new IllegalStateException("Publish directory does not exist: " + publishRoot);

        String projectId = resolveProjectId(project);

        Path publishDirectory = publishRoot.resolve(projectId).normalize();

        if (!Files.isDirectory(publishDirectory)) throw new IllegalStateException("Project publish directory does not exist: " + publishDirectory);

        return publishDirectory;

    }
    
    private String requireVersion(ToolRequest request) {

        String version = getString(request, "version");

        if (version == null) throw new IllegalArgumentException("version must not be blank.");

        return EpubReleaseVersion.parse(version).toString();

    }

    private void validateEpubFile(Path epubPath) {

        if (epubPath == null) throw new IllegalStateException("Published EPUB file is not available.");

        if (!Files.isRegularFile(epubPath)) throw new IllegalStateException("Published EPUB file does not exist: " + epubPath);

        if (!epubPath.getFileName().toString().toLowerCase().endsWith(".epub")) throw new IllegalStateException("Published file is not an EPUB file: " + epubPath);

    }

    private Instant resolvePublishedAt(Path epubPath) {

        try {

            FileTime fileTime = Files.getLastModifiedTime(epubPath);

            return fileTime.toInstant();

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to resolve EPUB publishedAt: " + epubPath, exception);

        }

    }

    private String createPreview(
            String version,
            Path epubPath,
            EpubArtifactFingerprint fingerprint,
            Instant publishedAt) {

        return "Version: " + version
                + "\nEPUB: " + epubPath
                + "\nFile Size: " + fingerprint.getFileSize()
                + "\nSHA-256: " + fingerprint.getSha256()
                + "\nPublished At: " + publishedAt;

    }

    private String resolveRunId(
            ToolRequest request,
            ToolContext context) {

        if (context != null && context.getRequestId() != null && !context.getRequestId().isBlank()) return context.getRequestId().trim();

        if (request != null && request.getRequestId() != null && !request.getRequestId().isBlank()) return request.getRequestId().trim();

        throw new IllegalStateException("runId is not available from ToolContext or ToolRequest.");

    }

    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();

    }

    private String getString(
            ToolRequest request,
            String name) {

        String value = request.getArgument(name, String.class);

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;

    }

    private Map<String, Object> stringProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");

        property.put("description", description);

        return Collections.unmodifiableMap(property);

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

        String code = errorCode == null || errorCode.isBlank() ? "EPUB_RELEASE_PREPARE_FAILED" : errorCode.trim();

        String message = errorMessage == null || errorMessage.isBlank() ? "Failed to prepare EPUB release." : errorMessage.trim();

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

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";

        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage().trim();

    }

}