/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.author;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import kr.co.goms.gomsbook.ai.epub.author.EpubAuthorPageInspector;
import kr.co.goms.gomsbook.ai.epub.author.EpubAuthorPageState;
import kr.co.goms.gomsbook.ai.epub.author.EpubAuthorReader;
import kr.co.goms.gomsbook.ai.epub.generation.author.EpubAuthorPage;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public class ReadEpubAuthorTool implements AgentTool {

    public static final String TOOL_NAME = "read_epub_author";

    private static final String TYPE = "epub_author";
    private static final String ACTION = "author_read";
    private static final String AUTHOR_FILE_NAME = "author.xhtml";

    private final CurrentProjectProvider projectProvider;
    private final EpubAuthorPageInspector authorPageInspector;
    private final EpubAuthorReader authorReader;

    public ReadEpubAuthorTool(CurrentProjectProvider projectProvider) {
        this(projectProvider, new EpubAuthorPageInspector(), new EpubAuthorReader());
    }

    public ReadEpubAuthorTool(
            CurrentProjectProvider projectProvider,
            EpubAuthorPageInspector authorPageInspector,
            EpubAuthorReader authorReader) {

        if (projectProvider == null) throw new IllegalArgumentException("projectProvider must not be null.");
        if (authorPageInspector == null) throw new IllegalArgumentException("authorPageInspector must not be null.");
        if (authorReader == null) throw new IllegalArgumentException("authorReader must not be null.");

        this.projectProvider = projectProvider;
        this.authorPageInspector = authorPageInspector;
        this.authorReader = authorReader;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "현재 EPUB 프로젝트의 작가소개 페이지 존재 여부와 등록 상태를 확인하고 author.xhtml 내용을 읽습니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        schema.put("additionalProperties", false);

        return schema;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();
            EpubAuthorPageState state = authorPageInspector.inspect(project);

            if (state.isEmpty()) return createNotFoundResult(request, project, state);
            if (!state.isValid()) return createInvalidStateResult(request, project, state);

            Path authorFile = resolveAuthorFile(project);
            EpubAuthorPage page = authorReader.read(authorFile);

            return createSuccessResult(request, project, state, page, authorFile);

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to read EPUB author page: " + safeMessage(exception);

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

        EpubProjectContext project = projectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB text directory is not available.");

        return project;
    }

    private Path resolveAuthorFile(EpubProjectContext project) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path authorFile = textDirectory.resolve(AUTHOR_FILE_NAME).normalize();

        if (!authorFile.startsWith(textDirectory)) throw new IllegalStateException("Author XHTML must be inside the EPUB Text directory.");
        if (!Files.exists(authorFile)) throw new IllegalStateException("Author XHTML does not exist: " + authorFile);
        if (!Files.isRegularFile(authorFile)) throw new IllegalStateException("Author XHTML path is not a file: " + authorFile);

        return authorFile;
    }

    private ToolResult createSuccessResult(
            ToolRequest request,
            EpubProjectContext project,
            EpubAuthorPageState state,
            EpubAuthorPage page,
            Path authorFile) {

        ToolResult.Builder builder = ToolResult.success(TOOL_NAME);

        if (request.hasRequestId()) builder.requestId(request.getRequestId());
        if (request.hasToolCallId()) builder.toolCallId(request.getToolCallId());

        builder.message("EPUB 작가소개 페이지를 읽었습니다.");

        builder.data("type", TYPE);
        builder.data("action", ACTION);
        builder.data("projectName", project.getProjectName());
        builder.data("exists", true);

        builder.data("fileExists", state.isFileExists());
        builder.data("manifestRegistered", state.isManifestRegistered());
        builder.data("spineRegistered", state.isSpineRegistered());
        builder.data("navigationRegistered", state.isNavigationRegistered());
        builder.data("valid", state.isValid());

        builder.data("fileName", page.getFileName());
        builder.data("authorName", value(page.getAuthorName()));
        builder.data("introduction", value(page.getIntroduction()));
        builder.data("profile", value(page.getProfile()));
        builder.data("careers", page.getCareers());
        builder.data("imageFileName", value(page.getImageFileName()));
        builder.data("imageAlt", value(page.getImageAlt()));
        builder.data("xhtmlPath", authorFile.toString());

        return builder.build();
    }

    private ToolResult createNotFoundResult(
            ToolRequest request,
            EpubProjectContext project,
            EpubAuthorPageState state) {

        ToolResult.Builder builder = ToolResult.success(TOOL_NAME);

        if (request.hasRequestId()) builder.requestId(request.getRequestId());
        if (request.hasToolCallId()) builder.toolCallId(request.getToolCallId());

        builder.message("현재 EPUB 프로젝트에는 작가소개 페이지가 없습니다.");

        builder.data("type", TYPE);
        builder.data("action", ACTION);
        builder.data("projectName", project.getProjectName());
        builder.data("exists", false);

        builder.data("fileExists", state.isFileExists());
        builder.data("manifestRegistered", state.isManifestRegistered());
        builder.data("spineRegistered", state.isSpineRegistered());
        builder.data("navigationRegistered", state.isNavigationRegistered());
        builder.data("valid", false);

        return builder.build();
    }

    private ToolResult createInvalidStateResult(
            ToolRequest request,
            EpubProjectContext project,
            EpubAuthorPageState state) {

        ToolResult.Builder builder = ToolResult.success(TOOL_NAME);

        if (request.hasRequestId()) builder.requestId(request.getRequestId());
        if (request.hasToolCallId()) builder.toolCallId(request.getToolCallId());

        builder.message("EPUB 작가소개 페이지의 구조가 일치하지 않습니다.");

        builder.data("type", TYPE);
        builder.data("action", ACTION);
        builder.data("projectName", project.getProjectName());
        builder.data("exists", state.isFileExists());

        builder.data("fileExists", state.isFileExists());
        builder.data("manifestRegistered", state.isManifestRegistered());
        builder.data("spineRegistered", state.isSpineRegistered());
        builder.data("navigationRegistered", state.isNavigationRegistered());
        builder.data("valid", false);

        return builder.build();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";

        String message = throwable.getMessage();

        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }
}