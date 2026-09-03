package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectStore;
import kr.co.goms.gomsbook.ai.project.DefaultCurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.InMemoryCurrentProjectStore;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.epub.navigation.ReadEpubNavigationTool;

public final class ReadEpubNavigationToolSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] ReadEpubNavigationTool smoke test start");

        Path projectRoot = Path.of("C:/1004.GomsBook/03.Project/lunchwork_seoul");

        CurrentProjectStore currentProjectStore = new InMemoryCurrentProjectStore(projectRoot);
        CurrentProjectProvider currentProjectProvider = new DefaultCurrentProjectProvider(currentProjectStore);

        ReadEpubNavigationTool tool = new ReadEpubNavigationTool(currentProjectProvider);

        ToolRequest request = ToolRequest.builder()
                .toolName(ReadEpubNavigationTool.NAME)
                .arguments(Map.of())
                .build();

        ToolContext context = ToolContext.builder().build();

        ToolResult result = tool.execute(request, context);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Tool Name = " + result.getToolName());
        System.out.println("[GomsBook AI Core] Status = " + result.getStatus());
        System.out.println("[GomsBook AI Core] Message = " + result.getMessage());
        System.out.println("[GomsBook AI Core] Project Name = " + result.getData().get("projectName"));
        System.out.println("[GomsBook AI Core] Nav File = " + result.getData().get("navFile"));
        System.out.println("[GomsBook AI Core] Entry Count = " + result.getData().get("entryCount"));
        System.out.println("[GomsBook AI Core] TOC = " + result.getData().get("toc"));
        System.out.println("[GomsBook AI Core] Issues = " + result.getIssues());
        System.out.println("[GomsBook AI Core] -------------------------");

        if (!result.isSuccess()) throw new IllegalStateException("ReadEpubNavigationTool execution failed: " + result);

        Object projectName = result.getData().get("projectName");
        if (projectName == null) throw new IllegalStateException("projectName result is missing.");

        Object navFile = result.getData().get("navFile");
        if (navFile == null) throw new IllegalStateException("navFile result is missing.");

        Object entryCountValue = result.getData().get("entryCount");
        if (!(entryCountValue instanceof Number entryCount)) throw new IllegalStateException("entryCount result is missing or invalid.");
        if (entryCount.intValue() <= 0) throw new IllegalStateException("Navigation TOC is empty.");

        Object tocValue = result.getData().get("toc");
        if (!(tocValue instanceof List<?> toc)) throw new IllegalStateException("toc result is missing or invalid.");
        if (toc.isEmpty()) throw new IllegalStateException("Navigation TOC list is empty.");

        printToc(toc, 0);

        System.out.println("[GomsBook AI Core] ReadEpubNavigationToolSmokeTest success");
    }

    private static void printToc(List<?> entries, int indent) {

        for (Object value : entries) {

            if (!(value instanceof Map<?, ?> entry)) continue;

            String prefix = "  ".repeat(indent);

            System.out.println("[GomsBook AI Core] " + prefix
                    + "- title=" + entry.get("title")
                    + ", href=" + entry.get("href")
                    + ", depth=" + entry.get("depth"));

            Object childrenValue = entry.get("children");

            if (childrenValue instanceof List<?> children && !children.isEmpty()) printToc(children, indent + 1);
        }
    }

    private ReadEpubNavigationToolSmokeTest() {
    }
}