/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.tool;

import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.tool.accessibility.ValidateAccessibilityTool;
import kr.co.goms.gomsbook.ai.tool.epub.generation.chapter.CreateBasicXhtmlTool;
import kr.co.goms.gomsbook.ai.tool.epub.inspect.InspectEpubTool;
import kr.co.goms.gomsbook.ai.tool.epub.navigation.ReadEpubNavigationTool;
import kr.co.goms.gomsbook.ai.tool.epub.validation.ValidateEpubStructureTool;
import kr.co.goms.gomsbook.ai.tool.epub.validation.ValidateEpubTool;
import kr.co.goms.gomsbook.ai.tool.image.InspectEpubImagesTool;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.event.AgentEventPublisher;

/**
 * Core 공통 Agent Tool을 등록하는 기본 구현체입니다.
 */
public final class DefaultAgentToolRegistrar implements AgentToolRegistrar {
	
	private final CurrentProjectProvider currentProjectProvider;
	private final PublishDirectoryProvider publishDirectoryProvider;
    private final EpubCheckValidator epubCheckValidator;
    private final AccessibilityValidator accessibilityValidator;
    private final AgentApprovalService approvalService;
    private final AgentEventPublisher eventPublisher;
    
    public DefaultAgentToolRegistrar(CurrentProjectProvider currentProjectProvider, PublishDirectoryProvider publishDirectoryProvider, EpubCheckValidator epubCheckValidator, 
    		AccessibilityValidator accessibilityValidator,
            AgentApprovalService approvalService,
            AgentEventPublisher eventPublisher) {
        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
        this.publishDirectoryProvider = Objects.requireNonNull(publishDirectoryProvider, "publishDirectoryProvider must not be null");
        this.epubCheckValidator = Objects.requireNonNull(epubCheckValidator, "epubCheckValidator must not be null");
        this.accessibilityValidator = Objects.requireNonNull(accessibilityValidator, "accessibilityValidator must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }


    @Override
    public void registerTools(ToolRegistry registry) {

        Objects.requireNonNull(registry, "registry must not be null");

        registerCoreTools(registry);
    }

    private void registerCoreTools(ToolRegistry registry) {

        registerIfAbsent(registry, new EchoTool());
        registerIfAbsent(registry, new InspectEpubTool());
        registerIfAbsent(registry, new ReadEpubNavigationTool(currentProjectProvider));
        registerIfAbsent(registry, new ValidateEpubStructureTool(currentProjectProvider, publishDirectoryProvider));
        registerIfAbsent(registry, new InspectEpubImagesTool(currentProjectProvider));

        registerIfAbsent(registry, new ValidateEpubTool(null, null, epubCheckValidator, null, publishDirectoryProvider));
        registerIfAbsent(registry, new ValidateAccessibilityTool(accessibilityValidator));
        registerIfAbsent(registry, new CreateBasicXhtmlTool(currentProjectProvider, approvalService, eventPublisher));
        
    }

    private void registerIfAbsent(ToolRegistry registry, AgentTool tool) {

        Objects.requireNonNull(tool, "tool must not be null");

        if (registry.contains(tool.getName())) return;

        registry.register(tool);
    }
}