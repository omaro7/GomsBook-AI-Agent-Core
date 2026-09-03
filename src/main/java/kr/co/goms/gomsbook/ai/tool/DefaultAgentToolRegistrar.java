/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.tool;

import java.nio.file.Path;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectStore;
import kr.co.goms.gomsbook.ai.tool.accessibility.ValidateAccessibilityTool;
import kr.co.goms.gomsbook.ai.tool.epub.generation.chapter.CreateBasicXhtmlTool;
import kr.co.goms.gomsbook.ai.tool.epub.inspect.InspectCurrentProjectTool;
import kr.co.goms.gomsbook.ai.tool.epub.inspect.InspectEpubTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubFontManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubImageManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubJsManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubStyleManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubTextManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.ReadEpubManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.metadata.ReadEpubMetadataTool;
import kr.co.goms.gomsbook.ai.tool.epub.navigation.ReadEpubNavigationTool;
import kr.co.goms.gomsbook.ai.tool.epub.pkg.ReadEpubPackageTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.ApplyEpubTemplateTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubBaseFilesTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectPlanTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectStructureTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.SwitchCurrentEpubProjectTool;
import kr.co.goms.gomsbook.ai.tool.epub.spine.ReadEpubSpineTool;
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
    
    private final CurrentProjectStore currentProjectStore;
    private final CreateEpubProjectPlanService createEpubProjectPlanService;
    private final Path epubProjectsRoot;
    
    public DefaultAgentToolRegistrar(CurrentProjectProvider currentProjectProvider, PublishDirectoryProvider publishDirectoryProvider, EpubCheckValidator epubCheckValidator, 
    		AccessibilityValidator accessibilityValidator,
            AgentApprovalService approvalService,
            AgentEventPublisher eventPublisher,
            CurrentProjectStore currentProjectStore, CreateEpubProjectPlanService createEpubProjectPlanService, Path epubProjectsRoot) {
        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
        this.publishDirectoryProvider = Objects.requireNonNull(publishDirectoryProvider, "publishDirectoryProvider must not be null");
        this.epubCheckValidator = Objects.requireNonNull(epubCheckValidator, "epubCheckValidator must not be null");
        this.accessibilityValidator = Objects.requireNonNull(accessibilityValidator, "accessibilityValidator must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.currentProjectStore = Objects.requireNonNull(currentProjectStore, "currentProjectStore must not be null");
        this.createEpubProjectPlanService = Objects.requireNonNull(createEpubProjectPlanService, "createEpubProjectPlanService must not be null");
        this.epubProjectsRoot = Objects.requireNonNull(epubProjectsRoot, "epubProjectsRoot must not be null").toAbsolutePath().normalize();
     }


    @Override
    public void registerTools(ToolRegistry registry) {

        Objects.requireNonNull(registry, "registry must not be null");

        registerCoreTools(registry);
    }

    private void registerCoreTools(ToolRegistry registry) {

        registerIfAbsent(registry, new EchoTool());
        registerIfAbsent(registry, new InspectEpubTool());
        registerIfAbsent(registry, new InspectCurrentProjectTool(currentProjectProvider));
        registerIfAbsent(registry, new ReadEpubNavigationTool(currentProjectProvider));
        registerIfAbsent(registry, new ValidateEpubStructureTool(currentProjectProvider, publishDirectoryProvider));
        registerIfAbsent(registry, new InspectEpubImagesTool(currentProjectProvider));

        registerIfAbsent(registry, new ValidateEpubTool(null, null, epubCheckValidator, null, publishDirectoryProvider));
        registerIfAbsent(registry, new ValidateAccessibilityTool(accessibilityValidator));
        registerIfAbsent(registry, new CreateBasicXhtmlTool(currentProjectProvider, approvalService, eventPublisher));
        
        registerIfAbsent(registry, new ReadEpubPackageTool(currentProjectProvider, publishDirectoryProvider));
        registerIfAbsent(registry, new ReadEpubMetadataTool(currentProjectProvider,publishDirectoryProvider));	// 현재 프로젝트의 최신 EPUB metadata 정보를 보여주세요.
        registerIfAbsent(registry, new ReadEpubManifestTool(currentProjectProvider,publishDirectoryProvider));
        registerIfAbsent(registry, new ReadEpubSpineTool(currentProjectProvider,publishDirectoryProvider));

        registerIfAbsent(registry, new CompareEpubTextManifestTool(currentProjectProvider,publishDirectoryProvider));
        registerIfAbsent(registry, new CompareEpubImageManifestTool(currentProjectProvider,publishDirectoryProvider));
        registerIfAbsent(registry, new CompareEpubFontManifestTool(currentProjectProvider,publishDirectoryProvider));
        registerIfAbsent(registry, new CompareEpubStyleManifestTool(currentProjectProvider,publishDirectoryProvider));
        registerIfAbsent(registry, new CompareEpubJsManifestTool(currentProjectProvider,publishDirectoryProvider));
        
        registerIfAbsent(registry, new CreateEpubProjectPlanTool(createEpubProjectPlanService,approvalService));

        
        registerIfAbsent(registry, new CreateEpubProjectTool(createEpubProjectPlanService, epubProjectsRoot));				// EPUB project root creation.
        registerIfAbsent(registry, new CreateEpubProjectStructureTool(createEpubProjectPlanService, epubProjectsRoot));		// EPUB project directory structure creation.
        registerIfAbsent(registry, new CreateEpubBaseFilesTool(createEpubProjectPlanService,epubProjectsRoot));				// EPUB project base files creation.

        registerIfAbsent(registry, new SwitchCurrentEpubProjectTool(currentProjectStore, epubProjectsRoot));				// EPUB project switch
        
        registerIfAbsent(registry, new ApplyEpubTemplateTool(currentProjectProvider, approvalService));						// EPUB Template apply
        
    }

    private void registerIfAbsent(ToolRegistry registry, AgentTool tool) {

        Objects.requireNonNull(tool, "tool must not be null");

        if (registry.contains(tool.getName())) return;

        registry.register(tool);
    }
}