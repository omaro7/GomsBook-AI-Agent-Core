/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.copyright;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.epub.copyright.EpubCopyrightLocator;
import kr.co.goms.gomsbook.ai.epub.generation.copyright.DefaultEpubCopyrightXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.generation.copyright.EpubCopyrightPage;
import kr.co.goms.gomsbook.ai.epub.generation.copyright.EpubCopyrightXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.plan.copyright.CreateEpubCopyrightPlan;
import kr.co.goms.gomsbook.ai.epub.resource.EpubStylesheetResolver;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class CreateEpubCopyrightTool implements AgentTool {

    public static final String TOOL_NAME = "create_epub_copyright";

    private static final String APPROVAL_TITLE = "판권 페이지 생성";
    private static final String DEFAULT_FILE_NAME = "copyright.xhtml";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final EpubCopyrightLocator copyrightLocator;
    private final EpubStylesheetResolver stylesheetResolver;
    private final EpubCopyrightXhtmlGenerator xhtmlGenerator;

    public CreateEpubCopyrightTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService) {

        this(
                currentProjectProvider,
                approvalService,
                new EpubCopyrightLocator(),
                new EpubStylesheetResolver(),
                new DefaultEpubCopyrightXhtmlGenerator());
    }

    public CreateEpubCopyrightTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService,
            EpubCopyrightLocator copyrightLocator,
            EpubStylesheetResolver stylesheetResolver,
            EpubCopyrightXhtmlGenerator xhtmlGenerator) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (approvalService == null) throw new IllegalArgumentException("approvalService must not be null.");
        if (copyrightLocator == null) throw new IllegalArgumentException("copyrightLocator must not be null.");
        if (stylesheetResolver == null) throw new IllegalArgumentException("stylesheetResolver must not be null.");
        if (xhtmlGenerator == null) throw new IllegalArgumentException("xhtmlGenerator must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.approvalService = approvalService;
        this.copyrightLocator = copyrightLocator;
        this.stylesheetResolver = stylesheetResolver;
        this.xhtmlGenerator = xhtmlGenerator;
    }

    @Override
    public String getName() {

        return TOOL_NAME;
    }

    @Override
    public String getDescription() {

        return "현재 EPUB 프로젝트에 새로운 판권 페이지를 생성하기 위한 미리보기를 만들고 사용자 승인을 요청합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("fileName", stringProperty("생성할 판권 XHTML 파일명입니다. 기본값은 copyright.xhtml입니다."));
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

            validateCopyrightNotExists(project);

            CreateEpubCopyrightPlan plan = createPlan(request);
            EpubCopyrightPage page = plan.getPage();

            prepareStylesheet(project, page);

            String content = createPreview(page);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
            String fileName = page.getFileName();
            String approvalMessage = "다음 내용으로 " + fileName + "을 생성하시겠습니까?";

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
                    .message("EPUB copyright creation approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {

            String errorMessage = "Failed to prepare EPUB copyright creation: " + safeMessage(exception);

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

    private void validateCopyrightNotExists(EpubProjectContext project) {

        copyrightLocator.find(project.getTextDirectory()).ifPresent(path -> {
            throw new IllegalStateException("Copyright page already exists: " + path + ". Use update_epub_copyright.");
        });
    }

    private CreateEpubCopyrightPlan createPlan(ToolRequest request) {

        EpubCopyrightPage page = new EpubCopyrightPage();

        page.setFileName(getString(request, "fileName", DEFAULT_FILE_NAME));
        page.setTitle(getString(request, "title"));
        page.setPublicationDate(getString(request, "publicationDate"));
        page.setAuthor(getString(request, "author"));
        page.setPublisherRepresentative(getString(request, "publisherRepresentative"));
        page.setPublisher(getString(request, "publisher"));
        page.setAddress(getString(request, "address"));
        page.setEmail(getString(request, "email"));
        page.setWebsite(getString(request, "website"));
        page.setPublishingRegistration(getString(request, "publishingRegistration"));
        page.setIsbn(getString(request, "isbn"));
        page.setPrice(getString(request, "price"));
        page.setSupportText(getString(request, "supportText"));
        page.setCopyrightHolder(getString(request, "copyrightHolder"));
        page.setCopyrightYear(getString(request, "copyrightYear"));
        page.setCopyrightText(getString(request, "copyrightText"));

        validatePage(page);

        return new CreateEpubCopyrightPlan(true, page);
    }

    private void validatePage(EpubCopyrightPage page) {

        if (page == null) throw new IllegalArgumentException("EpubCopyrightPage must not be null.");
        if (isBlank(page.getFileName())) throw new IllegalArgumentException("fileName must not be blank.");
        if (!page.getFileName().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("fileName must be an XHTML file.");
    }

    private void prepareStylesheet(
            EpubProjectContext project,
            EpubCopyrightPage page) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path copyrightFile = textDirectory.resolve(page.getFileName()).normalize();

        if (!copyrightFile.startsWith(textDirectory)) throw new IllegalArgumentException("Copyright file must be inside the EPUB Text directory.");

        String stylesheetHref = stylesheetResolver.resolveHref(copyrightFile);

        page.setStylesheetHref(stylesheetHref);
    }

    private String createPreview(
            EpubCopyrightPage page) {

        Path tempDirectory = null;

        try {

            tempDirectory = Files.createTempDirectory("gomsbook-copyright-preview-");

            Path generatedFile = xhtmlGenerator.generate(page, tempDirectory);

            return Files.readString(generatedFile, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to create copyright XHTML preview.", exception);

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

    private String getString(
            ToolRequest request,
            String name) {

        String value = request.getArgument(name, String.class);

        return trimToNull(value);
    }

    private String getString(
            ToolRequest request,
            String name,
            String defaultValue) {

        String value = getString(request, name);

        return value != null ? value : defaultValue;
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {

        return value == null || value.trim().isEmpty();
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