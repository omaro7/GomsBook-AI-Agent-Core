/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 현재 EPUB 프로젝트에 GomsBook OEBPS Template ZIP 적용 승인을 요청하는 Tool.
 *
 * D:\04.GomsBook-AI\GomsBook-AI-OEBPS-Template\GomsBook-AI-OEBPS-Template.zip
 * <p>
 * 실제 ZIP 적용은 수행하지 않는다.
 * 사용자가 승인하면 ApplyEpubTemplateApprovalHandler가 실제 적용 작업을 수행한다.
 * </p>
 */
public final class ApplyEpubTemplateTool implements AgentTool {

    public static final String TOOL_NAME = "apply_epub_template";

    private static final String DESCRIPTION =
            "Requests approval to apply a GomsBook OEBPS template ZIP file to the current EPUB project. "
                    + "The template may overwrite existing OEBPS files, so approval is required before applying it.";

    private static final String ARG_TEMPLATE_PATH = "templatePath";
    private static final String APPROVAL_TITLE = "EPUB 템플릿 적용";
    private static final String APPROVAL_MESSAGE = "현재 EPUB 프로젝트에 템플릿을 적용하시겠습니까? 기존 OEBPS 파일이 덮어써질 수 있습니다.";

    private final CurrentProjectProvider projectProvider;
    private final AgentApprovalService approvalService;
    
    private static final String EMPTY_CONTENT = "";


    public ApplyEpubTemplateTool(CurrentProjectProvider projectProvider, AgentApprovalService approvalService) {

        this.projectProvider = Objects.requireNonNull(projectProvider, "projectProvider must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
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

        properties.put(ARG_TEMPLATE_PATH, property("string", "Absolute or accessible local path of the GomsBook OEBPS template ZIP file."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList(ARG_TEMPLATE_PATH));
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }


    @Override
    public ToolValidationResult validate(ToolRequest request, ToolContext context) {

        List<ToolIssue> issues = new ArrayList<>();

        if (request == null) {

            issues.add(error("request", "Tool request must not be null."));

            return ToolValidationResult.invalid(issues);
        }

        if (!TOOL_NAME.equals(request.getToolName())) {

            issues.add(error("toolName", "Invalid tool name: " + request.getToolName()));

            return ToolValidationResult.invalid(issues);
        }

        if (context == null) {

            issues.add(error("context", "Tool context must not be null."));

            return ToolValidationResult.invalid(issues);
        }

        String templatePath = readString(safeArguments(request), ARG_TEMPLATE_PATH);

        if (templatePath == null || templatePath.isBlank()) {

            issues.add(error(ARG_TEMPLATE_PATH, "templatePath must not be blank."));

            return ToolValidationResult.invalid(issues);
        }

        try {

            validateTemplateZip(Path.of(templatePath.trim()));

            EpubProjectContext project = requireCurrentProject();
            Path projectRoot = normalize(project.getProjectRoot());

            if (!Files.isDirectory(projectRoot)) issues.add(error("projectRoot", "Current EPUB project root does not exist: " + projectRoot));

        } catch (Exception e) {

            issues.add(error(ARG_TEMPLATE_PATH, safeMessage(e)));
        }

        if (!issues.isEmpty()) return ToolValidationResult.invalid(issues);

        return ToolValidationResult.valid();
    }


    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        ToolValidationResult validation = validate(request, context);

        if (validation.isInvalid()) {

            String message = validation.hasMessage() ? validation.getMessage() : "EPUB template approval validation failed.";

            return ToolResult.failure(TOOL_NAME, message)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .validationResult(validation)
                    .build();
        }

        try {

            String templatePath = request.requireStringArgument(ARG_TEMPLATE_PATH).trim();
            EpubProjectContext project = requireCurrentProject();
            String runId = resolveRunId(request, context);
            String projectId = resolveProjectId(project);

            AgentApproval approval = approvalService.create(
                    runId,
                    projectId,
                    AgentApprovalAction.PREFIX + TOOL_NAME,
                    APPROVAL_TITLE,
                    APPROVAL_MESSAGE,
                    templatePath,
                    EMPTY_CONTENT);

            return ToolResult.success(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .message("EPUB template application requires approval.")
                    .data("approvalRequired", true)
                    .data("approvalId", approval.getApprovalId())
                    .data("title", APPROVAL_TITLE)
                    .data("message", APPROVAL_MESSAGE)
                    .data("fileName", templatePath)
                    .data("projectName", project.getProjectName())
                    .data("projectRoot", normalize(project.getProjectRoot()).toString())
                    .build();

        } catch (Exception e) {

            return ToolResult.failure(TOOL_NAME, "Failed to create EPUB template approval: " + safeMessage(e), e)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .build();
        }
    }


    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = projectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");

        return project;
    }


    private String resolveRunId(ToolRequest request, ToolContext context) {

        if (context != null && context.getRequestId() != null && !context.getRequestId().isBlank()) return context.getRequestId().trim();
        if (request != null && request.getRequestId() != null && !request.getRequestId().isBlank()) return request.getRequestId().trim();

        throw new IllegalStateException("runId is not available from ToolContext or ToolRequest.");
    }


    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

        return normalize(project.getProjectRoot()).toString();
    }


    private void validateTemplateZip(Path templateZip) {

        Path path = normalize(templateZip);

        if (!Files.exists(path)) throw new IllegalStateException("Template ZIP does not exist: " + path);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Template path is not a file: " + path);
        if (!Files.isReadable(path)) throw new IllegalStateException("Template ZIP is not readable: " + path);
        if (path.getFileName() == null || !path.getFileName().toString().toLowerCase().endsWith(".zip")) throw new IllegalArgumentException("Template file must be a ZIP file: " + path);
    }


    private Map<String, Object> property(String type, String description) {

        Map<String, Object> property = new LinkedHashMap<>();

        property.put("type", type);
        property.put("description", description);

        return property;
    }


    private Map<String, Object> safeArguments(ToolRequest request) {

        if (request == null || request.getArguments() == null) return Collections.emptyMap();

        return request.getArguments();
    }


    private String readString(Map<String, Object> arguments, String key) {

        Object value = arguments.get(key);

        if (value == null) return null;
        if (value instanceof String text) return text;

        return String.valueOf(value);
    }


    private ToolIssue error(String field, String message) {

        return ToolIssue.builder()
                .severity(ToolIssueSeverity.ERROR)
                .field(field)
                .message(message)
                .build();
    }


    private Path normalize(Path path) {

        if (path == null) throw new IllegalStateException("Path must not be null.");

        return path.toAbsolutePath().normalize();
    }


    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error.";

        String message = throwable.getMessage();

        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}