/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubCheckResult;
import kr.co.goms.gomsbook.ai.epub.service.EpubCheckRunner;
import kr.co.goms.gomsbook.ai.epub.service.LatestPublishedEpubResolver;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.epub.validation.fix.EpubFileCheckFixAction;
import kr.co.goms.gomsbook.ai.epub.validation.fix.EpubFileCheckFixService;
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
 * EPUBCheck 오류를 분석하고 현재 EPUB 프로젝트 상태를 기준으로 수정계획을 생성하는 Agent Tool입니다.
 *
 * <p>실제 EPUB 프로젝트 파일은 수정하지 않습니다.</p>
 */
public final class FixEpubFileCheckTool implements AgentTool {

    public static final String TOOL_NAME = "fix_epub_file_check";

    public static final String DESCRIPTION = "Runs EPUBCheck for the published EPUB file and creates concrete fix actions for detected EPUBCheck issues. "
            + "The tool analyzes EPUBCheck messages, creates fix plans, inspects the current EPUB project, and returns recommended update actions. "
            + "This tool does not modify EPUB project files directly.";

    private static final String EPUB_FILE_ARGUMENT = "epubFile";

    private final CurrentProjectProvider currentProjectProvider;
    private final PublishDirectoryProvider publishDirectoryProvider;
    private final LatestPublishedEpubResolver publishedEpubResolver;
    private final EpubCheckRunner epubCheckRunner;
    private final EpubFileCheckFixService fixService;

    public FixEpubFileCheckTool(
            CurrentProjectProvider currentProjectProvider,
            PublishDirectoryProvider publishDirectoryProvider,
            EpubCheckRunner epubCheckRunner,
            EpubFileCheckFixService fixService) {

        this(currentProjectProvider, publishDirectoryProvider, new LatestPublishedEpubResolver(), epubCheckRunner, fixService);
    }

    public FixEpubFileCheckTool(
            CurrentProjectProvider currentProjectProvider,
            PublishDirectoryProvider publishDirectoryProvider,
            LatestPublishedEpubResolver publishedEpubResolver,
            EpubCheckRunner epubCheckRunner,
            EpubFileCheckFixService fixService) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null.");
        this.publishDirectoryProvider = Objects.requireNonNull(publishDirectoryProvider, "publishDirectoryProvider must not be null.");
        this.publishedEpubResolver = Objects.requireNonNull(publishedEpubResolver, "publishedEpubResolver must not be null.");
        this.epubCheckRunner = Objects.requireNonNull(epubCheckRunner, "epubCheckRunner must not be null.");
        this.fixService = Objects.requireNonNull(fixService, "fixService must not be null.");
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
    public ToolValidationResult validate(ToolRequest request, ToolContext context) {

        ToolValidationResult.Builder result = ToolValidationResult.builder();

        if (request == null) return result.valid(false).issue(errorIssue("EPUB_FILE_CHECK_FIX_REQUEST_NULL", "Tool request must not be null.")).build();

        try {

            EpubProjectContext project = requireCurrentProject();
            Path projectRoot = requireProjectRoot(project);
            Path epubFile = resolveEpubFile(request, project);

            validateProjectRoot(projectRoot);
            validateEpubFile(epubFile);

            return result.valid(true).build();

        } catch (RuntimeException exception) {

            return result.valid(false).issue(errorIssue("EPUB_FILE_CHECK_FIX_ARGUMENT_INVALID", safeMessage(exception))).build();
        }
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        ToolValidationResult validation = validate(request, context);

        if (!validation.isValid()) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.VALIDATION_FAILED)
                    .validationResult(validation)
                    .message("EPUBCheck fix planning request is invalid.")
                    .errorCode("EPUB_FILE_CHECK_FIX_VALIDATION_FAILED")
                    .errorMessage("EPUBCheck fix planning request is invalid.")
                    .build();
        }

        EpubProjectContext project = requireCurrentProject();
        Path projectRoot = requireProjectRoot(project);
        Path epubFile = resolveEpubFile(request, project);

        try {

            EpubCheckResult checkResult = epubCheckRunner.run(epubFile);
            List<EpubFileCheckFixAction> actions = fixService.createFixActions(projectRoot, checkResult);

            return success(project, epubFile, checkResult, actions);

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_FILE_CHECK_FIX_FAILED",
                    "Failed to create EPUBCheck fix actions: " + safeMessage(exception),
                    epubFile,
                    exception);
        }
    }

    private ToolResult success(
            EpubProjectContext project,
            Path epubFile,
            EpubCheckResult checkResult,
            List<EpubFileCheckFixAction> actions) {

        List<Map<String, Object>> actionData = actions.stream().filter(Objects::nonNull).map(this::toActionData).toList();

        int inspectRequiredCount = countFixType(actions, "INSPECT_REQUIRED");
        int manualRequiredCount = countFixType(actions, "MANUAL_REQUIRED");
        int executableActionCount = countExecutableActions(actions);

        String message = createResultMessage(checkResult, actions, executableActionCount, manualRequiredCount);

        return ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.SUCCESS)
                .message(message)
                .data("projectId", resolveProjectId(project))
                .data("projectRoot", project.getProjectRoot().toAbsolutePath().normalize().toString())
                .data("epubFile", epubFile.toAbsolutePath().normalize().toString())
                .data("epubCheckVersion", checkResult.getEpubCheckVersion())
                .data("epubCheckMessageCount", checkResult.getMessages().size())
                .data("fixActionCount", actions.size())
                .data("executableActionCount", executableActionCount)
                .data("inspectRequiredCount", inspectRequiredCount)
                .data("manualRequiredCount", manualRequiredCount)
                .data("actions", actionData)
                .build();
    }

    private Map<String, Object> toActionData(EpubFileCheckFixAction action) {

        Map<String, Object> data = new LinkedHashMap<>();

        if (action.getIssueId() != null) data.put("issueId", action.getIssueId());
        if (action.getCategory() != null) data.put("category", action.getCategory().name());
        if (action.getFixType() != null) data.put("fixType", action.getFixType().name());
        if (action.getToolName() != null) data.put("toolName", action.getToolName());
        if (action.getOperation() != null) data.put("operation", action.getOperation());
        if (action.getReason() != null) data.put("reason", action.getReason());

        data.put("arguments", action.getArguments() == null ? Map.of() : action.getArguments());

        return Map.copyOf(data);
    }

    private String createResultMessage(EpubCheckResult checkResult, List<EpubFileCheckFixAction> actions, int executableActionCount, int manualRequiredCount) {

        if (checkResult.getMessages().isEmpty()) return "EPUBCheck 오류가 없습니다.";
        if (actions.isEmpty()) return "EPUBCheck 오류가 발견되었지만 생성된 수정계획이 없습니다.";

        return "EPUBCheck 오류 "
                + checkResult.getMessages().size()
                + "건을 분석하여 수정계획 "
                + actions.size()
                + "건을 생성했습니다. 실행 가능한 수정 "
                + executableActionCount
                + "건, 추가 확인 필요 "
                + manualRequiredCount
                + "건입니다.";
    }

    private int countExecutableActions(List<EpubFileCheckFixAction> actions) {

        if (actions == null || actions.isEmpty()) return 0;

        int count = 0;

        for (EpubFileCheckFixAction action : actions) {

            if (action == null) continue;
            if (action.getToolName() == null || action.getToolName().isBlank()) continue;

            count++;
        }

        return count;
    }

    private int countFixType(List<EpubFileCheckFixAction> actions, String fixType) {

        if (actions == null || actions.isEmpty() || fixType == null) return 0;

        int count = 0;

        for (EpubFileCheckFixAction action : actions) {

            if (action == null || action.getFixType() == null) continue;
            if (fixType.equals(action.getFixType().name())) count++;
        }

        return count;
    }

    private Path resolveEpubFile(ToolRequest request, EpubProjectContext project) {

        String requestedFile = getString(request, EPUB_FILE_ARGUMENT);

        if (requestedFile != null) return Path.of(requestedFile).toAbsolutePath().normalize();

        Path publishDirectory = publishDirectoryProvider.getPublishDirectory();

        if (publishDirectory == null) throw new IllegalStateException("EPUB publish directory is not configured.");

        String projectId = resolveProjectId(project);
        Path projectPublishDirectory = publishDirectory.resolve(projectId).toAbsolutePath().normalize();
        Path epubFile = publishedEpubResolver.resolve(projectPublishDirectory);

        if (epubFile == null) throw new IllegalStateException("Published EPUB file could not be resolved: " + projectPublishDirectory);

        return epubFile.toAbsolutePath().normalize();
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;
    }

    private Path requireProjectRoot(EpubProjectContext project) {

        if (project == null || project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project.getProjectRoot().toAbsolutePath().normalize();
    }

    private void validateProjectRoot(Path projectRoot) {

        if (!Files.exists(projectRoot)) throw new IllegalStateException("EPUB project root does not exist: " + projectRoot);
        if (!Files.isDirectory(projectRoot)) throw new IllegalStateException("EPUB project root is not a directory: " + projectRoot);
    }

    private void validateEpubFile(Path epubFile) {

        if (epubFile == null) throw new IllegalStateException("EPUB file is not available.");

        Path normalized = epubFile.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) throw new IllegalStateException("EPUB file does not exist: " + normalized);
        if (!Files.isRegularFile(normalized)) throw new IllegalStateException("EPUB path is not a regular file: " + normalized);
        if (!Files.isReadable(normalized)) throw new IllegalStateException("EPUB file is not readable: " + normalized);

        String fileName = normalized.getFileName() == null ? "" : normalized.getFileName().toString().toLowerCase(java.util.Locale.ROOT);

        if (!fileName.endsWith(".epub")) throw new IllegalArgumentException("EPUB file must use the .epub extension.");
    }

    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private String getString(ToolRequest request, String name) {

        if (request == null || name == null) return null;

        String value = request.getArgument(name, String.class);

        return trimToNull(value);
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put(
                EPUB_FILE_ARGUMENT,
                Map.of(
                        "type", "string",
                        "description", "Optional EPUB file path. If omitted, the latest published EPUB file of the current project is used."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.of());

        return Map.copyOf(schema);
    }

    private ToolResult failure(String errorCode, String errorMessage, Path epubFile, Throwable cause) {

        String code = trimToNull(errorCode) == null ? "EPUB_FILE_CHECK_FIX_FAILED" : errorCode.trim();
        String message = trimToNull(errorMessage) == null ? "EPUBCheck fix planning failed." : errorMessage.trim();

        ToolResult.Builder builder = ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.FAILED)
                .message(message)
                .errorCode(code)
                .errorMessage(message)
                .issue(errorIssue(code, message));

        if (epubFile != null) builder.data("epubFile", epubFile.toAbsolutePath().normalize().toString());

        if (cause != null) {
            builder.cause(cause);
            builder.data("exceptionType", cause.getClass().getName());
            builder.data("exceptionMessage", safeMessage(cause));
        }

        return builder.build();
    }

    private ToolIssue errorIssue(String code, String message) {
        return ToolIssue.builder().severity(ToolIssueSeverity.ERROR).code(code).message(message).build();
    }

    private static String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown EPUBCheck fix planning error.";

        String message = throwable.getMessage();

        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message.trim();
    }
}