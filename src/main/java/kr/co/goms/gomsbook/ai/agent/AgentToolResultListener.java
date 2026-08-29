/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.agent;

import kr.co.goms.gomsbook.ai.tool.ToolResult;

public interface AgentToolResultListener {

    void onToolResult(ToolResult result);
}