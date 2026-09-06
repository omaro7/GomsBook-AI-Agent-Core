/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.EpubNavigationApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class CreateEpubNavigationTool implements AgentTool {

    public static final String TOOL_NAME = "create_epub_navigation";

    private static final String DESCRIPTION =
            "Creates the table of contents navigation file (nav.xhtml) for the current EPUB project. "
                    + "Use this tool when the user asks to create a table of contents, TOC, navigation file, or nav.xhtml. "
                    + "Use the table of contents structure already provided or agreed upon in the conversation. "
                    + "This tool is only for the currently selected EPUB project. "
                    + "Do not use this tool to create a new EPUB project. "
                    + "Do not use this tool to modify an existing nav.xhtml. "
                    + "Do not create chapter XHTML files or modify content.opf with this tool. "
                    + "The generated navigation document requires user approval before the file is created.";

    private static final String APPROVAL_TITLE = "EPUB 목차 생성";
    private static final String DEFAULT_TITLE = "차례";
    private static final String DEFAULT_LANGUAGE = "ko";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public CreateEpubNavigationTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService) {
        this(currentProjectProvider, approvalService, new Gson());
    }

    public CreateEpubNavigationTool(CurrentProjectProvider currentProjectProvider, AgentApprovalService approvalService, Gson gson) {
        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (approvalService == null) throw new IllegalArgumentException("approvalService must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
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

        properties.put("title", stringProperty("Navigation Document 제목입니다. 기본값은 차례입니다."));
        properties.put("language", stringProperty("Navigation Document 언어입니다. 기본값은 ko입니다."));
        properties.put("items", navigationItemsProperty());

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("items"));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {
        try {
            if (request == null) throw new IllegalArgumentException("ToolRequest must not be null.");

            EpubProjectContext project = requireCurrentProject();

            validateNavigationNotExists(project);

            String fileName = project.getNavigationFile().getFileName().toString();
            String title = getString(request, "title", DEFAULT_TITLE);
            String language = getString(request, "language", DEFAULT_LANGUAGE);

            List<EpubNavigationItem> tocItems = createNavigationItems(request);
            EpubNavigation navigation = createNavigation(title, language, tocItems);

            EpubNavigationApprovalPayload payload = EpubNavigationApprovalPayload.from(fileName, navigation);

            String content = gson.toJson(payload);
            String preview = createPreview(navigation);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
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
            data.put("previewTitle", "내용");
            data.put("preview", preview);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB navigation creation approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {
            String errorMessage = "Failed to prepare EPUB navigation creation: " + safeMessage(exception);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .status(ToolStatus.FAILED)
                    .message(errorMessage)
                    .errorCode("EPUB_NAVIGATION_CREATE_FAILED")
                    .errorMessage(errorMessage)
                    .cause(exception)
                    .build();
        }
    }

    private EpubProjectContext requireCurrentProject() {
        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getNavigationFile() == null) throw new IllegalStateException("Current EPUB navigation file is not available.");

        return project;
    }

    private void validateNavigationNotExists(EpubProjectContext project) {
        if (project.hasNavigationFile()) throw new IllegalStateException("EPUB navigation already exists: " + project.getNavigationFile() + ". Use update_epub_navigation instead.");
    }

    private EpubNavigation createNavigation(String title, String language, List<EpubNavigationItem> tocItems) {
        EpubNavigation navigation = EpubNavigation.builder()
                .title(title)
                .language(language)
                .tocTitle(title)
                .tocItems(tocItems)
                .includeLandmarks(false)
                .includePageList(false)
                .build();

        navigation.validate();

        return navigation;
    }

    private List<EpubNavigationItem> createNavigationItems(ToolRequest request) {
        Object value = request.getArguments() == null ? null : request.getArguments().get("items");

        if (!(value instanceof List<?>)) throw new IllegalArgumentException("items must be an array.");

        List<?> values = (List<?>) value;

        if (values.isEmpty()) throw new IllegalArgumentException("items must contain at least one navigation item.");

        List<EpubNavigationItem> result = new ArrayList<>();

        for (Object item : values) {
            if (!(item instanceof Map<?, ?>)) throw new IllegalArgumentException("Each navigation item must be an object.");

            result.add(createNavigationItem((Map<?, ?>) item));
        }

        return List.copyOf(result);
    }

    private EpubNavigationItem createNavigationItem(Map<?, ?> source) {
        String label = getMapString(source, "label");
        String href = getMapString(source, "href");
        String id = getMapString(source, "id");
        String epubType = getMapString(source, "epubType");

        if (isBlank(label)) throw new IllegalArgumentException("Navigation item label must not be blank.");
        if (isBlank(href)) throw new IllegalArgumentException("Navigation item href must not be blank.");

        EpubNavigationItem.Builder builder = EpubNavigationItem.builder(label, href);

        if (!isBlank(id)) builder.id(id);
        if (!isBlank(epubType)) builder.epubType(epubType);

        Object childrenValue = source.get("children");

        if (childrenValue instanceof List<?>) {
            List<EpubNavigationItem> children = new ArrayList<>();

            for (Object child : (List<?>) childrenValue) {
                if (!(child instanceof Map<?, ?>)) throw new IllegalArgumentException("Each navigation child item must be an object.");

                children.add(createNavigationItem((Map<?, ?>) child));
            }

            builder.children(children);
        }

        return builder.build();
    }

    private String createPreview(EpubNavigation navigation) {
        StringBuilder builder = new StringBuilder();

        builder.append("<nav epub:type=\"toc\" role=\"doc-toc\" aria-labelledby=\"toc-title\">");
        builder.append("<h1 id=\"toc-title\">");
        builder.append(escapeHtml(navigation.getTocTitle()));
        builder.append("</h1>");
        builder.append("<ol>");

        appendPreviewItems(builder, navigation.getTocItems());

        builder.append("</ol>");
        builder.append("</nav>");

        return builder.toString();
    }

    private void appendPreviewItems(StringBuilder builder, List<EpubNavigationItem> items) {
        for (EpubNavigationItem item : items) {
            builder.append("<li>");
            builder.append("<a href=\"");
            builder.append(escapeHtml(item.getHref()));
            builder.append("\">");
            builder.append(escapeHtml(item.getLabel()));
            builder.append("</a>");

            if (item.hasChildren()) {
                builder.append("<ol>");
                appendPreviewItems(builder, item.getChildren());
                builder.append("</ol>");
            }

            builder.append("</li>");
        }
    }

    private Map<String, Object> navigationItemsProperty() {
        Map<String, Object> childProperties = new LinkedHashMap<>();

        childProperties.put("id", stringProperty("하위 Navigation item ID입니다."));
        childProperties.put("label", stringProperty("하위 목차에 표시할 제목입니다."));
        childProperties.put("href", stringProperty("하위 목차 항목이 참조할 EPUB 상대 경로입니다."));
        childProperties.put("epubType", stringProperty("EPUB semantic type입니다. 예: chapter"));

        Map<String, Object> childItem = new LinkedHashMap<>();

        childItem.put("type", "object");
        childItem.put("properties", childProperties);
        childItem.put("required", List.of("label", "href"));
        childItem.put("additionalProperties", false);

        Map<String, Object> children = new LinkedHashMap<>();

        children.put("type", "array");
        children.put("items", childItem);

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("id", stringProperty("Navigation item ID입니다."));
        properties.put("label", stringProperty("목차에 표시할 제목입니다."));
        properties.put("href", stringProperty("목차 항목이 참조할 EPUB 상대 경로입니다."));
        properties.put("epubType", stringProperty("EPUB semantic type입니다. 예: part, chapter, appendix"));
        properties.put("children", children);

        Map<String, Object> item = new LinkedHashMap<>();

        item.put("type", "object");
        item.put("properties", properties);
        item.put("required", List.of("label", "href"));
        item.put("additionalProperties", false);

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "array");
        property.put("description", "EPUB 목차 항목 목록입니다.");
        property.put("items", item);

        return property;
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }

    private String getString(ToolRequest request, String name) {
        return trimToNull(request.getArgument(name, String.class));
    }

    private String getString(ToolRequest request, String name, String defaultValue) {
        String value = getString(request, name);

        return value != null ? value : defaultValue;
    }

    private String getMapString(Map<?, ?> source, String name) {
        Object value = source.get(name);

        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private String resolveRunId(ToolRequest request, ToolContext context) {
        if (context != null && !isBlank(context.getRequestId())) return context.getRequestId().trim();
        if (request != null && !isBlank(request.getRequestId())) return request.getRequestId().trim();

        throw new IllegalStateException("runId is not available from ToolContext or ToolRequest.");
    }

    private String resolveProjectId(EpubProjectContext project) {
        if (!isBlank(project.getProjectName())) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private String trimToNull(String value) {
        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escapeHtml(String value) {
        if (value == null) return "";

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) return "Unknown error.";
        if (isBlank(throwable.getMessage())) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}