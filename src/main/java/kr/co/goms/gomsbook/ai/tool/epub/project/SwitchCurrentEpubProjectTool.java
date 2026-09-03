/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.tool.epub.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlan;
import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectStore;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 생성된 EPUB 프로젝트를 현재 프로젝트로 전환하는 Tool.
 *
 * <p>
 * CreateEpubProjectPlan의 folderName을 기준으로 실제 프로젝트
 * 경로를 결정하고 CurrentProjectStore의 현재 프로젝트를 변경한다.
 * </p>
 */
public final class SwitchCurrentEpubProjectTool implements AgentTool {

    public static final String TOOL_NAME = "switch_current_epub_project";

    private static final String DESCRIPTION = "Switches the current GomsBook EPUB project to a newly created EPUB project.";

    private static final String ARG_PLAN_ID = "planId";
    private static final String OEBPS_DIRECTORY = "OEBPS";
    private static final String PACKAGE_FILE = "content.opf";

    private final CurrentProjectStore currentProjectStore;
    private final CreateEpubProjectPlanService planService;
    private final Path projectsRoot;


    public SwitchCurrentEpubProjectTool(CurrentProjectStore currentProjectStore, CreateEpubProjectPlanService planService, Path projectsRoot) {

        this.currentProjectStore = Objects.requireNonNull(currentProjectStore, "currentProjectStore must not be null");
        this.planService = Objects.requireNonNull(planService, "planService must not be null");
        this.projectsRoot = Objects.requireNonNull(projectsRoot, "projectsRoot must not be null").toAbsolutePath().normalize();
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

        return Map.of(
                "type", "object",
                "properties", Map.of(
                        ARG_PLAN_ID, Map.of(
                                "type", "string",
                                "description", "EPUB project creation plan ID."
                        )
                ),
                "required", List.of(ARG_PLAN_ID),
                "additionalProperties", false
        );
    }


    @Override
    public ToolValidationResult validate(ToolRequest request, ToolContext context) {

        if (request == null) {

            return ToolValidationResult.invalid("Tool request must not be null.");
        }

        if (!TOOL_NAME.equals(request.getToolName())) {

            return ToolValidationResult.invalid("Invalid tool name: " + request.getToolName());
        }

        try {

            String planId = request.requireStringArgument(ARG_PLAN_ID);
            CreateEpubProjectPlan plan = planService.get(planId);

            if (!plan.isCreated()) {

                return ToolValidationResult.invalid("EPUB project must be CREATED before switching. planId=" + planId + ", status=" + plan.getStatus());
            }

            Path projectRoot = resolveProjectRoot(plan);

            if (!Files.isDirectory(projectRoot)) {

                return ToolValidationResult.invalid("EPUB project root does not exist: " + projectRoot);
            }

            Path packageDocument = resolvePackageDocument(projectRoot);

            if (!Files.isRegularFile(packageDocument)) {

                return ToolValidationResult.invalid("EPUB package document does not exist: " + packageDocument);
            }

            return ToolValidationResult.valid();

        } catch (Exception e) {

            return ToolValidationResult.invalid(e.getMessage());
        }
    }


    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        ToolValidationResult validation = validate(request, context);

        if (validation.isInvalid()) {

            String message = validation.hasMessage() ? validation.getMessage() : "Current EPUB project switch validation failed.";

            return ToolResult.failure(TOOL_NAME, message)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .validationResult(validation)
                    .build();
        }

        try {

            String planId = request.requireStringArgument(ARG_PLAN_ID);
            CreateEpubProjectPlan plan = planService.get(planId);
            Path projectRoot = resolveProjectRoot(plan);
            Path previousProjectRoot = currentProjectStore.getCurrentProjectRoot();

            currentProjectStore.setCurrentProjectRoot(projectRoot);

            return ToolResult.success(TOOL_NAME)
                    .requestId(request.getRequestId())
                    .toolCallId(request.getToolCallId())
                    .message("Current EPUB project switched successfully.")
                    .data("planId", planId)
                    .data("projectName", plan.getProjectName())
                    .data("folderName", plan.getFolderName())
                    .data("projectRoot", projectRoot.toString())
                    .data("previousProjectRoot", previousProjectRoot != null ? previousProjectRoot.toString() : null)
                    .build();

        } catch (Exception e) {

            return ToolResult.failure(TOOL_NAME, "Failed to switch current EPUB project: " + e.getMessage(), e)
                    .requestId(request != null ? request.getRequestId() : null)
                    .toolCallId(request != null ? request.getToolCallId() : null)
                    .build();
        }
    }


    private Path resolveProjectRoot(CreateEpubProjectPlan plan) {

        Path projectRoot = projectsRoot.resolve(plan.getFolderName()).toAbsolutePath().normalize();

        if (!projectRoot.startsWith(projectsRoot)) {

            throw new IllegalStateException("EPUB project path escapes projects root: " + projectRoot);
        }

        return projectRoot;
    }


    private Path resolvePackageDocument(Path projectRoot) {

        return projectRoot.resolve(OEBPS_DIRECTORY).resolve(PACKAGE_FILE).normalize();
    }
}