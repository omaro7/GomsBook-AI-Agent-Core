package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;
import java.util.Map;

import kr.co.goms.gomsbook.ai.epub.service.EpubStructureValidator;
import kr.co.goms.gomsbook.ai.epub.service.LatestPublishedEpubResolver;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectStore;
import kr.co.goms.gomsbook.ai.project.DefaultCurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.InMemoryCurrentProjectStore;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.epub.validation.ValidateEpubStructureTool;
import kr.co.goms.gomsbook.ai.epub.policy.spine.EpubSpineOrderPolicy;
import kr.co.goms.gomsbook.ai.epub.policy.spine.DefaultEpubSpineOrderPolicy;

public final class ValidateEpubStructureToolSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] ValidateEpubStructureTool smoke test start");

        Path projectRoot = Path.of("C:/1004.GomsBook/03.Project/lunchwork_seoul");
        Path publishDirectory = Path.of("C:/1004.GomsBook/02.Publish/lunchwork_seoul");

        CurrentProjectStore currentProjectStore = new InMemoryCurrentProjectStore(projectRoot);
        CurrentProjectProvider currentProjectProvider = new DefaultCurrentProjectProvider(currentProjectStore);

        PublishDirectoryProvider publishDirectoryProvider = () -> publishDirectory;

        EpubSpineOrderPolicy spineOrderPolicy = new DefaultEpubSpineOrderPolicy();
        LatestPublishedEpubResolver latestPublishedEpubResolver = new LatestPublishedEpubResolver();
        EpubStructureValidator epubStructureValidator = new EpubStructureValidator(spineOrderPolicy);

        ValidateEpubStructureTool tool = new ValidateEpubStructureTool(
                currentProjectProvider,
                publishDirectoryProvider,
                latestPublishedEpubResolver,
                epubStructureValidator);
        
        ToolRequest request = ToolRequest.builder()
                .toolName(ValidateEpubStructureTool.NAME)
                .arguments(Map.of())
                .build();

        ToolContext context = ToolContext.builder().build();

        ToolResult result = tool.execute(request, context);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Tool Name = " + result.getToolName());
        System.out.println("[GomsBook AI Core] Status = " + result.getStatus());
        System.out.println("[GomsBook AI Core] Message = " + result.getMessage());
        System.out.println("[GomsBook AI Core] EPUB File = " + result.getData().get("epubFile"));
        System.out.println("[GomsBook AI Core] Valid = " + result.getData().get("valid"));
        System.out.println("[GomsBook AI Core] Package Path = " + result.getData().get("packagePath"));
        System.out.println("[GomsBook AI Core] Manifest Item Count = " + result.getData().get("manifestItemCount"));
        System.out.println("[GomsBook AI Core] Spine Item Count = " + result.getData().get("spineItemCount"));
        System.out.println("[GomsBook AI Core] Nav ID = " + result.getData().get("navId"));
        System.out.println("[GomsBook AI Core] Nav Href = " + result.getData().get("navHref"));
        System.out.println("[GomsBook AI Core] Issue Count = " + result.getData().get("issueCount"));
        System.out.println("[GomsBook AI Core] Error Count = " + result.getData().get("errorCount"));
        System.out.println("[GomsBook AI Core] Warning Count = " + result.getData().get("warningCount"));
        System.out.println("[GomsBook AI Core] Issues = " + result.getIssues());
        System.out.println("[GomsBook AI Core] -------------------------");

        if (!result.isSuccess()) throw new IllegalStateException("ValidateEpubStructureTool execution failed: " + result);

        Object epubFile = result.getData().get("epubFile");
        if (!(epubFile instanceof String value) || value.isBlank()) throw new IllegalStateException("epubFile result is missing.");

        Object valid = result.getData().get("valid");
        if (!(valid instanceof Boolean)) throw new IllegalStateException("valid result is missing or invalid.");

        Object manifestItemCount = result.getData().get("manifestItemCount");
        if (!(manifestItemCount instanceof Number)) throw new IllegalStateException("manifestItemCount result is missing or invalid.");

        Object spineItemCount = result.getData().get("spineItemCount");
        if (!(spineItemCount instanceof Number)) throw new IllegalStateException("spineItemCount result is missing or invalid.");

        Object issueCount = result.getData().get("issueCount");
        if (!(issueCount instanceof Number)) throw new IllegalStateException("issueCount result is missing or invalid.");

        Object errorCount = result.getData().get("errorCount");
        if (!(errorCount instanceof Number)) throw new IllegalStateException("errorCount result is missing or invalid.");

        Object warningCount = result.getData().get("warningCount");
        if (!(warningCount instanceof Number)) throw new IllegalStateException("warningCount result is missing or invalid.");

        System.out.println("[GomsBook AI Core] ValidateEpubStructureToolSmokeTest success");
    }

    private ValidateEpubStructureToolSmokeTest() {
    }
}