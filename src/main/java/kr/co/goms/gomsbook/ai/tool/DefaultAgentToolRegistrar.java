/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.tool;

import java.nio.file.Path;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.plan.project.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.epub.publish.EpubArtifactFingerprintService;
import kr.co.goms.gomsbook.ai.epub.publish.EpubPublisher;
import kr.co.goms.gomsbook.ai.epub.service.EpubStructureValidator;
import kr.co.goms.gomsbook.ai.epub.service.LatestPublishedEpubResolver;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubProjectAccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubProjectValidator;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectStore;
import kr.co.goms.gomsbook.ai.tool.accessibility.ValidateAccessibilityTool;
import kr.co.goms.gomsbook.ai.tool.epub.author.CreateEpubAuthorTool;
import kr.co.goms.gomsbook.ai.tool.epub.author.DeleteEpubAuthorTool;
import kr.co.goms.gomsbook.ai.tool.epub.author.ReadEpubAuthorTool;
import kr.co.goms.gomsbook.ai.tool.epub.author.UpdateEpubAuthorTool;
import kr.co.goms.gomsbook.ai.tool.epub.chapter.CreateEpubChapterTool;
import kr.co.goms.gomsbook.ai.tool.epub.chapter.DeleteEpubChapterTool;
import kr.co.goms.gomsbook.ai.tool.epub.chapter.ReadEpubChapterTool;
import kr.co.goms.gomsbook.ai.tool.epub.chapter.UpdateEpubChapterTool;
import kr.co.goms.gomsbook.ai.tool.epub.copyright.CreateEpubCopyrightTool;
import kr.co.goms.gomsbook.ai.tool.epub.copyright.ReadEpubCopyrightTool;
import kr.co.goms.gomsbook.ai.tool.epub.copyright.UpdateEpubCopyrightTool;
import kr.co.goms.gomsbook.ai.tool.epub.generation.chapter.CreateBasicXhtmlTool;
import kr.co.goms.gomsbook.ai.tool.epub.inspect.InspectCurrentProjectTool;
import kr.co.goms.gomsbook.ai.tool.epub.inspect.InspectEpubTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubFontManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubImageManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubJsManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubStyleManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.CompareEpubTextManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.ReadEpubFileManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.ReadEpubManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.manifest.UpdateEpubManifestTool;
import kr.co.goms.gomsbook.ai.tool.epub.metadata.ReadEpubFileMetadataTool;
import kr.co.goms.gomsbook.ai.tool.epub.metadata.ReadEpubMetadataTool;
import kr.co.goms.gomsbook.ai.tool.epub.metadata.UpdateEpubMetadataTool;
import kr.co.goms.gomsbook.ai.tool.epub.navigation.CreateEpubNavigationTool;
import kr.co.goms.gomsbook.ai.tool.epub.navigation.ReadEpubNavigationTool;
import kr.co.goms.gomsbook.ai.tool.epub.navigation.UpdateEpubNavigationTool;
import kr.co.goms.gomsbook.ai.tool.epub.part.CreateEpubPartTool;
import kr.co.goms.gomsbook.ai.tool.epub.part.ReadEpubPartTool;
import kr.co.goms.gomsbook.ai.tool.epub.part.UpdateEpubPartTool;
import kr.co.goms.gomsbook.ai.tool.epub.pkg.ReadEpubFilePackageTool;
import kr.co.goms.gomsbook.ai.tool.epub.pkg.ReadEpubPackageTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.ApplyEpubTemplateTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubBaseFilesTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectPlanTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectStructureTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.CreateEpubProjectTool;
import kr.co.goms.gomsbook.ai.tool.epub.project.SwitchCurrentEpubProjectTool;
import kr.co.goms.gomsbook.ai.tool.epub.publish.PublishEpubTool;
import kr.co.goms.gomsbook.ai.tool.epub.resource.ApplyEpubStylesheetTool;
import kr.co.goms.gomsbook.ai.tool.epub.spine.ReadEpubFileSpineTool;
import kr.co.goms.gomsbook.ai.tool.epub.spine.ReadEpubSpineTool;
import kr.co.goms.gomsbook.ai.tool.epub.spine.UpdateEpubSpineTool;
import kr.co.goms.gomsbook.ai.tool.epub.validation.FixEpubFileCheckTool;
import kr.co.goms.gomsbook.ai.tool.epub.validation.ValidateEpubFileStructureTool;
import kr.co.goms.gomsbook.ai.tool.epub.validation.ValidateEpubFileTool;
import kr.co.goms.gomsbook.ai.tool.epub.validation.ValidateEpubProjectAccessibilityTool;
import kr.co.goms.gomsbook.ai.tool.epub.validation.ValidateEpubProjectTool;
import kr.co.goms.gomsbook.ai.tool.epub.xhtml.UpdateEpubXhtmlAttributeTool;
import kr.co.goms.gomsbook.ai.tool.image.InspectEpubImagesTool;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.event.AgentEventPublisher;
import com.google.gson.Gson;
import kr.co.goms.gomsbook.ai.epub.service.EpubCheckRunner;
import kr.co.goms.gomsbook.ai.epub.validation.fix.EpubFileCheckFixService;
import kr.co.goms.gomsbook.ai.tool.epub.validation.FixEpubFileCheckTool;

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
    
    private final LatestPublishedEpubResolver latestPublishedEpubResolver;
    private final EpubStructureValidator epubStructureValidator;
    private final EpubProjectAccessibilityValidator epubProjectAccessibilityValidator;
    private final EpubProjectValidator epubProjectValidator;
    
    private final Gson gson;
    
    private final EpubCheckRunner epubCheckRunner;
    private final EpubFileCheckFixService epubFileCheckFixService;
    private final EpubArtifactFingerprintService epubArtifactFingerprintService;
    
    public DefaultAgentToolRegistrar(CurrentProjectProvider currentProjectProvider, PublishDirectoryProvider publishDirectoryProvider, EpubCheckValidator epubCheckValidator, 
    		AccessibilityValidator accessibilityValidator,
            AgentApprovalService approvalService,
            AgentEventPublisher eventPublisher,
            CurrentProjectStore currentProjectStore, CreateEpubProjectPlanService createEpubProjectPlanService, Path epubProjectsRoot,
            LatestPublishedEpubResolver latestPublishedEpubResolver, EpubStructureValidator epubStructureValidator,
            Gson gson,
            EpubProjectAccessibilityValidator epubProjectAccessibilityValidator, EpubProjectValidator epubProjectValidator,
            EpubCheckRunner epubCheckRunner, EpubFileCheckFixService epubFileCheckFixService,
            EpubArtifactFingerprintService epubArtifactFingerprintService
            ) {
        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
        this.publishDirectoryProvider = Objects.requireNonNull(publishDirectoryProvider, "publishDirectoryProvider must not be null");
        this.epubCheckValidator = Objects.requireNonNull(epubCheckValidator, "epubCheckValidator must not be null");
        this.accessibilityValidator = Objects.requireNonNull(accessibilityValidator, "accessibilityValidator must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.currentProjectStore = Objects.requireNonNull(currentProjectStore, "currentProjectStore must not be null");
        this.createEpubProjectPlanService = Objects.requireNonNull(createEpubProjectPlanService, "createEpubProjectPlanService must not be null");
        this.epubProjectsRoot = Objects.requireNonNull(epubProjectsRoot, "epubProjectsRoot must not be null").toAbsolutePath().normalize();
        this.latestPublishedEpubResolver = Objects.requireNonNull(latestPublishedEpubResolver, "latestPublishedEpubResolver must not be null");
        this.epubStructureValidator = Objects.requireNonNull(epubStructureValidator, "epubStructureValidator must not be null");
        this.gson = Objects.requireNonNull(gson, "gson must not be null");
        this.epubProjectAccessibilityValidator = Objects.requireNonNull(epubProjectAccessibilityValidator, "accessibilityValidator must not be null");
        this.epubProjectValidator = Objects.requireNonNull(epubProjectValidator, "epubProjectValidator must not be null");
        this.epubCheckRunner = Objects.requireNonNull(epubCheckRunner, "epubCheckRunner must not be null");
        this.epubFileCheckFixService = Objects.requireNonNull(epubFileCheckFixService, "epubFileCheckFixService must not be null");
        this.epubArtifactFingerprintService = Objects.requireNonNull(epubArtifactFingerprintService, "epubArtifactFingerprintService must not be null");
               
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
        registerIfAbsent(registry, new ValidateEpubFileStructureTool(currentProjectProvider, publishDirectoryProvider, latestPublishedEpubResolver, epubStructureValidator));
        registerIfAbsent(registry, new InspectEpubImagesTool(currentProjectProvider));

        registerIfAbsent(registry, new ValidateEpubFileTool(null, null, epubCheckValidator, null, publishDirectoryProvider, currentProjectProvider));
        registerIfAbsent(registry, new ValidateAccessibilityTool(accessibilityValidator));
        registerIfAbsent(registry, new CreateBasicXhtmlTool(currentProjectProvider, approvalService, eventPublisher));
        
        registerIfAbsent(registry, new ReadEpubFilePackageTool(currentProjectProvider, publishDirectoryProvider));			// EPUB File Package 정보
        registerIfAbsent(registry, new ReadEpubFileMetadataTool(currentProjectProvider,publishDirectoryProvider));			// EPUB File Metadata 정보
        registerIfAbsent(registry, new ReadEpubFileManifestTool(currentProjectProvider,publishDirectoryProvider));			// EPUB File Metadata 정보
        registerIfAbsent(registry, new ReadEpubFileSpineTool(currentProjectProvider,publishDirectoryProvider));				// EPUB File Spine 정보

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
        registerIfAbsent(registry, new ApplyEpubStylesheetTool(currentProjectProvider, approvalService));					// EPUB Stylesheet apply
        
        registerIfAbsent(registry, new ReadEpubCopyrightTool(currentProjectProvider));										// EPUB Copyright 내용 읽어오기
        registerIfAbsent(registry, new UpdateEpubCopyrightTool(currentProjectProvider, approvalService));					// EPUB Copyright 수정
        registerIfAbsent(registry, new CreateEpubCopyrightTool(currentProjectProvider, approvalService));					// EPUB Copyright 신규생성

        registerIfAbsent(registry, new ReadEpubAuthorTool(currentProjectProvider));											// EPUB Author 내용 읽어오기
        registerIfAbsent(registry, new CreateEpubAuthorTool(currentProjectProvider, approvalService));						// EPUB Author 신규생성
        registerIfAbsent(registry, new UpdateEpubAuthorTool(currentProjectProvider, approvalService));						// EPUB Author 수정
        registerIfAbsent(registry, new DeleteEpubAuthorTool(currentProjectProvider, approvalService));						// EPUB Author 삭제

        registerIfAbsent(registry, new ReadEpubNavigationTool(currentProjectProvider));										// EPUB Navigaton 읽기
        registerIfAbsent(registry, new CreateEpubNavigationTool(currentProjectProvider, approvalService));					// EPUB Navigaton 신규생성
        registerIfAbsent(registry, new UpdateEpubNavigationTool(currentProjectProvider, approvalService, gson)); 			// EPUB Navigation 수정

        registerIfAbsent(registry, new CreateEpubPartTool(currentProjectProvider, approvalService));						// EPUB Part 신규생성
        registerIfAbsent(registry, new ReadEpubPartTool(currentProjectProvider));											// EPUB Part 읽기
        registerIfAbsent(registry, new UpdateEpubPartTool(currentProjectProvider, approvalService));						// EPUB Part 수정
        
        registerIfAbsent(registry, new CreateEpubChapterTool(currentProjectProvider, approvalService));						// EPUB Chapter 신규생성
        registerIfAbsent(registry, new ReadEpubChapterTool(currentProjectProvider));										// EPUB Chapter 읽기
        registerIfAbsent(registry, new UpdateEpubChapterTool(currentProjectProvider, approvalService));						// EPUB Chapter 수정
        registerIfAbsent(registry, new DeleteEpubChapterTool(currentProjectProvider, approvalService));						// EPUB Chapter 삭제
        
        registerIfAbsent(registry, new ReadEpubPackageTool(currentProjectProvider));										// EPUB Content.opf Package 정보
        registerIfAbsent(registry, new ReadEpubMetadataTool(currentProjectProvider));										// EPUB Content.opf Metadata 정보
        registerIfAbsent(registry, new ReadEpubManifestTool(currentProjectProvider));										// EPUB Content.opf Metadata 정보
        registerIfAbsent(registry, new ReadEpubSpineTool(currentProjectProvider));											// EPUB Content.opf Spine 정보
        
        registerIfAbsent(registry, new UpdateEpubSpineTool(currentProjectProvider, approvalService));						// EPUB Content.opf Spine 수정
        registerIfAbsent(registry, new UpdateEpubManifestTool(currentProjectProvider, approvalService));					// EPUB Content.opf Manifest 수정
        registerIfAbsent(registry, new UpdateEpubMetadataTool(currentProjectProvider, approvalService));					// EPUB Content.opf Metadata 수정
        
        registerIfAbsent(registry, new ValidateEpubProjectAccessibilityTool(currentProjectProvider, epubProjectAccessibilityValidator));		// EPUB Project Accessibility Validator
        registerIfAbsent(registry, new UpdateEpubXhtmlAttributeTool(currentProjectProvider, approvalService));				// EPUB xhtml 속성 수정
        registerIfAbsent(registry, new ValidateEpubProjectTool(currentProjectProvider, epubProjectValidator));				// EPUB Project 검증
        
        registerIfAbsent(registry, new PublishEpubTool(currentProjectProvider, publishDirectoryProvider, epubArtifactFingerprintService));					// EPUB .epub 파일 생성
        registerIfAbsent(registry, new FixEpubFileCheckTool(currentProjectProvider, publishDirectoryProvider, epubCheckRunner, epubFileCheckFixService));	// EPUB .epub 파일 검증 후 Fix하기
        
    }

    private void registerIfAbsent(ToolRegistry registry, AgentTool tool) {

        Objects.requireNonNull(tool, "tool must not be null");

        if (registry.contains(tool.getName())) return;

        registry.register(tool);
    }
}