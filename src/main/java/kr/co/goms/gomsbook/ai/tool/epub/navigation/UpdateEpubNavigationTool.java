/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.tool.epub.navigation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubNavigationApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationInsertPosition;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdateItem;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class UpdateEpubNavigationTool implements AgentTool {

    public static final String TOOL_NAME = "update_epub_navigation";

    private static final String APPROVAL_TITLE = "EPUB 목차 수정";
    private static final String PREVIEW_TITLE = "내용";

    private final CurrentProjectProvider currentProjectProvider;
    private final AgentApprovalService approvalService;
    private final Gson gson;

    public UpdateEpubNavigationTool(
            CurrentProjectProvider currentProjectProvider,
            AgentApprovalService approvalService,
            Gson gson) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null.");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null.");
        this.gson = Objects.requireNonNull(gson, "gson must not be null.");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "현재 EPUB 프로젝트의 기존 nav.xhtml 목차 항목을 수정하거나 위치를 이동합니다. "
                + "기존 목차 전체를 다시 생성하지 않습니다. "
                + "특정 항목의 제목 변경, 첫 위치 이동, 마지막 위치 이동, 다른 항목 앞 또는 뒤 이동에 사용합니다. "
                + "position은 FIRST, LAST, BEFORE, AFTER 중 하나를 사용합니다. "
                + "BEFORE 또는 AFTER를 사용하는 경우 referenceHref가 반드시 필요합니다. "
                + "nav.xhtml이 없는 경우 이 Tool을 사용하지 말고 create_epub_navigation을 사용합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("href", stringProperty(
                "수정하거나 이동할 기존 목차 항목의 href입니다. 예: copyright.xhtml"));

        properties.put("label", stringProperty(
                "목차에 표시할 제목입니다. 기존 제목을 유지하려면 현재 제목을 전달합니다."));

        properties.put("id", stringProperty(
                "목차 li 요소의 선택적 id입니다. 필요하지 않으면 생략합니다."));

        properties.put("epubType", stringProperty(
                "목차 링크의 선택적 epub:type입니다. 필요하지 않으면 생략합니다."));

        properties.put("position", enumProperty(
                "항목의 최종 위치입니다.",
                List.of("FIRST", "LAST", "BEFORE", "AFTER")));

        properties.put("referenceHref", stringProperty(
                "position이 BEFORE 또는 AFTER일 때 기준이 되는 목차 항목의 href입니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("href", "label", "position"));
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
            Path navigationPath = requireNavigationFile(project);

            Map<String, Object> arguments = request.getArguments();

            if (arguments == null || arguments.isEmpty()) {
                throw new IllegalArgumentException("EPUB navigation update arguments must not be empty.");
            }

            EpubNavigationUpdateItem updateItem = createUpdateItem(arguments);

            String fileName = navigationPath.getFileName().toString();
            UpdateEpubNavigationApprovalPayload payload =
                    new UpdateEpubNavigationApprovalPayload(
                            fileName,
                            List.of(updateItem));

            String content = gson.toJson(payload);
            String preview = createPreview(updateItem);
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);
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
            data.put("previewTitle", PREVIEW_TITLE);
            data.put("preview", preview);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB navigation update approval is required.")
                    .data(data)
                    .build();

        } catch (RuntimeException exception) {
            String errorMessage = "Failed to prepare EPUB navigation update: " + safeMessage(exception);

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
        if (project.getNavigationFile() == null) throw new IllegalStateException("Current EPUB navigation file is not available.");

        return project;
    }

    private Path requireNavigationFile(EpubProjectContext project) {
        Path navigationPath = project.getNavigationFile().toAbsolutePath().normalize();

        if (!Files.exists(navigationPath)) {
            throw new IllegalStateException(
                    "EPUB navigation does not exist: "
                            + navigationPath
                            + ". Use create_epub_navigation instead.");
        }

        if (!Files.isRegularFile(navigationPath)) {
            throw new IllegalStateException("EPUB navigation is not a file: " + navigationPath);
        }

        return navigationPath;
    }

    private EpubNavigationUpdateItem createUpdateItem(Map<String, Object> arguments) {
        String href = requireString(arguments, "href");
        String label = requireString(arguments, "label");
        String id = getString(arguments, "id");
        String epubType = getString(arguments, "epubType");
        EpubNavigationInsertPosition position = parsePosition(requireString(arguments, "position"));
        String referenceHref = getString(arguments, "referenceHref");

        validatePosition(position, href, referenceHref);

        EpubNavigationItem.Builder builder = EpubNavigationItem.builder(label, href);

        if (id != null) builder.id(id);
        if (epubType != null) builder.epubType(epubType);

        EpubNavigationItem navigationItem = builder.build();

        if (position == EpubNavigationInsertPosition.FIRST) {
            return EpubNavigationUpdateItem.first(navigationItem);
        }

        if (position == EpubNavigationInsertPosition.LAST) {
            return EpubNavigationUpdateItem.last(navigationItem);
        }

        if (position == EpubNavigationInsertPosition.BEFORE) {
            return EpubNavigationUpdateItem.before(navigationItem, referenceHref);
        }

        if (position == EpubNavigationInsertPosition.AFTER) {
            return EpubNavigationUpdateItem.after(navigationItem, referenceHref);
        }

        throw new IllegalArgumentException("Unsupported EPUB navigation position: " + position);
    }

    private void validatePosition(
            EpubNavigationInsertPosition position,
            String href,
            String referenceHref) {

        if (position == EpubNavigationInsertPosition.FIRST
                || position == EpubNavigationInsertPosition.LAST) {

            return;
        }

        if (referenceHref == null || referenceHref.isBlank()) {
            throw new IllegalArgumentException(
                    "referenceHref is required when position is "
                            + position
                            + ".");
        }

        if (normalizeHref(href).equals(normalizeHref(referenceHref))) {
            throw new IllegalArgumentException(
                    "Navigation item cannot be positioned relative to itself: "
                            + href);
        }
    }

    private EpubNavigationInsertPosition parsePosition(String value) {
        try {
            return EpubNavigationInsertPosition.valueOf(value.trim().toUpperCase());

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "position must be one of FIRST, LAST, BEFORE, AFTER: "
                            + value,
                    exception);
        }
    }

    private String createPreview(EpubNavigationUpdateItem updateItem) {
        EpubNavigationItem item = updateItem.getItem();
        StringBuilder builder = new StringBuilder();

        builder.append("목차 항목 수정\n");
        builder.append("제목: ").append(item.getLabel()).append('\n');
        builder.append("href: ").append(item.getHref()).append('\n');
        builder.append("위치: ").append(updateItem.getPosition());

        if (updateItem.getReferenceHref() != null) {
            builder.append('\n');
            builder.append("기준 href: ").append(updateItem.getReferenceHref());
        }

        return builder.toString();
    }

    private String requireString(
            Map<String, Object> arguments,
            String name) {

        String value = getString(arguments, name);

        if (value == null) throw new IllegalArgumentException(name + " must not be blank.");

        return value;
    }

    private String getString(
            Map<String, Object> arguments,
            String name) {

        if (arguments == null || !arguments.containsKey(name)) return null;

        Object value = arguments.get(name);

        if (value == null) return null;

        String text = String.valueOf(value).trim();

        return text.isEmpty() ? null : text;
    }

    private String resolveRunId(
            ToolRequest request,
            ToolContext context) {

        if (context != null
                && context.getRequestId() != null
                && !context.getRequestId().isBlank()) {

            return context.getRequestId().trim();
        }

        if (request != null
                && request.getRequestId() != null
                && !request.getRequestId().isBlank()) {

            return request.getRequestId().trim();
        }

        throw new IllegalStateException(
                "runId is not available from ToolContext or ToolRequest.");
    }

    private String resolveProjectId(EpubProjectContext project) {
        if (project.getProjectName() != null
                && !project.getProjectName().isBlank()) {

            return project.getProjectName().trim();
        }

        return project.getProjectRoot()
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);

        return property;
    }

    private Map<String, Object> enumProperty(
            String description,
            List<String> values) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", "string");
        property.put("description", description);
        property.put("enum", values);

        return property;
    }

    private String normalizeHref(String href) {
        if (href == null) return "";

        return href.trim().replace('\\', '/');
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) return "Unknown error.";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}