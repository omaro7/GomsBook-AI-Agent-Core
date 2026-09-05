/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.copyright;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.epub.copyright.EpubCopyrightLocator;
import kr.co.goms.gomsbook.ai.epub.copyright.EpubCopyrightReader;
import kr.co.goms.gomsbook.ai.epub.generation.copyright.DefaultEpubCopyrightXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.generation.copyright.EpubCopyrightPage;
import kr.co.goms.gomsbook.ai.epub.generation.copyright.EpubCopyrightXhtmlGenerator;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class UpdateEpubCopyrightTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_copyright";

    private static final String APPROVAL_TITLE = "판권 페이지 수정";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final EpubCopyrightReader copyrightReader;
    private final EpubCopyrightLocator copyrightLocator;
    private final EpubCopyrightXhtmlGenerator xhtmlGenerator;


    public UpdateEpubCopyrightTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService) {

        this(
                currentProjectProvider,
                approvalService,
                new EpubCopyrightReader(),
                new EpubCopyrightLocator(),
                new DefaultEpubCopyrightXhtmlGenerator());
    }


    public UpdateEpubCopyrightTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService,
            EpubCopyrightReader copyrightReader,
            EpubCopyrightLocator copyrightLocator,
            EpubCopyrightXhtmlGenerator xhtmlGenerator) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (approvalService == null) throw new IllegalArgumentException("approvalService must not be null.");
        if (copyrightReader == null) throw new IllegalArgumentException("copyrightReader must not be null.");
        if (copyrightLocator == null) throw new IllegalArgumentException("copyrightLocator must not be null.");
        if (xhtmlGenerator == null) throw new IllegalArgumentException("xhtmlGenerator must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.approvalService = approvalService;
        this.copyrightReader = copyrightReader;
        this.copyrightLocator = copyrightLocator;
        this.xhtmlGenerator = xhtmlGenerator;
    }


    @Override
    public String getName() {

        return TOOL_NAME;
    }


    @Override
    public String getDescription() {

        return "현재 EPUB 프로젝트의 판권 페이지 정보를 수정하기 위한 미리보기를 생성하고 사용자 승인을 요청합니다.";
    }


    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("title", stringProperty("도서명입니다."));
        properties.put("publicationDate", stringProperty("발행일입니다."));
        properties.put("author", stringProperty("지은이입니다."));
        properties.put("publisherRepresentative", stringProperty("펴낸이입니다."));
        properties.put("publisher", stringProperty("펴낸곳 또는 출판사명입니다."));
        properties.put("address", stringProperty("출판사 주소입니다."));
        properties.put("email", stringProperty("출판사 이메일입니다."));
        properties.put("website", stringProperty("출판사 웹사이트입니다."));
        properties.put("publishingRegistration", stringProperty("출판등록 정보입니다."));
        properties.put("isbn", stringProperty("ISBN입니다."));
        properties.put("price", stringProperty("도서 정가입니다. 예: 12,000원"));
        properties.put("supportText", stringProperty("지원사업 관련 문구입니다."));
        properties.put("copyrightHolder", stringProperty("저작권자입니다."));
        properties.put("copyrightYear", stringProperty("저작권 연도입니다."));
        properties.put("copyrightText", stringProperty("저작권 고지문입니다."));

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

            if (arguments == null || arguments.isEmpty()) throw new IllegalArgumentException("Copyright update arguments must not be empty.");

            Path copyrightFile = copyrightLocator.locate(project.getTextDirectory());
            EpubCopyrightPage page = copyrightReader.read(copyrightFile);

            applyUpdates(page, arguments);

            String content = createPreview(page);
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

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB copyright update approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.FAILED)
                    .message("Failed to prepare EPUB copyright update: " + safeMessage(exception))
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


    private void applyUpdates(
            EpubCopyrightPage page,
            Map<String, Object> arguments) {

        if (page == null) throw new IllegalArgumentException("EpubCopyrightPage must not be null.");
        if (arguments == null || arguments.isEmpty()) throw new IllegalArgumentException("Copyright update arguments must not be empty.");

        setIfPresent(arguments, "title", page::setTitle);
        setIfPresent(arguments, "publicationDate", page::setPublicationDate);
        setIfPresent(arguments, "author", page::setAuthor);
        setIfPresent(arguments, "publisherRepresentative", page::setPublisherRepresentative);
        setIfPresent(arguments, "publisher", page::setPublisher);
        setIfPresent(arguments, "address", page::setAddress);
        setIfPresent(arguments, "email", page::setEmail);
        setIfPresent(arguments, "website", page::setWebsite);
        setIfPresent(arguments, "publishingRegistration", page::setPublishingRegistration);
        setIfPresent(arguments, "isbn", page::setIsbn);
        setIfPresent(arguments, "price", page::setPrice);
        setIfPresent(arguments, "supportText", page::setSupportText);
        setIfPresent(arguments, "copyrightHolder", page::setCopyrightHolder);
        setIfPresent(arguments, "copyrightYear", page::setCopyrightYear);
        setIfPresent(arguments, "copyrightText", page::setCopyrightText);
    }


    private void setIfPresent(
            Map<String, Object> arguments,
            String name,
            Consumer<String> setter) {

        if (!arguments.containsKey(name)) return;

        Object value = arguments.get(name);

        setter.accept(value == null ? null : String.valueOf(value).trim());
    }


    private String createPreview(
            EpubCopyrightPage page) {

        Path tempDirectory = null;

        try {

            tempDirectory = Files.createTempDirectory("gomsbook-copyright-preview-");

            Path generatedFile = xhtmlGenerator.generate(page, tempDirectory);

            return Files.readString(generatedFile, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to create copyright XHTML preview.",
                    exception);

        } finally {

            deleteDirectory(tempDirectory);
        }
    }


    private String resolveRunId(
            ToolRequest request,
            ToolContext context) {

        if (context != null && context.getRequestId() != null && !context.getRequestId().isBlank()) return context.getRequestId().trim();
        if (request != null && request.getRequestId() != null && !request.getRequestId().isBlank()) return request.getRequestId().trim();

        throw new IllegalStateException("runId is not available from ToolContext or ToolRequest.");
    }


    private String resolveProjectId(
            EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }


    private Map<String, Object> stringProperty(
            String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }


    private void deleteDirectory(
            Path directory) {

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


    private String safeMessage(
            Throwable throwable) {

        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}