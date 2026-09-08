/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.chapter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.epub.chapter.EpubChapterReader;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class ReadEpubChapterTool implements AgentTool {

    public static final String TOOL_NAME = "read_epub_chapter";

    private static final String ERROR_CODE = "EPUB_CHAPTER_READ_FAILED";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubChapterReader chapterReader;

    public ReadEpubChapterTool(CurrentProjectProvider currentProjectProvider) {
        this(currentProjectProvider, new EpubChapterReader());
    }

    public ReadEpubChapterTool(CurrentProjectProvider currentProjectProvider, EpubChapterReader chapterReader) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (chapterReader == null) throw new IllegalArgumentException("chapterReader must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.chapterReader = chapterReader;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Reads an existing chapter XHTML document from the current EPUB project. "
                + "Use this tool when the user asks to read, show, inspect, view, retrieve, or check the content of an existing chapter. "
                + "The target chapter is identified by its XHTML file name. "
                + "This tool only reads the existing chapter and does not create, update, delete, or modify any EPUB file. "
                + "Do not use this tool to read parts, navigation, metadata, author information, copyright information, or other EPUB components.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("fileName", stringProperty("읽을 Chapter XHTML 파일명입니다. 예: chapter01_01.xhtml"));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("fileName"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            String fileName = getString(request, "fileName");

            if (isBlank(fileName)) throw new IllegalArgumentException("fileName must not be blank.");

            String xhtml = chapterReader.read(fileName, project.getTextDirectory());

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("fileName", fileName);
            data.put("xhtml", xhtml);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB chapter read successfully.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            return failure(request, exception);
        }
    }

    private ToolResult failure(ToolRequest request, RuntimeException exception) {

        String message = "Failed to read EPUB chapter: " + safeMessage(exception);

        ToolResult.Builder builder = ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.FAILED)
                .message(message)
                .errorCode(ERROR_CODE)
                .errorMessage(message)
                .cause(exception);

        if (request != null) {

            builder.requestId(request.getRequestId());
            builder.toolCallId(request.getToolCallId());
        }

        builder.data("exceptionType", exception.getClass().getName());

        return builder.build();
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

        return project;
    }

    private Map<String, Object> stringProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }

    private String getString(ToolRequest request, String name) {

        String value = request.getArgument(name, String.class);

        return trimToNull(value);
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();

        return message.trim();
    }
}