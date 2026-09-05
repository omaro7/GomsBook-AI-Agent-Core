/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.author;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.EpubAuthorApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.author.EpubAuthorPageInspector;
import kr.co.goms.gomsbook.ai.epub.author.EpubAuthorPageState;
import kr.co.goms.gomsbook.ai.epub.author.EpubAuthorReader;
import kr.co.goms.gomsbook.ai.epub.generation.author.DefaultEpubAuthorXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.generation.author.EpubAuthorPage;
import kr.co.goms.gomsbook.ai.epub.generation.author.EpubAuthorXhtmlGenerator;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class UpdateEpubAuthorTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_author";

    private static final String APPROVAL_TITLE = "작가소개 페이지 수정";
    private static final String AUTHOR_FILE_NAME = "author.xhtml";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final EpubAuthorPageInspector authorPageInspector;
    private final EpubAuthorReader authorReader;
    private final EpubAuthorXhtmlGenerator xhtmlGenerator;
    private final Gson gson;

    public UpdateEpubAuthorTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService) {

        this(
                currentProjectProvider,
                approvalService,
                new EpubAuthorPageInspector(),
                new EpubAuthorReader(),
                new DefaultEpubAuthorXhtmlGenerator(),
                new Gson());
    }

    public UpdateEpubAuthorTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService,
            EpubAuthorPageInspector authorPageInspector,
            EpubAuthorReader authorReader,
            EpubAuthorXhtmlGenerator xhtmlGenerator,
            Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (approvalService == null) throw new IllegalArgumentException("approvalService must not be null.");
        if (authorPageInspector == null) throw new IllegalArgumentException("authorPageInspector must not be null.");
        if (authorReader == null) throw new IllegalArgumentException("authorReader must not be null.");
        if (xhtmlGenerator == null) throw new IllegalArgumentException("xhtmlGenerator must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.approvalService = approvalService;
        this.authorPageInspector = authorPageInspector;
        this.authorReader = authorReader;
        this.xhtmlGenerator = xhtmlGenerator;
        this.gson = gson;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "현재 EPUB 프로젝트의 기존 작가소개 페이지 정보를 수정하고 사용자 승인을 요청합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("authorName", stringProperty("작가 이름입니다."));
        properties.put("introduction", stringProperty("작가소개 도입 문장입니다."));
        properties.put("profile", stringProperty("작가 프로필입니다."));
        properties.put("careers", stringArrayProperty("작가 경력 목록입니다."));
        properties.put("imageFileName", stringProperty("작가 이미지 파일명입니다."));
        properties.put("imageAlt", stringProperty("작가 이미지 대체 텍스트입니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        try {

            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();
            Map<String, Object> arguments = request.getArguments();

            if (arguments == null || arguments.isEmpty()) {
                throw new IllegalArgumentException("Author update arguments must not be empty.");
            }

            EpubAuthorPageState state = authorPageInspector.inspect(project);

            validateAuthorPageState(state);

            Path authorFile = resolveAuthorFile(project);
            EpubAuthorPage page = authorReader.read(authorFile);

            applyUpdates(page, arguments);
            validatePage(page);

            String preview = createPreview(project, page);
            String content = createApprovalContent(page);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String fileName = page.getFileName();
            String approvalMessage = "다음 내용으로 " + fileName + "을 수정하시겠습니까?";

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    approvalMessage,
                    fileName,
                    content);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("approvalRequired", true);
            data.put("approvalId", approval.getApprovalId());
            data.put("action", approval.getAction());
            data.put("title", approval.getTitle());
            data.put("message", approval.getMessage());
            data.put("fileName", approval.getFileName());
            data.put("content", approval.getContent());
            data.put("previewTitle", "내용");
            data.put("preview", preview);
            data.put("displayInstruction", "미리보기 섹션 제목은 반드시 '내용'으로 표시하고 콜론(:)을 붙이지 마세요.");

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB author update approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to update EPUB author page: " + safeMessage(exception);

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

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

        return project;
    }

    private void validateAuthorPageState(EpubAuthorPageState state) {

        if (state == null) throw new IllegalStateException("Author page state is not available.");

        if (state.isEmpty()) {
            throw new IllegalStateException("Author page does not exist. Create the author page before updating it.");
        }

        if (!state.isValid()) {
            throw new IllegalStateException("Author page EPUB structure is inconsistent: " + state);
        }
    }

    private Path resolveAuthorFile(EpubProjectContext project) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path authorFile = textDirectory.resolve(AUTHOR_FILE_NAME).normalize();

        if (!authorFile.startsWith(textDirectory)) {
            throw new IllegalStateException("Author XHTML must be inside the EPUB Text directory.");
        }

        if (!Files.exists(authorFile)) {
            throw new IllegalStateException("Author XHTML does not exist: " + authorFile);
        }

        if (!Files.isRegularFile(authorFile)) {
            throw new IllegalStateException("Author XHTML path is not a file: " + authorFile);
        }

        return authorFile;
    }

    private void applyUpdates(
            EpubAuthorPage page,
            Map<String, Object> arguments) {

        if (page == null) throw new IllegalArgumentException("EpubAuthorPage must not be null.");
        if (arguments == null || arguments.isEmpty()) throw new IllegalArgumentException("Author update arguments must not be empty.");

        setIfPresent(arguments, "authorName", page::setAuthorName);
        setIfPresent(arguments, "introduction", page::setIntroduction);
        setIfPresent(arguments, "profile", page::setProfile);
        setStringListIfPresent(arguments, "careers", page::setCareers);
        setIfPresent(arguments, "imageFileName", page::setImageFileName);
        setIfPresent(arguments, "imageAlt", page::setImageAlt);
    }

    private void setIfPresent(
            Map<String, Object> arguments,
            String name,
            Consumer<String> setter) {

        if (!arguments.containsKey(name)) return;

        Object value = arguments.get(name);

        setter.accept(value == null ? null : trimToNull(String.valueOf(value)));
    }

    private void setStringListIfPresent(
            Map<String, Object> arguments,
            String name,
            Consumer<List<String>> setter) {

        if (!arguments.containsKey(name)) return;

        Object value = arguments.get(name);

        if (value == null) {
            setter.accept(List.of());
            return;
        }

        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException(name + " must be an array.");
        }

        List<String> values = new ArrayList<>();

        for (Object item : (List<?>) value) {

            if (item == null) continue;

            String text = trimToNull(String.valueOf(item));

            if (text != null) values.add(text);
        }

        setter.accept(values);
    }

    private void validatePage(EpubAuthorPage page) {

        if (page == null) throw new IllegalArgumentException("EpubAuthorPage must not be null.");
        if (isBlank(page.getFileName())) throw new IllegalArgumentException("fileName must not be blank.");
        if (isBlank(page.getAuthorName())) throw new IllegalArgumentException("authorName must not be blank.");
    }

    private String createApprovalContent(EpubAuthorPage page) {

        EpubAuthorApprovalPayload payload = new EpubAuthorApprovalPayload();

        payload.setFileName(page.getFileName());
        payload.setAuthorName(page.getAuthorName());
        payload.setIntroduction(page.getIntroduction());
        payload.setProfile(page.getProfile());
        payload.setCareers(page.getCareers());
        payload.setImageFileName(page.getImageFileName());
        payload.setImageAlt(page.getImageAlt());

        return gson.toJson(payload);
    }

    private String createPreview(
            EpubProjectContext project,
            EpubAuthorPage page) {

        if (project == null) throw new IllegalArgumentException("project must not be null.");
        if (page == null) throw new IllegalArgumentException("page must not be null.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

        try {

            Path textDirectory = project.getTextDirectory()
                    .toAbsolutePath()
                    .normalize();

            return xhtmlGenerator.render(
                    page,
                    textDirectory);

        } catch (RuntimeException exception) {

            throw new IllegalStateException(
                    "Failed to create author XHTML update preview.",
                    exception);
        }
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

    private Map<String, Object> stringProperty(String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }

    private Map<String, Object> stringArrayProperty(String description) {

        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "string");

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "array");
        property.put("description", description);
        property.put("items", items);

        return property;
    }

    private void deleteDirectory(Path directory) {

        if (directory == null || !Files.exists(directory)) return;

        try (Stream<Path> stream = Files.walk(directory)) {

            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {

                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignore) {
                        }
                    });

        } catch (Exception ignore) {
        }
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}