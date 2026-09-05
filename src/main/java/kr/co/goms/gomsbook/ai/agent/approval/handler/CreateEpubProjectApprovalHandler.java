/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.epub.plan.project.CreateEpubProjectPlan;
import kr.co.goms.gomsbook.ai.epub.plan.project.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolExecutor;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubBaseFilesTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectStructureTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.SwitchCurrentEpubProjectTool;

/**
 * 승인된 EPUB 프로젝트 생성 작업을 실행하는 Handler.
 * 현재 프로젝트까지 변경합니다.
 */
public final class CreateEpubProjectApprovalHandler implements AgentApprovalHandler {

    private final CreateEpubProjectPlanService planService;
    private final ToolExecutor toolExecutor;


    public CreateEpubProjectApprovalHandler(CreateEpubProjectPlanService planService, ToolExecutor toolExecutor) {

        this.planService = Objects.requireNonNull(planService, "planService must not be null");
        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor must not be null");
    }


    @Override
    public void execute(AgentApproval approval) {

        Objects.requireNonNull(approval, "approval must not be null");

        String planId = requireText(approval.getProjectId(), "approval.projectId");
        CreateEpubProjectPlan plan = planService.get(planId);

        if (!plan.isWaitingForApproval()) {

            throw new IllegalStateException("EPUB project plan is not waiting for approval. planId=" + planId + ", status=" + plan.getStatus());
        }

        /*
         * WAITING_PROJECT_APPROVAL
         *          ↓
         * PROJECT_APPROVED
         */
        planService.approve(planId);

        ToolContext context = createToolContext(approval);

        /*
         * 1. 프로젝트 루트 생성
         *
         * PROJECT_APPROVED
         *       ↓
         * CREATING
         */
        executeRequiredTool(CreateEpubProjectTool.TOOL_NAME, planId, context);

        /*
         * 2. EPUB 기본 디렉터리 구조 생성
         */
        executeRequiredTool(CreateEpubProjectStructureTool.TOOL_NAME, planId, context);

        /*
         * 3. EPUB 기본 파일 생성
         *
         * CREATING
         *    ↓
         * CREATED
         */
        executeRequiredTool(CreateEpubBaseFilesTool.TOOL_NAME, planId, context);

        CreateEpubProjectPlan completedPlan = planService.get(planId);

        if (!completedPlan.isCreated()) {

            throw new IllegalStateException("EPUB project creation did not reach CREATED status. planId=" + planId + ", status=" + completedPlan.getStatus());
        }

        /*
         * 4. 생성된 EPUB 프로젝트를 현재 프로젝트로 전환
         */
        executeRequiredTool(SwitchCurrentEpubProjectTool.TOOL_NAME, planId, context);
    }


    private void executeRequiredTool(String toolName, String planId, ToolContext context) {

        ToolRequest request = ToolRequest.builder()
                .toolName(toolName)
                .arguments(Map.of("planId", planId))
                .build();

        ToolResult result = toolExecutor.execute(request, context);

        if (result == null) {

            throw new IllegalStateException("Tool returned null result: " + toolName);
        }

        if (result.hasError()) {

            throw new IllegalStateException(createToolErrorMessage(toolName, result));
        }
    }


    private ToolContext createToolContext(AgentApproval approval) {

        return ToolContext.builder()
                .requestId(approval.getRunId())
                .sessionId(approval.getRunId())
                .build();
    }


    private String createToolErrorMessage(String toolName, ToolResult result) {

        String message = result.getErrorMessage();

        if (message == null || message.isBlank()) {

            message = result.getMessage();
        }

        if (message == null || message.isBlank()) {

            message = "Unknown tool execution error.";
        }

        return "Approved EPUB project action failed. tool=" + toolName + ", message=" + message;
    }


    private String requireText(String value, String name) {

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(name + " must not be blank.");
        }

        return value.trim();
    }
}