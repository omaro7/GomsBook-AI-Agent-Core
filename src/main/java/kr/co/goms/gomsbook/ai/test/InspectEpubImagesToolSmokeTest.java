package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.DefaultCurrentProjectProvider;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.image.InspectEpubImagesTool;

public final class InspectEpubImagesToolSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] InspectEpubImagesTool smoke test start");

        Path projectRoot = Path.of("C:/1004.GomsBook/03.Project/lunchwork_seoul");

        CurrentProjectProvider currentProjectProvider = new DefaultCurrentProjectProvider(() -> projectRoot);

        InspectEpubImagesTool tool = new InspectEpubImagesTool(currentProjectProvider);

        ToolRequest request = ToolRequest.builder()
                .toolName(InspectEpubImagesTool.NAME)
                .arguments(Map.of())
                .build();

        ToolContext context = ToolContext.builder().build();

        ToolResult result = tool.execute(request, context);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Tool Name = " + result.getToolName());
        System.out.println("[GomsBook AI Core] Status = " + result.getStatus());
        System.out.println("[GomsBook AI Core] Message = " + result.getMessage());
        System.out.println("[GomsBook AI Core] Project Name = " + result.getData().get("projectName"));
        System.out.println("[GomsBook AI Core] Text Directory = " + result.getData().get("textDirectory"));
        System.out.println("[GomsBook AI Core] Image Count = " + result.getData().get("imageCount"));
        System.out.println("[GomsBook AI Core] Missing Alt Count = " + result.getData().get("missingAltCount"));
        System.out.println("[GomsBook AI Core] Aria Hidden Count = " + result.getData().get("ariaHiddenCount"));
        System.out.println("[GomsBook AI Core] Images = " + result.getData().get("images"));
        System.out.println("[GomsBook AI Core] Issues = " + result.getIssues());
        System.out.println("[GomsBook AI Core] -------------------------");

        if (!result.isSuccess()) throw new IllegalStateException("InspectEpubImagesTool execution failed: " + result);

        Object projectName = result.getData().get("projectName");
        if (!(projectName instanceof String value) || value.isBlank()) throw new IllegalStateException("projectName result is missing.");

        Object textDirectory = result.getData().get("textDirectory");
        if (!(textDirectory instanceof String value2) || value2.isBlank()) throw new IllegalStateException("textDirectory result is missing.");

        Object imageCount = result.getData().get("imageCount");
        if (!(imageCount instanceof Number)) throw new IllegalStateException("imageCount result is missing or invalid.");

        Object missingAltCount = result.getData().get("missingAltCount");
        if (!(missingAltCount instanceof Number)) throw new IllegalStateException("missingAltCount result is missing or invalid.");

        Object ariaHiddenCount = result.getData().get("ariaHiddenCount");
        if (!(ariaHiddenCount instanceof Number)) throw new IllegalStateException("ariaHiddenCount result is missing or invalid.");

        Object imagesValue = result.getData().get("images");
        if (!(imagesValue instanceof List<?> images)) throw new IllegalStateException("images result is missing or invalid.");

        printImages(images);

        System.out.println("[GomsBook AI Core] InspectEpubImagesToolSmokeTest success");
    }

    private static void printImages(List<?> images) {

        for (Object value : images) {

            if (!(value instanceof Map<?, ?> image)) continue;

            System.out.println(
                    "[GomsBook AI Core] Image"
                            + " | xhtml=" + image.get("xhtmlFile")
                            + " | src=" + image.get("src")
                            + " | imgId=" + image.get("imgId")
                            + " | alt=" + image.get("alt")
                            + " | role=" + image.get("role")
                            + " | ariaHidden=" + image.get("ariaHidden")
                            + " | altMissing=" + image.get("altMissing"));
        }
    }

    private InspectEpubImagesToolSmokeTest() {
    }
}