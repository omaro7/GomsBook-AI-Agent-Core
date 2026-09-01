/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationRequest;
import kr.co.goms.gomsbook.ai.epub.service.LatestPublishedEpubResolver;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.epub.validation.CompositeEpubValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubAccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationIssue;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationResult;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidator;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 생성된 EPUB 파일을 검증하는 Agent Tool입니다.
 * Epubcheck5.3.0
 * <p>EpubRuntime 전체에 의존하지 않고 검증기만 직접 주입받습니다.</p>
 */
public final class ValidateEpubTool implements AgentTool {

    public static final String NAME = "validate_epub";
    public static final String TOOL_NAME = NAME;
    public static final String DESCRIPTION = "Validates an EPUB file using internal validation, accessibility validation, EPUBCheck, or all configured validators.";

    private static final String PROJECT_ROOT_ARGUMENT = "projectRoot";
    private static final String EPUB_GENERATION_REQUEST_ATTRIBUTE = "epubGenerationRequest";
    private static final String EPUB_FILE_ARGUMENT = "epubFile";
    private static final String VALIDATION_MODE_ARGUMENT = "validationMode";
    private static final String GENERATION_OPTIONS_ATTRIBUTE = "epubGenerationOptions";

    private final EpubValidator internalValidator;
    private final EpubAccessibilityValidator accessibilityValidator;
    private final EpubCheckValidator epubCheckValidator;
    private final CompositeEpubValidator compositeValidator;
    private final PublishDirectoryProvider publishDirectoryProvider;
    private final LatestPublishedEpubResolver publishedEpubResolver;
    

    public ValidateEpubTool(EpubValidator internalValidator, EpubAccessibilityValidator accessibilityValidator, EpubCheckValidator epubCheckValidator, CompositeEpubValidator compositeValidator, PublishDirectoryProvider publishDirectoryProvider) {
        this(internalValidator, accessibilityValidator, epubCheckValidator, compositeValidator, publishDirectoryProvider, new LatestPublishedEpubResolver());
    }

    public ValidateEpubTool(EpubValidator internalValidator, EpubAccessibilityValidator accessibilityValidator, EpubCheckValidator epubCheckValidator, CompositeEpubValidator compositeValidator, PublishDirectoryProvider publishDirectoryProvider, LatestPublishedEpubResolver publishedEpubResolver) {
        this.internalValidator = internalValidator;
        this.accessibilityValidator = accessibilityValidator;
        this.epubCheckValidator = epubCheckValidator;
        this.compositeValidator = compositeValidator;
        this.publishDirectoryProvider = Objects.requireNonNull(publishDirectoryProvider, "publishDirectoryProvider must not be null");
        this.publishedEpubResolver = Objects.requireNonNull(publishedEpubResolver, "publishedEpubResolver must not be null");
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

        if (request == null) return result.valid(false).issue(errorIssue("EPUB_VALIDATION_REQUEST_NULL", "Tool request must not be null.")).build();

        Path epubFile;

        try {
            epubFile = resolveEpubFile(request, context);
        } catch (RuntimeException exception) {
            return result.valid(false).issue(errorIssue("EPUB_VALIDATION_ARGUMENT_INVALID", safeMessage(exception))).build();
        }

        if (epubFile == null) return result.valid(false).issue(errorIssue("EPUB_VALIDATION_FILE_MISSING", "EPUB file was not provided.")).build();

        Path normalized = epubFile.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) return result.valid(false).issue(errorIssue("EPUB_VALIDATION_FILE_NOT_FOUND", "EPUB file does not exist: " + normalized)).build();
        if (!Files.isRegularFile(normalized)) return result.valid(false).issue(errorIssue("EPUB_VALIDATION_NOT_FILE", "EPUB path is not a regular file: " + normalized)).build();
        if (!Files.isReadable(normalized)) return result.valid(false).issue(errorIssue("EPUB_VALIDATION_NOT_READABLE", "EPUB file is not readable: " + normalized)).build();

        String fileName = normalized.getFileName() == null ? "" : normalized.getFileName().toString().toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".epub")) return result.valid(false).issue(errorIssue("EPUB_VALIDATION_EXTENSION_INVALID", "Validation target must use the .epub extension.")).build();

        ValidationMode mode;

        try {
            mode = resolveValidationMode(request, context);
        } catch (RuntimeException exception) {
            return result.valid(false).issue(errorIssue("EPUB_VALIDATION_MODE_INVALID", safeMessage(exception))).build();
        }

        if (!supportsMode(mode)) return result.valid(false).issue(errorIssue("EPUB_VALIDATION_MODE_UNAVAILABLE", "Validation mode is not available: " + mode)).build();

        return result.valid(true).build();
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        ToolValidationResult validation = validate(request, context);

        if (!validation.isValid()) return ToolResult.builder().toolName(TOOL_NAME).status(ToolStatus.VALIDATION_FAILED).validationResult(validation).message("EPUB validation request is invalid.").build();

        Path projectRoot = resolveProjectRoot(request, context);
        Path epubFile = Objects.requireNonNull(resolveEpubFile(request, context), "EPUB file must not be null.").toAbsolutePath().normalize();
        ValidationMode mode = resolveValidationMode(request, context);
        EpubGenerationOptions options = resolveOptions(context);

        try {
            EpubValidationResult validationResult = executeValidation(projectRoot, epubFile, options, mode);
            return convertResult(epubFile, mode, validationResult);
        } catch (RuntimeException exception) {
            return failure("EPUB_VALIDATION_UNEXPECTED_ERROR", "Unexpected EPUB validation error: " + safeMessage(exception), epubFile, mode, exception);
        }
    }

    private EpubValidationResult executeValidation(Path projectRoot, Path epubFile, EpubGenerationOptions options, ValidationMode mode) {

        return switch (mode) {
            case INTERNAL -> requireInternalValidator().validate(projectRoot, epubFile, options);
            case ACCESSIBILITY -> requireAccessibilityValidator().validate(projectRoot, epubFile, options);
            case EPUB_CHECK -> requireEpubCheckValidator().validate(projectRoot, epubFile, options);
            case ALL -> requireCompositeValidator().validate(projectRoot, epubFile, options);
        };
    }

    private ToolResult convertResult(Path epubFile, ValidationMode mode, EpubValidationResult validationResult) {

        Objects.requireNonNull(validationResult, "EPUB validation result must not be null.");

        ToolStatus status = resolveToolStatus(validationResult);
        String message = validationResult.getMessage().orElseGet(validationResult::getSummary);

        ToolResult.Builder builder = ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(status)
                .message(message)
                .validationResult(null)
                .data("validationResult", validationResult)
                .data("epubFile", epubFile.toString())
                .data("validationMode", mode.name())
                .data("validationStatus", validationResult.getStatus().name())
                .data("issueCount", validationResult.getIssueCount())
                .data("fatalCount", validationResult.getFatalCount())
                .data("errorCount", validationResult.getErrorCount())
                .data("warningCount", validationResult.getWarningCount())
                .data("infoCount", validationResult.getInfoCount())
                .data("autoFixableIssueCount", validationResult.getAutoFixableIssueCount())
                .data("durationMillis", validationResult.getDurationMillis());

        validationResult.getValidatorName().ifPresent(value -> builder.data("validator", value));
        validationResult.getValidatorVersion().ifPresent(value -> builder.data("validatorVersion", value));

        for (EpubValidationIssue issue : validationResult.getIssues()) if (issue != null) builder.issue(convertIssue(issue));

        validationResult.getCause().ifPresent(cause -> {
            builder.data("exceptionType", cause.getClass().getName());
            builder.data("exceptionMessage", safeMessage(cause));
            if (status != ToolStatus.SUCCESS) builder.cause(cause);
        });

        if (status == ToolStatus.VALIDATION_FAILED) builder.errorMessage(message == null || message.isBlank() ? "EPUB validation failed." : message);

        return builder.build();
    }

    private ToolIssue convertIssue(EpubValidationIssue issue) {

        Objects.requireNonNull(issue, "EPUB validation issue must not be null.");

        StringBuilder message = new StringBuilder(issue.getDisplayMessage());
        issue.getSuggestion().ifPresent(suggestion -> message.append(" / suggestion: ").append(suggestion));

        return ToolIssue.builder().severity(mapSeverity(issue.getSeverity())).code(issue.getCode()).message(message.toString()).build();
    }

    private ToolIssueSeverity mapSeverity(EpubValidationIssue.Severity severity) {

        if (severity == null) return ToolIssueSeverity.ERROR;

        return switch (severity) {
            case INFO -> ToolIssueSeverity.INFO;
            case WARNING -> ToolIssueSeverity.WARNING;
            case ERROR, FATAL -> ToolIssueSeverity.ERROR;
        };
    }

    private ToolStatus resolveToolStatus(EpubValidationResult result) {

        if (result == null) return ToolStatus.FAILED;

        return switch (result.getStatus()) {
            case PASSED, PASSED_WITH_WARNINGS, PARTIAL -> ToolStatus.SUCCESS;
            case NOT_PERFORMED, FAILED -> ToolStatus.VALIDATION_FAILED;
        };
    }

    private boolean supportsMode(ValidationMode mode) {

        if (mode == null) return false;

        return switch (mode) {
            case INTERNAL -> internalValidator != null;
            case ACCESSIBILITY -> accessibilityValidator != null;
            case EPUB_CHECK -> epubCheckValidator != null && epubCheckValidator.isAvailable();
            case ALL -> compositeValidator != null && !compositeValidator.isEmpty();
        };
    }

    private EpubValidator requireInternalValidator() {

        if (internalValidator == null) throw new IllegalStateException("Internal EPUB validator is not configured.");

        return internalValidator;
    }

    private EpubAccessibilityValidator requireAccessibilityValidator() {

        if (accessibilityValidator == null) throw new IllegalStateException("EPUB accessibility validator is not configured.");

        return accessibilityValidator;
    }

    private EpubCheckValidator requireEpubCheckValidator() {

        if (epubCheckValidator == null) throw new IllegalStateException("EPUBCheck validator is not configured.");
        if (!epubCheckValidator.isAvailable()) throw new IllegalStateException(epubCheckValidator.getAvailability().getMessage().orElse("EPUBCheck is not available."));

        return epubCheckValidator;
    }

    private CompositeEpubValidator requireCompositeValidator() {

        if (compositeValidator == null || compositeValidator.isEmpty()) throw new IllegalStateException("Composite EPUB validator is not configured.");

        return compositeValidator;
    }

    private Path resolveEpubFile(ToolRequest request, ToolContext context) {

        Path result = request == null ? null : resolvePathFromArguments(request.getArguments());

        if (result != null) return result;

        if (context != null) {
            result = toPath(context.getAttribute(EPUB_FILE_ARGUMENT));
            if (result != null) return result;
        }

        Path publishDirectory = publishDirectoryProvider.getPublishDirectory();

        if (publishDirectory == null) return null;

        return publishedEpubResolver.resolve(publishDirectory);
    }

    private Path resolvePathFromArguments(Object arguments) {

        if (arguments == null) return null;

        Path direct = toPath(arguments);

        if (direct != null) return direct;
        if (arguments instanceof Map<?, ?> map) return toPath(map.get(EPUB_FILE_ARGUMENT));

        return null;
    }

    private Path resolveProjectRoot(ToolRequest request, ToolContext context) {

        if (context != null) {
            Path path = toPath(context.getAttribute(PROJECT_ROOT_ARGUMENT));
            if (path != null) return path.toAbsolutePath().normalize();
        }

        if (context != null) {
            EpubGenerationRequest generationRequest = context.getAttribute(EPUB_GENERATION_REQUEST_ATTRIBUTE, EpubGenerationRequest.class);
            if (generationRequest != null && generationRequest.getProjectRoot() != null) return generationRequest.getProjectRoot().toAbsolutePath().normalize();
        }

        Path path = toPath(getArgumentValue(request, PROJECT_ROOT_ARGUMENT));

        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private ValidationMode resolveValidationMode(ToolRequest request, ToolContext context) {

        Object value = getArgumentValue(request, VALIDATION_MODE_ARGUMENT);

        if (value == null && context != null) value = context.getAttribute(VALIDATION_MODE_ARGUMENT);
        if (value instanceof ValidationMode mode) return mode;
        if (value instanceof String text && !text.isBlank()) return ValidationMode.from(text);

        return ValidationMode.ALL;
    }

    private EpubGenerationOptions resolveOptions(ToolContext context) {

        if (context != null) {
            EpubGenerationOptions options = context.getAttribute(GENERATION_OPTIONS_ATTRIBUTE, EpubGenerationOptions.class);
            if (options != null) return options;
        }

        return EpubGenerationOptions.defaultOptions();
    }

    private Object getArgumentValue(ToolRequest request, String name) {

        if (request == null || name == null || name.isBlank()) return null;

        return request.getArguments().get(name);
    }

    private Path toPath(Object value) {

        if (value == null) return null;
        if (value instanceof Path path) return path;

        if (value instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty()) return null;
            return Path.of(normalized);
        }

        return null;
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put(EPUB_FILE_ARGUMENT, Map.of("type", "string", "description", "Path of the EPUB file to validate."));
        properties.put(VALIDATION_MODE_ARGUMENT, Map.of("type", "string", "enum", List.of("INTERNAL", "ACCESSIBILITY", "EPUB_CHECK", "ALL"), "description", "EPUB validation mode."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.of(EPUB_FILE_ARGUMENT));

        return Map.copyOf(schema);
    }

    private ToolResult failure(String errorCode, String errorMessage, Path epubFile, ValidationMode mode, Throwable cause) {

        String code = errorCode == null || errorCode.isBlank() ? "EPUB_VALIDATION_FAILED" : errorCode.trim();
        String message = errorMessage == null || errorMessage.isBlank() ? "EPUB validation failed." : errorMessage.trim();

        ToolResult.Builder builder = ToolResult.builder().toolName(TOOL_NAME).status(ToolStatus.FAILED).message(message).errorCode(code).errorMessage(message).issue(errorIssue(code, message));

        if (epubFile != null) builder.data("epubFile", epubFile.toAbsolutePath().normalize().toString());
        if (mode != null) builder.data("validationMode", mode.name());

        if (cause != null) {
            builder.cause(cause);
            builder.data("exceptionType", cause.getClass().getName());
        }

        return builder.build();
    }

    private ToolIssue errorIssue(String code, String message) {
        return ToolIssue.builder().severity(ToolIssueSeverity.ERROR).code(code).message(message).build();
    }

    private static String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown EPUB validation error.";

        String message = throwable.getMessage();

        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message.trim();
    }

    public enum ValidationMode {

        INTERNAL,
        ACCESSIBILITY,
        EPUB_CHECK,
        ALL;

        public static ValidationMode from(String value) {

            if (value == null || value.isBlank()) return ALL;

            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

            return switch (normalized) {
                case "INTERNAL", "STRUCTURE", "BASIC" -> INTERNAL;
                case "ACCESSIBILITY", "A11Y" -> ACCESSIBILITY;
                case "EPUBCHECK", "EPUB_CHECK", "CHECK" -> EPUB_CHECK;
                case "ALL", "FULL" -> ALL;
                default -> throw new IllegalArgumentException("Unsupported EPUB validation mode: " + value);
            };
        }
    }
}