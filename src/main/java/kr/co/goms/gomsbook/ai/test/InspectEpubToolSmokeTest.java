package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;
import java.util.Map;

import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.epub.inspect.InspectEpubTool;

public final class InspectEpubToolSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] InspectEpubTool smoke test start");

        Path epubFile = Path.of("C:\\1004.GomsBook\\02.Publish\\lunchwork_seoul\\lunchwork_seoul-202608163712.epub");

        InspectEpubTool tool = new InspectEpubTool();

        ToolRequest request = ToolRequest.builder()
                .toolName(InspectEpubTool.TOOL_NAME)
                .arguments(Map.of("epubFile", epubFile.toString()))
                .build();

        ToolContext context = ToolContext.builder().build();

        ToolResult result = tool.execute(request, context);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Tool Name = " + result.getToolName());
        System.out.println("[GomsBook AI Core] Status = " + result.getStatus());
        System.out.println("[GomsBook AI Core] Message = " + result.getMessage());
        System.out.println("[GomsBook AI Core] Data = " + result.getData());
        System.out.println("[GomsBook AI Core] Issues = " + result.getIssues());
        System.out.println("[GomsBook AI Core] -------------------------");

        if (!result.isSuccess()) throw new IllegalStateException("InspectEpubTool execution failed: " + result);

        if (!result.getData().containsKey("epubFile")) throw new IllegalStateException("epubFile result is missing.");

        if (!result.getData().containsKey("entryCount")) throw new IllegalStateException("entryCount result is missing.");

        if (!result.getData().containsKey("manifestItemCount")) throw new IllegalStateException("manifestItemCount result is missing.");

        if (!result.getData().containsKey("spineItemCount")) throw new IllegalStateException("spineItemCount result is missing.");

        System.out.println("[GomsBook AI Core] InspectEpubToolSmokeTest success");
    }

    private InspectEpubToolSmokeTest() {
    }
}