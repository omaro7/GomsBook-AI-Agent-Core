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
 * 신규 EPUB 프로젝트의 기본 디렉터리 구조를 생성하는 Tool.
 *
 * <p>
 * CreateEpubProjectTool에서 프로젝트 루트 디렉터리가 생성되고
 * Plan 상태가 CREATING으로 변경된 이후 실행한다.
 * </p>
 *
 * <p>
 * 생성 구조:
 * </p>
 *
 * <pre>
 * project/
 * ├─ META-INF/
 * └─ OEBPS/
 *    ├─ Text/
 *    ├─ Styles/
 *    ├─ Images/
 *    ├─ Fonts/
 *    └─ Scripts/
 * </pre>
 *
 * <p>
 * container.xml, content.opf, nav.xhtml, style1.css,
 * quiz.js 등의 파일 생성은 담당하지 않는다.
 * </p>
 */
public final class CreateEpubProjectStructureTool
        implements AgentTool {

    public static final String TOOL_NAME =
            "create_epub_project_structure";

    private static final String DESCRIPTION =
            "Creates the standard EPUB directory structure "
                    + "for a project currently being created.";

    private final CreateEpubProjectPlanService planService;

    private final Path projectsRoot;


    public CreateEpubProjectStructureTool(
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
                        "EPUB project creation plan identifier "
                                + "whose status is CREATING."));


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
                            "Invalid EPUB project structure request.")
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


            requireCreating(
                    plan);


            Path projectPath =
                    resolveProjectPath(
                            plan.getFolderName());


            validateProjectRoot(
                    projectPath);


            List<String> createdDirectories =
                    createProjectStructure(
                            projectPath);


            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME)
                    .status(
                            ToolStatus.SUCCESS)
                    .message(
                            "EPUB project directory structure was created successfully.")
                    .data(
                            createOutput(
                                    plan,
                                    projectPath,
                                    createdDirectories))
                    .build();


        } catch (IllegalArgumentException exception) {

            return failure(
                    "Invalid EPUB project structure request: "
                            + safeMessage(
                                    exception),
                    exception);

        } catch (IllegalStateException exception) {

            return failure(
                    "EPUB project structure creation failed: "
                            + safeMessage(
                                    exception),
                    exception);

        } catch (Exception exception) {

            return failure(
                    "Failed to create EPUB project structure: "
                            + safeMessage(
                                    exception),
                    exception);
        }
    }


    private void requireCreating(
            CreateEpubProjectPlan plan) {

        if (plan == null) {

            throw new IllegalStateException(
                    "EPUB project creation plan is not available.");
        }


        if (plan.getStatus()
                != CreateEpubProjectPlan.Status.CREATING) {

            throw new IllegalStateException(
                    "EPUB project creation plan is not in CREATING status. "
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


    private void validateProjectRoot(
            Path projectPath) {

        if (!Files.exists(
                projectPath)) {

            throw new IllegalStateException(
                    "EPUB project root does not exist: "
                            + projectPath);
        }


        if (!Files.isDirectory(
                projectPath)) {

            throw new IllegalStateException(
                    "EPUB project root is not a directory: "
                            + projectPath);
        }
    }


    private List<String> createProjectStructure(
            Path projectPath) {

        List<String> createdDirectories =
                new ArrayList<>();


        createDirectory(
                projectPath.resolve(
                        "META-INF"),
                createdDirectories);


        Path oebpsPath =
                projectPath.resolve(
                        "OEBPS");


        createDirectory(
                oebpsPath,
                createdDirectories);

        createDirectory(
                oebpsPath.resolve(
                        "Text"),
                createdDirectories);

        createDirectory(
                oebpsPath.resolve(
                        "Styles"),
                createdDirectories);

        createDirectory(
                oebpsPath.resolve(
                        "Images"),
                createdDirectories);

        createDirectory(
                oebpsPath.resolve(
                        "Fonts"),
                createdDirectories);

        createDirectory(
                oebpsPath.resolve(
                        "Scripts"),
                createdDirectories);


        return List.copyOf(
                createdDirectories);
    }


    private void createDirectory(
            Path directory,
            List<String> createdDirectories) {

        try {

            boolean existed =
                    Files.exists(
                            directory);


            Files.createDirectories(
                    directory);


            if (!existed) {

                createdDirectories.add(
                        directory.toString());
            }


        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to create EPUB project directory: "
                            + directory,
                    exception);
        }
    }


    private Map<String, Object> createOutput(
            CreateEpubProjectPlan plan,
            Path projectPath,
            List<String> createdDirectories) {

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
                "projectPath",
                projectPath.toString());

        output.put(
                "planStatus",
                plan.getStatus()
                        .name());

        output.put(
                "createdDirectoryCount",
                createdDirectories.size());

        output.put(
                "createdDirectories",
                createdDirectories);

        output.put(
                "structureCreated",
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


        return normalized;
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