package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;
import java.util.Map;

import kr.co.goms.gomsbook.ai.epub.service.EpubCheckRunner;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckRunnerValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.epub.validation.ValidateEpubTool;

public final class ValidateEpubToolSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] ValidateEpubTool smoke test start");

        Path epubCheckDirectory = Path.of("D:/14.EPub/lib/epubcheck-5.3.0");
        Path publishDirectory = Path.of("C:/1004.GomsBook/02.Publish/lunchwork_seoul");
        Path projectRoot = Path.of("C:/1004.GomsBook/03.Project/lunchwork_seoul");
        Path epubFile = Path.of("C:/1004.GomsBook/02.Publish/lunchwork_seoul/lunchwork_seoul-202608163712.epub");
        
        EpubCheckRunner epubCheckRunner = new EpubCheckRunner(epubCheckDirectory, "5.3.0");
        EpubCheckValidator epubCheckValidator = new EpubCheckRunnerValidator(epubCheckRunner, "5.3.0");
        PublishDirectoryProvider publishDirectoryProvider = () -> publishDirectory;

        ValidateEpubTool tool = new ValidateEpubTool(null, null, epubCheckValidator, null, publishDirectoryProvider);

        ToolRequest request = ToolRequest.builder().toolName(tool.getName()).arguments(Map.of("validationMode", "EPUB_CHECK")).build();
        ToolContext context = ToolContext.builder().build();

        ToolResult result = tool.execute(request, context);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Tool Name = " + result.getToolName());
        System.out.println("[GomsBook AI Core] Status = " + result.getStatus());
        System.out.println("[GomsBook AI Core] Message = " + result.getMessage());
        System.out.println("[GomsBook AI Core] Data = " + result.getData());
        System.out.println("[GomsBook AI Core] Issues = " + result.getIssues());
        System.out.println("[GomsBook AI Core] -------------------------");

        if (!result.isSuccess() && result.getStatus() == null) throw new IllegalStateException("ValidateEpubTool returned an invalid ToolResult.");
        if (!result.getData().containsKey("epubFile")) throw new IllegalStateException("Resolved EPUB file is missing.");
        if (!"EPUB_CHECK".equals(String.valueOf(result.getData().get("validationMode")))) throw new IllegalStateException("Validation mode is not EPUB_CHECK.");

        System.out.println("[GomsBook AI Core] Resolved EPUB = " + result.getData().get("epubFile"));
        System.out.println("[GomsBook AI Core] Validation Status = " + result.getData().get("validationStatus"));
        System.out.println("[GomsBook AI Core] Fatal Count = " + result.getData().get("fatalCount"));
        System.out.println("[GomsBook AI Core] Error Count = " + result.getData().get("errorCount"));
        System.out.println("[GomsBook AI Core] Warning Count = " + result.getData().get("warningCount"));
        System.out.println("[GomsBook AI Core] ValidateEpubToolSmokeTest success");
    }

    private ValidateEpubToolSmokeTest() {
    }
}