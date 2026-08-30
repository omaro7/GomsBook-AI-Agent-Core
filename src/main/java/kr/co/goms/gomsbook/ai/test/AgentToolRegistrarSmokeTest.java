package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.tool.AgentToolRegistrar;
import kr.co.goms.gomsbook.ai.tool.DefaultAgentToolRegistrar;
import kr.co.goms.gomsbook.ai.tool.ToolRegistry;
import kr.co.goms.gomsbook.ai.epub.service.EpubCheckRunner;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.DefaultCurrentProjectProvider;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.accessibility.validation.DefaultAccessibilityValidator;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.approval.DefaultAgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.event.AgentEventPublisher;
import kr.co.goms.gomsbook.ai.agent.event.DefaultAgentEventPublisher;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckRunnerValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;

public final class AgentToolRegistrarSmokeTest {

	public static void main(String[] args) {

	    System.out.println("[GomsBook AI Core] AgentToolRegistrar smoke test start");

	    Path projectRoot = Path.of("C:/1004.GomsBook/03.Project/lunchwork_seoul");
	    Path publishDirectory = Path.of("C:/1004.GomsBook/02.Publish/lunchwork_seoul");
	    Path epubCheckDirectory = Path.of("D:/14.EPub/lib/epubcheck-5.3.0");

	    CurrentProjectProvider currentProjectProvider = new DefaultCurrentProjectProvider(() -> projectRoot);
	    PublishDirectoryProvider publishDirectoryProvider = () -> publishDirectory;
	    
	    EpubCheckRunner epubCheckRunner = new EpubCheckRunner(epubCheckDirectory, "5.3.0");
	    EpubCheckValidator epubCheckValidator = new EpubCheckRunnerValidator(epubCheckRunner, "5.3.0");
	    
	    AccessibilityValidator accessibilityValidator = new DefaultAccessibilityValidator(List.of());

	    AgentApprovalService approvalService = new DefaultAgentApprovalService();

	    AgentEventPublisher eventPublisher = new DefaultAgentEventPublisher();
	    
	    AgentToolRegistrar registrar = new DefaultAgentToolRegistrar(currentProjectProvider, publishDirectoryProvider, epubCheckValidator, accessibilityValidator,
	    		approvalService, eventPublisher);

	    
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