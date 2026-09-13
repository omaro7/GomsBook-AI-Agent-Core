/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.proofreading;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import kr.co.goms.gomsbook.ai.epub.proofreading.KoreanTypoChecker;
import kr.co.goms.gomsbook.ai.epub.model.EpubKoreanTypoCheckResult;
import kr.co.goms.gomsbook.ai.epub.model.EpubKoreanTypoIssue;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

/**
 * 현재 EPUB 프로젝트의 한글 오타를 검사합니다.
 *
 * Text 폴더 전체 검사:
 * {
 *   "scope": "TEXT"
 * }
 *
 * 특정 XHTML 파일 검사:
 * {
 *   "scope": "FILE",
 *   "fileName": "chapter01_01.xhtml"
 * }
 */
public final class CheckEpubKoreanTypoTool implements AgentTool {

    public static final String TOOL_NAME = "check_epub_korean_typo";

    private static final String DESCRIPTION = """
            Checks Korean text in the current EPUB project for spelling, spacing, typographical, grammatical, particle, and contextual errors using the configured Korean proofreading checker.
            Use this tool when the user asks to check, inspect, review, proofread, or find Korean typos or spelling errors in EPUB XHTML content.
            It can inspect either all XHTML files under OEBPS/Text or a single specified XHTML file.
            The tool returns proofreading candidates including the original text, suggested correction, issue type, explanation, confidence score, line number, and source offsets.
            This tool performs analysis only and never modifies EPUB files.
            Detected items are proofreading candidates and should not be treated as confirmed errors without review because literary expressions, proper nouns, intentional colloquial language, or authorial style may be valid.
            Use a separate Fix tool when the user explicitly requests that reviewed typo candidates be applied to EPUB XHTML files.
            """;

    private final CurrentProjectProvider currentProjectProvider;

    private final KoreanTypoChecker typoChecker;

    public CheckEpubKoreanTypoTool(CurrentProjectProvider currentProjectProvider, KoreanTypoChecker typoChecker) {
        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (typoChecker == null) throw new IllegalArgumentException("typoChecker must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.typoChecker = typoChecker;
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

        properties.put("scope", enumProperty(List.of("TEXT", "FILE"), "검사 범위입니다. TEXT는 OEBPS/Text 전체 XHTML, FILE은 지정한 XHTML 파일 하나를 검사합니다."));
        properties.put("fileName", stringProperty("scope가 FILE일 때 검사할 XHTML 파일명입니다. 예: chapter01_01.xhtml"));

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
            Path textDirectory = resolveTextDirectory(project);

            validateTextDirectory(textDirectory);

            List<Path> files = resolveTargetFiles(textDirectory, scope, arguments);
            List<EpubKoreanTypoIssue> issues = new ArrayList<>();

            for (Path file : files) issues.addAll(typoChecker.check(file));

            EpubKoreanTypoCheckResult result = new EpubKoreanTypoCheckResult(files.size(), issues.size(), issues);
            Map<String, Object> data = createResultData(scope, textDirectory, result);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message(createSuccessMessage(result))
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {
            String errorMessage = "Failed to check EPUB Korean typo: " + safeMessage(exception);

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

    private Map<String, Object> createResultData(Scope scope, Path textDirectory, EpubKoreanTypoCheckResult result) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("scope", scope.name());
        data.put("textDirectory", textDirectory.toString());
        data.put("checkedFileCount", result.checkedFileCount());
        data.put("issueCount", result.issueCount());
        data.put("issues", result.issues());

        return data;
    }

    private String createSuccessMessage(EpubKoreanTypoCheckResult result) {
        if (result.issueCount() == 0) return result.checkedFileCount() + "개의 XHTML 파일을 검사했으며 한글 오타 후보가 발견되지 않았습니다.";

        return result.checkedFileCount() + "개의 XHTML 파일을 검사했으며 " + result.issueCount() + "개의 한글 오타 후보가 발견되었습니다.";
    }

    private List<Path> resolveTargetFiles(Path textDirectory, Scope scope, Map<String, Object> arguments) {
        if (scope == Scope.TEXT) return collectXhtmlFiles(textDirectory);

        String fileName = requireString(arguments, "fileName");

        validateFileName(fileName);

        Path file = textDirectory.resolve(fileName).normalize();

        if (!file.startsWith(textDirectory)) throw new IllegalArgumentException("XHTML file must be inside the EPUB Text directory.");
        if (!Files.isRegularFile(file)) throw new IllegalStateException("XHTML file was not found: " + file);

        return List.of(file);
    }

    private List<Path> collectXhtmlFiles(Path textDirectory) {
        try (Stream<Path> stream = Files.walk(textDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isXhtmlFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to collect EPUB XHTML files: " + textDirectory, exception);
        }
    }

    private boolean isXhtmlFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

        return fileName.endsWith(".xhtml") || fileName.endsWith(".html");
    }

    private void validateFileName(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);

        if (!normalized.endsWith(".xhtml") && !normalized.endsWith(".html")) throw new IllegalArgumentException("fileName must be an XHTML or HTML file: " + fileName);
    }

    private void validateTextDirectory(Path textDirectory) {
        if (!Files.exists(textDirectory)) throw new IllegalStateException("EPUB Text directory was not found: " + textDirectory);
        if (!Files.isDirectory(textDirectory)) throw new IllegalStateException("EPUB Text path is not a directory: " + textDirectory);
        if (!Files.isReadable(textDirectory)) throw new IllegalStateException("EPUB Text directory is not readable: " + textDirectory);
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
                throw new IllegalArgumentException("Unsupported Korean typo check scope: " + value);
            }
        }
    }
}