/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolExecutor;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.epub.project.ApplyEpubTemplateTool;

/**
 * 승인된 EPUB 템플릿 적용 작업을 실행하는 Handler.
 */
public final class ApplyEpubTemplateApprovalHandler implements AgentApprovalHandler {

    private final ToolExecutor toolExecutor;


    public ApplyEpubTemplateApprovalHandler(ToolExecutor toolExecutor) {

        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor must not be null");
    }


    @Override
    public void execute(AgentApproval approval) {

        Objects.requireNonNull(approval, "approval must not be null");

        String templatePath = requireText(approval.getFileName(), "approval.fileName");
        ToolContext context = createToolContext(approval);

        ToolRequest request = ToolRequest.builder()
                .toolName(ApplyEpubTemplateTool.TOOL_NAME)
                .arguments(Map.of("templatePath", templatePath))
                .build();

        ToolResult result = toolExecutor.execute(request, context);

        if (result == null) {

            throw new IllegalStateException("Tool returned null result: " + ApplyEpubTemplateTool.TOOL_NAME);
        }

        if (result.hasError()) {

            throw new IllegalStateException(createToolErrorMessage(result));
        }
    }


    private ToolContext createToolContext(AgentApproval approval) {

        return ToolContext.builder()
                .requestId(approval.getRunId())
                .sessionId(approval.getRunId())
                .build();
    }


    private String createToolErrorMessage(ToolResult result) {

        String message = result.getErrorMessage();

        if (message == null || message.isBlank()) message = result.getMessage();

        if (message == null || message.isBlank()) message = "Unknown tool execution error.";

        return "Approved EPUB template action failed. tool=" + ApplyEpubTemplateTool.TOOL_NAME + ", message=" + message;
    }


    private String requireText(String value, String name) {

        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");

        return value.trim();
    }
}