package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.plan.project.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.epub.plan.project.CreateEpubProjectPlanStore;
import kr.co.goms.gomsbook.ai.epub.plan.project.DefaultCreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.epub.plan.project.InMemoryCreateEpubProjectPlanStore;
import kr.co.goms.gomsbook.ai.epub.policy.spine.DefaultEpubSpineOrderPolicy;
import kr.co.goms.gomsbook.ai.epub.policy.spine.EpubSpineOrderPolicy;
import kr.co.goms.gomsbook.ai.epub.publish.DefaultEpubArtifactFingerprintService;
import kr.co.goms.gomsbook.ai.epub.publish.DefaultEpubPublisher;
import kr.co.goms.gomsbook.ai.epub.publish.EpubArtifactFingerprintService;
import kr.co.goms.gomsbook.ai.epub.publish.EpubPublisher;
import kr.co.goms.gomsbook.ai.epub.service.EpubCheckRunner;
import kr.co.goms.gomsbook.ai.epub.service.EpubStructureValidator;
import kr.co.goms.gomsbook.ai.epub.service.LatestPublishedEpubResolver;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectStore;
import kr.co.goms.gomsbook.ai.project.DefaultCurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.InMemoryCurrentProjectStore;
import kr.co.goms.gomsbook.ai.tool.ToolRegistry;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.accessibility.validation.DefaultAccessibilityValidator;
import kr.co.goms.gomsbook.ai.accessibility.validation.DefaultEpubProjectAccessibilityValidator;
import kr.co.goms.gomsbook.ai.accessibility.validation.DefaultEpubProjectValidator;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.DefaultAgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.event.AgentEventPublisher;
import kr.co.goms.gomsbook.ai.agent.event.DefaultAgentEventPublisher;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckRunnerValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubProjectAccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubProjectValidator;
import kr.co.goms.gomsbook.ai.epub.validation.fix.DefaultEpubFileCheckFixService;
import kr.co.goms.gomsbook.ai.epub.validation.fix.EpubFileCheckFixService;
import kr.co.goms.gomsbook.ai.tool.DefaultAgentToolRegistrar;
import kr.co.goms.gomsbook.ai.tool.AgentToolRegistrar;
import kr.co.goms.gomsbook.ai.epub.validation.DefaultEpubFileCheckIssueAnalyzer;
import kr.co.goms.gomsbook.ai.epub.validation.EpubFileCheckIssueAnalyzer;
import kr.co.goms.gomsbook.ai.epub.validation.fix.DefaultEpubFileCheckFixPlan;
import kr.co.goms.gomsbook.ai.epub.validation.fix.DefaultEpubFileCheckFixResolver;
import kr.co.goms.gomsbook.ai.epub.validation.fix.EpubFileCheckFixPlan;
import kr.co.goms.gomsbook.ai.epub.validation.fix.EpubFileCheckFixResolver;

import com.google.gson.Gson;
public final class AgentToolRegistrarSmokeTest {

	public static void main(String[] args) {

	    System.out.println("[GomsBook AI Core] AgentToolRegistrar smoke test start");

	    Path projectRoot = Path.of("C:/1004.GomsBook/03.Project/lunchwork_seoul");
	    Path publishDirectory = Path.of("C:/1004.GomsBook/02.Publish/lunchwork_seoul");
	    Path epubCheckDirectory = Path.of("D:/14.EPub/lib/epubcheck-5.3.0");

        CurrentProjectStore currentProjectStore = new InMemoryCurrentProjectStore(projectRoot);
        CurrentProjectProvider currentProjectProvider = new DefaultCurrentProjectProvider(currentProjectStore);

	    PublishDirectoryProvider publishDirectoryProvider = () -> publishDirectory;
	    
	    EpubCheckRunner epubCheckRunner = new EpubCheckRunner(epubCheckDirectory, "5.3.0");
	    EpubCheckValidator epubCheckValidator = new EpubCheckRunnerValidator(epubCheckRunner, "5.3.0");

	    AccessibilityValidator accessibilityValidator = new DefaultAccessibilityValidator(List.of());

	    AgentApprovalService approvalService = new DefaultAgentApprovalService();

	    AgentEventPublisher eventPublisher = new DefaultAgentEventPublisher();
	    
	    CreateEpubProjectPlanStore createEpubProjectPlanStore = new InMemoryCreateEpubProjectPlanStore();

	    CreateEpubProjectPlanService createEpubProjectPlanService = new DefaultCreateEpubProjectPlanService(createEpubProjectPlanStore);

	    Path epubProjectsRoot = Path.of("C:\\1004.GomsBook\\03.Project");
	    

	    EpubArtifactFingerprintService epubArtifactFingerprintService = new DefaultEpubArtifactFingerprintService();
	    
	    EpubPublisher epubPublisher = new DefaultEpubPublisher(projectRoot, publishDirectory, epubArtifactFingerprintService);
	    
	    EpubSpineOrderPolicy spineOrderPolicy = new DefaultEpubSpineOrderPolicy();
	    LatestPublishedEpubResolver latestPublishedEpubResolver = new LatestPublishedEpubResolver();
	    EpubStructureValidator epubStructureValidator = new EpubStructureValidator(spineOrderPolicy);
	    Gson gson = new Gson();
	    EpubProjectAccessibilityValidator epubProjectAccessibilityValidator = new DefaultEpubProjectAccessibilityValidator(accessibilityValidator);
	    EpubProjectValidator epubProjectValidator = new DefaultEpubProjectValidator();
	    
	    EpubFileCheckIssueAnalyzer issueAnalyzer = new DefaultEpubFileCheckIssueAnalyzer();
	    EpubFileCheckFixPlan fixPlan = new DefaultEpubFileCheckFixPlan();
	    EpubFileCheckFixResolver fixResolver = new DefaultEpubFileCheckFixResolver();
	    EpubFileCheckFixService epubFileCheckFixService = new DefaultEpubFileCheckFixService(issueAnalyzer, fixPlan, fixResolver);

	    
	    AgentToolRegistrar registrar = new DefaultAgentToolRegistrar(
	    		currentProjectProvider, publishDirectoryProvider, epubCheckValidator, accessibilityValidator,
	    		approvalService, eventPublisher, 
	    		currentProjectStore, createEpubProjectPlanService, epubProjectsRoot,
	    		latestPublishedEpubResolver, epubStructureValidator,
	    		gson,
	    		epubProjectAccessibilityValidator, epubProjectValidator,
	    		epubCheckRunner, epubFileCheckFixService,
	    		epubArtifactFingerprintService
	    );

	    
	    ToolRegistry registry = new ToolRegistry();

	    registrar.registerTools(registry);

	    System.out.println("[GomsBook AI Core] Registry Size = " + registry.size());
	    System.out.println("[GomsBook AI Core] Tool Names = " + registry.getToolNames());

	    if (!registry.contains("echo")) throw new IllegalStateException("Echo Tool is not registered.");

	    if (!registry.contains("inspect_epub")) throw new IllegalStateException("Inspect EPUB Tool is not registered.");

	    if (!registry.contains("read_epub_navigation")) throw new IllegalStateException("Read EPUB Navigation Tool is not registered.");

	    if (!registry.contains("validate_epub_structure")) throw new IllegalStateException("Validate EPUB Structure Tool is not registered.");

	    System.out.println("[GomsBook AI Core] AgentToolRegistrarSmokeTest success");
	}

    private AgentToolRegistrarSmokeTest() {
    }
}