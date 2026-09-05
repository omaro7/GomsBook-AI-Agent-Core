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

import kr.co.goms.gomsbook.ai.epub.plan.project.CreateEpubProjectPlan;
import kr.co.goms.gomsbook.ai.epub.plan.project.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 승인된 신규 EPUB 프로젝트 Plan을 기반으로
 * 실제 프로젝트 루트 디렉터리를 생성하는 Tool.
 *
 * <p>
 * 이 Tool은 프로젝트 루트 디렉터리만 생성한다.
 * META-INF, OEBPS, Text, Styles 등의 EPUB 하위 구조는
 * CreateEpubProjectStructureTool에서 생성한다.
 * </p>
 */
public final class CreateEpubProjectTool
        implements AgentTool {

    public static final String TOOL_NAME =
            "create_epub_project";

    private static final String DESCRIPTION =
            "Creates the root directory for an approved EPUB project creation plan.";

    private final CreateEpubProjectPlanService planService;

    private final Path projectsRoot;


    public CreateEpubProjectTool(
            CreateEpubProjectPlanService planService,
            Path projectsRoot) {

        this.planService =
                Objects.requireNonNull(
                        planService,
                        "planService must not be null");

        this.projectsRoot =
                Objects.requireNonNull(
                        projectsRoot,
                        "projectsRoot must not be null")
                        .toAbsolutePath()
                        .normalize();
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
                "planId",
                property(
                        "string",
                        "Approved EPUB project creation plan identifier."));


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
                        "planId"));

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


        Object planIdValue =
                arguments.get(
                        "planId");

        if (!(planIdValue instanceof String)) {

            issues.add(
                    error(
                            "planId",
                            "planId must be a string value."));

        } else {

            String planId =
                    ((String) planIdValue)
                            .trim();

            if (planId.isEmpty()) {

                issues.add(
                        error(
                                "planId",
                                "planId must not be blank."));
            }
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
                            "Invalid EPUB project creation request.")
                    .issues(
                            validation.getIssues())
                    .build();
        }


        try {

            Map<String, Object> arguments =
                    safeArguments(
                            request);

            String planId =
                    readRequiredString(
                            arguments,
                            "planId");


            CreateEpubProjectPlan plan =
                    planService.get(
                            planId);


            requireApproved(
                    plan);


            Path projectPath =
                    resolveProjectPath(
                            plan.getFolderName());


            validateProjectPath(
                    projectPath);


            Files.createDirectory(
                    projectPath);


            /*
             * 프로젝트 루트 생성에 성공했으므로
             * 이후 EPUB 기본 디렉터리 및 파일 생성 단계로 진입한다.
             */
            planService.markCreating(
                    planId);


            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME)
                    .status(
                            ToolStatus.SUCCESS)
                    .message(
                            "EPUB project root directory was created successfully.")
                    .data(
                            createOutput(
                                    planService.get(
                                            planId),
                                    projectPath))
                    .build();


        } catch (IllegalArgumentException exception) {

            return failure(
                    "Invalid EPUB project creation request: "
                            + safeMessage(
                                    exception),
                    exception);

        } catch (IllegalStateException exception) {

            return failure(
                    "EPUB project creation failed: "
                            + safeMessage(
                                    exception),
                    exception);

        } catch (Exception exception) {

            return failure(
                    "Failed to create EPUB project directory: "
                            + safeMessage(
                                    exception),
                    exception);
        }
    }


    private void requireApproved(
            CreateEpubProjectPlan plan) {

        if (plan == null) {

            throw new IllegalStateException(
                    "EPUB project creation plan is not available.");
        }


        if (plan.getStatus()
                != CreateEpubProjectPlan.Status.PROJECT_APPROVED) {

            throw new IllegalStateException(
                    "EPUB project creation plan is not approved. "
                            + "planId="
                            + plan.getPlanId()
                            + ", status="
                            + plan.getStatus());
        }
    }


    private Path resolveProjectPath(
            String folderName) {

        String normalizedFolderName =
                requireFolderName(
                        folderName);


        Path projectPath =
                projectsRoot
                        .resolve(
                                normalizedFolderName)
                        .toAbsolutePath()
                        .normalize();


        if (!projectPath.startsWith(
                projectsRoot)) {

            throw new IllegalArgumentException(
                    "Project path escapes projects root: "
                            + projectPath);
        }


        return projectPath;
    }


    private void validateProjectPath(
            Path projectPath) {

        if (!Files.exists(
                projectsRoot)) {

            throw new IllegalStateException(
                    "EPUB projects root does not exist: "
                            + projectsRoot);
        }


        if (!Files.isDirectory(
                projectsRoot)) {

            throw new IllegalStateException(
                    "EPUB projects root is not a directory: "
                            + projectsRoot);
        }


        if (Files.exists(
                projectPath)) {

            throw new IllegalStateException(
                    "EPUB project already exists: "
                            + projectPath);
        }
    }


    private String requireFolderName(
            String folderName) {

        if (folderName == null
                || folderName.isBlank()) {

            throw new IllegalArgumentException(
                    "folderName must not be blank.");
        }


        String normalized =
                folderName.trim();


        if (".".equals(
                normalized)
                || "..".equals(
                        normalized)) {

            throw new IllegalArgumentException(
                    "Invalid project folder name: "
                            + normalized);
        }


        if (normalized.contains("/")
                || normalized.contains("\\")) {

            throw new IllegalArgumentException(
                    "Project folder name must not contain path separators: "
                            + normalized);
        }


        if (containsInvalidFileNameCharacter(
                normalized)) {

            throw new IllegalArgumentException(
                    "Project folder name contains invalid characters: "
                            + normalized);
        }


        return normalized;
    }


    private boolean containsInvalidFileNameCharacter(
            String value) {

        return value.contains(":")
                || value.contains("*")
                || value.contains("?")
                || value.contains("\"")
                || value.contains("<")
                || value.contains(">")
                || value.contains("|");
    }


    private Map<String, Object> createOutput(
            CreateEpubProjectPlan plan,
            Path projectPath) {

        Map<String, Object> output =
                new LinkedHashMap<>();


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
                "projectPath",
                projectPath.toString());

        output.put(
                "planStatus",
                plan.getStatus()
                        .name());

        output.put(
                "created",
                true);


        return Collections.unmodifiableMap(
                output);
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


    private ToolResult failure(
            String message,
            Exception exception) {

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


    private String safeMessage(
            Throwable throwable) {

        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage()
                        .isBlank()) {

            return "Unknown error";
        }

        return throwable.getMessage();
    }
}