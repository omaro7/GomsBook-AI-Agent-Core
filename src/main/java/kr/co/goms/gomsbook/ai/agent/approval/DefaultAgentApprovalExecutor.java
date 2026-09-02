/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.agent.approval;

import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlan;
import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolExecutor;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubBaseFilesTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectStructureTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectTool;

/**
 * 승인된 Agent 작업을 실제 실행하는 기본 Executor.
 */
public final class DefaultAgentApprovalExecutor
        implements AgentApprovalExecutor {

    private static final String ACTION_CREATE_EPUB_PROJECT =
            "create_epub_project";

    private final CurrentProjectProvider currentProjectProvider;

    private final CreateEpubProjectPlanService createEpubProjectPlanService;

    private final ToolExecutor toolExecutor;


    public DefaultAgentApprovalExecutor(
            CurrentProjectProvider currentProjectProvider,
            CreateEpubProjectPlanService createEpubProjectPlanService,
            ToolExecutor toolExecutor) {

        this.currentProjectProvider =
                Objects.requireNonNull(
                        currentProjectProvider,
                        "currentProjectProvider must not be null");

        this.createEpubProjectPlanService =
                Objects.requireNonNull(
                        createEpubProjectPlanService,
                        "createEpubProjectPlanService must not be null");

        this.toolExecutor =
                Objects.requireNonNull(
                        toolExecutor,
                        "toolExecutor must not be null");
    }


    @Override
    public void execute(
            AgentApproval approval) {

        Objects.requireNonNull(
                approval,
                "approval must not be null");


        if (!approval.isApproved()) {

            throw new IllegalStateException(
                    "Approval is not approved: "
                            + approval.getApprovalId()
                            + ", status="
                            + approval.getStatus());
        }


        String action =
                requireText(
                        approval.getAction(),
                        "approval.action");


        switch (action) {

            case ACTION_CREATE_EPUB_PROJECT ->
                    executeCreateEpubProject(
                            approval);

            default ->
                    executeLegacyAction(
                            approval);
        }
    }


    private void executeCreateEpubProject(
            AgentApproval approval) {

        /*
         * CreateEpubProjectPlanTool에서
         * AgentApproval.projectId 위치에 planId를 저장하였다.
         */
        String planId =
                requireText(
                        approval.getProjectId(),
                        "approval.projectId");


        CreateEpubProjectPlan plan =
                createEpubProjectPlanService.get(
                        planId);


        if (!plan.isWaitingForApproval()) {

            throw new IllegalStateException(
                    "EPUB project plan is not waiting for approval. "
                            + "planId="
                            + planId
                            + ", status="
                            + plan.getStatus());
        }


        /*
         * 사용자 승인을 Project Plan에도 반영한다.
         *
         * WAITING_PROJECT_APPROVAL
         *          ↓
         * PROJECT_APPROVED
         */
        createEpubProjectPlanService.approve(
                planId);


        ToolContext context =
                createToolContext(
                        approval);


        /*
         * 1. 프로젝트 루트 생성
         *
         * PROJECT_APPROVED
         *       ↓
         * CREATING
         */
        executeRequiredTool(
                CreateEpubProjectTool.TOOL_NAME,
                planId,
                context);


        /*
         * 2. EPUB 기본 디렉터리 구조 생성
         */
        executeRequiredTool(
                CreateEpubProjectStructureTool.TOOL_NAME,
                planId,
                context);


        /*
         * 3. EPUB 기본 파일 생성
         *
         * CREATING
         *    ↓
         * CREATED
         */
        executeRequiredTool(
                CreateEpubBaseFilesTool.TOOL_NAME,
                planId,
                context);


        CreateEpubProjectPlan completedPlan =
                createEpubProjectPlanService.get(
                        planId);


        if (!completedPlan.isCreated()) {

            throw new IllegalStateException(
                    "EPUB project creation did not reach CREATED status. "
                            + "planId="
                            + planId
                            + ", status="
                            + completedPlan.getStatus());
        }
    }


    private void executeRequiredTool(
            String toolName,
            String planId,
            ToolContext context) {

        ToolRequest request =
                ToolRequest.builder()
                        .toolName(
                                toolName)
                        .arguments(
                                Map.of(
                                        "planId",
                                        planId))
                        .build();


        ToolResult result =
                toolExecutor.execute(
                        request,
                        context);


        if (result == null) {

            throw new IllegalStateException(
                    "Tool returned null result: "
                            + toolName);
        }


        if (result.hasError()) {

            throw new IllegalStateException(
                    createToolErrorMessage(
                            toolName,
                            result));
        }
    }


    private ToolContext createToolContext(
            AgentApproval approval) {

        return ToolContext.builder()
                .requestId(
                        approval.getRunId())
                .sessionId(
                        approval.getRunId())
                .build();
    }


    private String createToolErrorMessage(
            String toolName,
            ToolResult result) {

        String message =
                result.getErrorMessage();


        if (message == null
                || message.isBlank()) {

            message =
                    result.getMessage();
        }


        if (message == null
                || message.isBlank()) {

            message =
                    "Unknown tool execution error.";
        }


        return "Approved EPUB project action failed. "
                + "tool="
                + toolName
                + ", message="
                + message;
    }


    /**
     * 기존 승인 액션을 처리한다.
     *
     * <p>
     * 기존 DefaultAgentApprovalExecutor에 다른 승인 처리 로직이
     * 있었다면 이 메서드 안으로 기존 코드를 이동한다.
     * </p>
     */
    private void executeLegacyAction(
            AgentApproval approval) {

        /*
         * 기존 승인 처리 코드가 있다면 이 위치에 유지한다.
         */

        throw new IllegalArgumentException(
                "Unsupported approval action: "
                        + approval.getAction());
    }


    private String requireText(
            String value,
            String name) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    name
                            + " must not be blank.");
        }

        return value.trim();
    }
}