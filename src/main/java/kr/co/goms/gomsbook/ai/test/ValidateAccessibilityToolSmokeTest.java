package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.accessibility.validation.DefaultAccessibilityValidator;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.accessibility.ValidateAccessibilityTool;

public final class ValidateAccessibilityToolSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] ValidateAccessibilityTool smoke test start");

        Path projectRoot = Path.of("C:/1004.GomsBook/03.Project/lunchwork_seoul");

        AccessibilityValidator accessibilityValidator = new DefaultAccessibilityValidator(List.of());

        ValidateAccessibilityTool tool = new ValidateAccessibilityTool(accessibilityValidator);

        ToolRequest request = ToolRequest.builder()
                .toolName(tool.getName())
                .arguments(Map.of(
                        "documentPath", "OEBPS/Text/chapter10_2.xhtml"
                ))
                .build();

        ToolContext context = ToolContext.builder()
                .projectRoot(projectRoot)
                .build();

        ToolResult result = tool.execute(request, context);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Tool Name = " + result.getToolName());
        System.out.println("[GomsBook AI Core] Status = " + result.getStatus());
        System.out.println("[GomsBook AI Core] Message = " + result.getMessage());
        System.out.println("[GomsBook AI Core] Data = " + result.getData());
        System.out.println("[GomsBook AI Core] Issues = " + result.getIssues());
        System.out.println("[GomsBook AI Core] -------------------------");

        if (result.getStatus() == null) throw new IllegalStateException("ValidateAccessibilityTool returned status=null.");
        if (!result.isSuccess()) throw new IllegalStateException("ValidateAccessibilityTool failed: " + result.getMessage());
        if (!result.getData().containsKey("documentPath")) throw new IllegalStateException("documentPath is missing from ToolResult.");
        if (!result.getData().containsKey("validationCompleted")) throw new IllegalStateException("validationCompleted is missing from ToolResult.");
        if (!result.getData().containsKey("totalIssueCount")) throw new IllegalStateException("totalIssueCount is missing from ToolResult.");

        System.out.println("[GomsBook AI Core] Document Path = " + result.getData().get("documentPath"));
        System.out.println("[GomsBook AI Core] Validation Completed = " + result.getData().get("validationCompleted"));
        System.out.println("[GomsBook AI Core] Passed = " + result.getData().get("passed"));
        System.out.println("[GomsBook AI Core] Total Issue Count = " + result.getData().get("totalIssueCount"));
        System.out.println("[GomsBook AI Core] Error Count = " + result.getData().get("errorCount"));
        System.out.println("[GomsBook AI Core] Warning Count = " + result.getData().get("warningCount"));
        System.out.println("[GomsBook AI Core] Info Count = " + result.getData().get("infoCount"));

        System.out.println("[GomsBook AI Core] ValidateAccessibilityToolSmokeTest success");
    }

    private ValidateAccessibilityToolSmokeTest() {}
}