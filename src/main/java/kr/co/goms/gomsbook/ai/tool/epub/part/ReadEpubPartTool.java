/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.part;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartPage;
import kr.co.goms.gomsbook.ai.epub.part.EpubPartReader;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

public final class ReadEpubPartTool implements AgentTool {

    public static final String TOOL_NAME = "read_epub_part";

    private static final String ARGUMENT_FILE_NAME = "fileName";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubPartReader partReader;

    public ReadEpubPartTool(CurrentProjectProvider currentProjectProvider) {
        this(currentProjectProvider, new EpubPartReader());
    }

    public ReadEpubPartTool(CurrentProjectProvider currentProjectProvider, EpubPartReader partReader) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (partReader == null) throw new IllegalArgumentException("partReader must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.partReader = partReader;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Reads an existing EPUB Part XHTML file from the current EPUB project. "
                + "Use this tool only to obtain the current contents of an existing EPUB Part. "
                + "When the user asks to modify, edit, replace, append, rewrite, or otherwise change an EPUB Part, "
                + "first use this tool to read the existing Part and then always call update_epub_part with the modified content. "
                + "Do not stop after reading the Part and do not present a modification proposal as the final response. "
                + "This tool never modifies EPUB files and never creates an approval request.";
    }
    
    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put(
                ARGUMENT_FILE_NAME,
                Map.of(
                        "type", "string",
                        "description", "EPUB Part XHTML file name to read, for example part01.xhtml."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(ARGUMENT_FILE_NAME));
        schema.put("additionalProperties", false);

        return Map.copyOf(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        if (request == null) throw new IllegalArgumentException("request must not be null.");

        try {

            EpubProjectContext project = requireProject();
            String fileName = requireFileName(request);
            Path textDirectory = project.getTextDirectory();

            EpubPartPage page = partReader.read(textDirectory, fileName);

            return createResult(request, project, textDirectory, page);

        } catch (Exception exception) {

            return ToolResult
                    .failure(
                            TOOL_NAME,
                            "Failed to read EPUB part: " + safeMessage(exception),
                            exception)
                    .build();
        }
    }

    private EpubProjectContext requireProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB text directory is not available.");

        return project;
    }

    private String requireFileName(ToolRequest request) {

        String fileName = request.getArgument(ARGUMENT_FILE_NAME, String.class);

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");

        return fileName.trim();
    }

    private ToolResult createResult(
            ToolRequest request,
            EpubProjectContext project,
            Path textDirectory,
            EpubPartPage page) {

        Path xhtmlPath = textDirectory.resolve(page.getFileName()).toAbsolutePath().normalize();

        ToolResult.Builder builder = ToolResult.success(TOOL_NAME);

        if (request.hasRequestId()) builder.requestId(request.getRequestId());
        if (request.hasToolCallId()) builder.toolCallId(request.getToolCallId());

        builder.message("EPUB Part를 읽었습니다.");
        builder.data("projectName", project.getProjectName());
        builder.data("partNumber", page.getPartNumber());
        builder.data("fileName", page.getFileName());
        builder.data("title", page.getTitle());
        builder.data("displayTitle", page.getDisplayTitle());
        builder.data("manifestId", page.getManifestId());
        builder.data("xhtmlPath", xhtmlPath.toString());
        builder.data("xhtml", page.getXhtml());

        return builder.build();
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";

        String message = throwable.getMessage();

        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message.trim();
    }
}