/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalAction;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlan;
import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 신규 EPUB 프로젝트 생성 Plan을 생성하는 Tool.
 *
 * <p>
 * 이 Tool은 실제 프로젝트 디렉터리나 파일을 생성하지 않는다.
 * 프로젝트명과 폴더명을 기반으로 프로젝트 생성 Plan만 생성하고
 * 사용자 승인 대기 상태로 저장한다.
 * </p>
 *
 * <p>
 * 목차, Part, Chapter, Author, Copyright, Quiz 등의
 * 책 콘텐츠 생성 계획은 이 Tool에서 처리하지 않는다.
 * 해당 영역은 epub.generation.plan에서 처리한다.
 * </p>
 */
public final class CreateEpubProjectPlanTool
        implements AgentTool {

    public static final String TOOL_NAME =
            "create_epub_project_plan";

    private static final String DESCRIPTION =
            "Creates a proposal for a new EPUB project. "
                    + "Use this tool immediately when the user asks to create "
                    + "a new EPUB project. "
                    + "Only determine the project name and project folder name. "
                    + "Do not ask for a table of contents, chapters, book content, "
                    + "images, fonts, author information, copyright information, "
                    + "quiz information, or other publication resources before "
                    + "using this tool. "
                    + "The generated project plan requires user approval before "
                    + "any directory or file is created.";

    private final CreateEpubProjectPlanService planService;

    private final AgentApprovalService approvalService;


    public CreateEpubProjectPlanTool(
            CreateEpubProjectPlanService planService,
            AgentApprovalService approvalService) {

        this.planService =
                Objects.requireNonNull(
                        planService,
                        "planService must not be null");

        this.approvalService =
                Objects.requireNonNull(
                        approvalService,
                        "approvalService must not be null");
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

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "projectName",
                property(
                        "string",
                        "Name of the new EPUB project. "
                                + "Use the project name requested by the user."));

        properties.put(
                "folderName",
                property(
                        "string",
                        "Recommended project folder name. "
                                + "Use a short lowercase kebab-case or "
                                + "snake_case English folder name without "
                                + "path separators."));


        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object");

        schema.put(
                "properties",
                properties);

        schema.put(
                "required",
                List.of(
                        "projectName",
                        "folderName"));

        schema.put(
                "additionalProperties",
                false);

        return Collections.unmodifiableMap(
                schema);
    }


    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        List<ToolIssue> issues =
                new ArrayList<>();


        if (request == null) {

            issues.add(
                    error(
                            "request",
                            "Tool request must not be null."));

            return ToolValidationResult.invalid(
                    issues);
        }


        if (context == null) {

            issues.add(
                    error(
                            "context",
                            "Tool context must not be null."));

            return ToolValidationResult.invalid(
                    issues);
        }


        if (!TOOL_NAME.equals(
                request.getToolName())) {

            issues.add(
                    error(
                            "toolName",
                            "Invalid tool name: "
                                    + request.getToolName()));
        }


        Map<String, Object> arguments =
                safeArguments(
                        request);


        validateRequiredString(
                arguments,
                "projectName",
                issues);

        validateRequiredString(
                arguments,
                "folderName",
                issues);


        String folderName =
                readString(
                        arguments,
                        "folderName");


        if (hasText(
                folderName)) {

            validateFolderName(
                    folderName,
                    issues);
        }


        if (!issues.isEmpty()) {

            return ToolValidationResult.invalid(
                    issues);
        }

        return ToolValidationResult.valid();
    }


    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        ToolValidationResult validation =
                validate(
                        request,
                        context);


        if (!validation.isValid()) {

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME)
                    .status(
                            ToolStatus.FAILED)
                    .message(
                            "Invalid EPUB project plan request.")
                    .issues(
                            validation.getIssues())
                    .build();
        }


        try {

            Map<String, Object> arguments =
                    safeArguments(
                            request);


            String projectName =
                    readRequiredString(
                            arguments,
                            "projectName");

            String folderName =
                    readRequiredString(
                            arguments,
                            "folderName");


            /*
             * 실제 프로젝트 디렉터리는 생성하지 않는다.
             *
             * 여기서는 WAITING_PROJECT_APPROVAL 상태의
             * 프로젝트 생성 Plan만 생성하고 Store에 저장한다.
             */
            CreateEpubProjectPlan plan =
                    planService.create(
                            projectName,
                            folderName);


            String runId =
                    requireRunId(
                            context);


            String approvalTitle =
                    "새 EPUB 프로젝트 생성";

            String approvalMessage =
                    createApprovalMessage(
                            plan);

            String approvalContent =
                    createApprovalContent(
                            plan);


            /*
             * projectId 자리는 아직 실제 프로젝트가 생성되기 전이므로
             * CreateEpubProjectPlan의 planId를 사용한다.
             */
            AgentApproval approval =
                    approvalService.create(
                            runId,
                            plan.getPlanId(),
                            AgentApprovalAction.of(CreateEpubProjectTool.TOOL_NAME),
                            approvalTitle,
                            approvalMessage,
                            plan.getFolderName(),
                            approvalContent);


            Map<String, Object> output =
                    createOutput(
                            plan,
                            approval);


            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME)
                    .status(
                            ToolStatus.SUCCESS)
                    .message(
                            "EPUB project creation plan was created "
                                    + "and is waiting for user approval.")
                    .data(
                            output)
                    .build();


        } catch (IllegalArgumentException exception) {

            return failure(
                    "Invalid EPUB project plan request: "
                            + safeMessage(
                                    exception),
                    exception);

        } catch (IllegalStateException exception) {

            return failure(
                    "Failed to create EPUB project plan: "
                            + safeMessage(
                                    exception),
                    exception);

        } catch (RuntimeException exception) {

            return failure(
                    "Unexpected EPUB project plan failure: "
                            + safeMessage(
                                    exception),
                    exception);
        }
    }


    private Map<String, Object> createOutput(
            CreateEpubProjectPlan plan,
            AgentApproval approval) {

        Map<String, Object> output =
                new LinkedHashMap<>();


        output.put(
                "type",
                "EPUB_PROJECT_PLAN");

        output.put(
                "action",
                "CREATE");

        output.put(
                "planId",
                plan.getPlanId());

        output.put(
                "projectName",
                plan.getProjectName());

        output.put(
                "folderName",
                plan.getFolderName());

        output.put(
                "language",
                plan.getLanguage());

        output.put(
                "epubVersion",
                plan.getEpubVersion());

        output.put(
                "status",
                plan.getStatus()
                        .name());


        /*
         * AgentRunService가 APPROVAL_REQUIRED 이벤트를
         * 생성하기 위해 필요한 필드.
         */
        output.put(
                "approvalRequired",
                true);

        output.put(
                "approvalId",
                approval.getApprovalId());

        output.put(
                "title",
                approval.getTitle());

        output.put(
                "message",
                approval.getMessage());

        output.put(
                "fileName",
                plan.getFolderName());

        output.put(
                "content",
                approval.getContent());


        output.put(
                "approved",
                false);


        return Collections.unmodifiableMap(
                output);
    }


    private String createApprovalMessage(
            CreateEpubProjectPlan plan) {

        return "새 EPUB 프로젝트를 생성하시겠습니까?"
                + " 프로젝트명="
                + plan.getProjectName()
                + ", 폴더명="
                + plan.getFolderName();
    }


    private String createApprovalContent(
            CreateEpubProjectPlan plan) {

        return """
                새 EPUB 프로젝트를 생성합니다.

                프로젝트명 : %s
                폴더명     : %s
                프로젝트 유형 : EPUB %s
                언어       : %s

                승인 후 프로젝트 디렉터리와 기본 EPUB 구조를 생성합니다.
                """.formatted(
                        plan.getProjectName(),
                        plan.getFolderName(),
                        plan.getEpubVersion(),
                        plan.getLanguage());
    }


    private String requireRunId(
            ToolContext context) {

        if (context == null
                || !context.hasRequestId()) {

            throw new IllegalStateException(
                    "ToolContext requestId is required "
                            + "for project approval.");
        }


        String requestId =
                context.getRequestId();


        if (requestId == null
                || requestId.isBlank()) {

            throw new IllegalStateException(
                    "ToolContext requestId must not be blank.");
        }


        return requestId.trim();
    }


    private void validateRequiredString(
            Map<String, Object> arguments,
            String key,
            List<ToolIssue> issues) {

        Object value =
                arguments.get(
                        key);


        if (!(value instanceof String)) {

            issues.add(
                    error(
                            key,
                            key
                                    + " must be a string value."));

            return;
        }


        if (((String) value)
                .isBlank()) {

            issues.add(
                    error(
                            key,
                            key
                                    + " must not be blank."));
        }
    }


    private void validateFolderName(
            String folderName,
            List<ToolIssue> issues) {

        String value =
                folderName.trim();


        if (".".equals(
                value)
                || "..".equals(
                        value)) {

            issues.add(
                    error(
                            "folderName",
                            "folderName must not be '.' or '..'."));

            return;
        }


        if (value.contains("/")
                || value.contains("\\")) {

            issues.add(
                    error(
                            "folderName",
                            "folderName must not contain path separators."));
        }


        if (value.contains(":")
                || value.contains("*")
                || value.contains("?")
                || value.contains("\"")
                || value.contains("<")
                || value.contains(">")
                || value.contains("|")) {

            issues.add(
                    error(
                            "folderName",
                            "folderName contains invalid file name characters."));
        }
    }


    private Map<String, Object> property(
            String type,
            String description) {

        Map<String, Object> property =
                new LinkedHashMap<>();

        property.put(
                "type",
                type);

        property.put(
                "description",
                description);

        return property;
    }


    private Map<String, Object> safeArguments(
            ToolRequest request) {

        if (request == null
                || request.getArguments() == null) {

            return Collections.emptyMap();
        }

        return request.getArguments();
    }


    private String readRequiredString(
            Map<String, Object> arguments,
            String key) {

        Object value =
                arguments.get(
                        key);


        if (!(value instanceof String text)
                || text.isBlank()) {

            throw new IllegalArgumentException(
                    key
                            + " must not be blank.");
        }


        return text.trim();
    }


    private String readString(
            Map<String, Object> arguments,
            String key) {

        Object value =
                arguments.get(
                        key);


        if (value == null) {

            return null;
        }


        if (value instanceof String text) {

            return text;
        }


        return String.valueOf(
                value);
    }


    private ToolIssue error(
            String field,
            String message) {

        return ToolIssue.builder()
                .severity(
                        ToolIssueSeverity.ERROR)
                .field(
                        field)
                .message(
                        message)
                .build();
    }


    private ToolResult failure(
            String message,
            RuntimeException exception) {

        return ToolResult.builder()
                .toolName(
                        TOOL_NAME)
                .status(
                        ToolStatus.FAILED)
                .message(
                        message)
                .cause(
                        exception)
                .build();
    }


    private boolean hasText(
            String value) {

        return value != null
                && !value.trim()
                        .isEmpty();
    }


    private String safeMessage(
            Throwable throwable) {

        if (throwable == null
                || !hasText(
                        throwable.getMessage())) {

            return "Unknown error";
        }

        return throwable.getMessage();
    }
}