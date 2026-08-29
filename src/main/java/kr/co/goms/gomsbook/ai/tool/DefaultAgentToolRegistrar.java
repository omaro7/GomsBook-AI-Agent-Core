/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.tool;

import java.util.Objects;

/**
 * Core 공통 Agent Tool을 등록하는 기본 구현체입니다.
 */
public final class DefaultAgentToolRegistrar implements AgentToolRegistrar {

    @Override
    public void registerTools(ToolRegistry registry) {

        Objects.requireNonNull(registry, "registry must not be null");

        registerCoreTools(registry);
    }

    private void registerCoreTools(ToolRegistry registry) {

        registerIfAbsent(registry, new EchoTool());
    }

    private void registerIfAbsent(ToolRegistry registry, AgentTool tool) {

        Objects.requireNonNull(tool, "tool must not be null");

        if (registry.contains(tool.getName())) return;

        registry.register(tool);
    }
}