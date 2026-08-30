/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.agent.event;

public enum AgentEventType {

    STARTED,

    THINKING,

    TOOL_CALLING,

    TOOL_RESULT,

    MESSAGE,

    APPROVAL_REQUIRED,

    COMPLETED,

    ERROR
}