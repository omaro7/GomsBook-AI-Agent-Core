package kr.co.goms.gomsbook.ai.test;

import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.llm.LlmToolDefinition;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.DefaultToolDefinitionMapper;
import kr.co.goms.gomsbook.ai.tool.DefaultToolDefinitionProvider;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolDefinitionMapper;
import kr.co.goms.gomsbook.ai.tool.ToolDefinitionProvider;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolRegistry;

public final class ToolDefinitionProviderSmokeTest {

    private static final String TOOL_NAME = "echo";

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] ToolDefinitionProvider smoke test start");

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(createEchoTool());

        ToolDefinitionMapper mapper = new DefaultToolDefinitionMapper();
        ToolDefinitionProvider provider = new DefaultToolDefinitionProvider(toolRegistry, mapper);

        List<LlmToolDefinition> definitions = provider.getToolDefinitions();

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Registry Size = " + toolRegistry.size());
        System.out.println("[GomsBook AI Core] Definition Count = " + definitions.size());

        for (LlmToolDefinition definition : definitions) {
            System.out.println("[GomsBook AI Core] Tool Name = " + definition.getName());
            System.out.println("[GomsBook AI Core] Tool Description = " + definition.getDescription());
            System.out.println("[GomsBook AI Core] Tool Parameters = " + definition.getParameters());
        }

        System.out.println("[GomsBook AI Core] -------------------------");

        if (definitions.size() != 1) throw new IllegalStateException("Expected definition count=1, but was " + definitions.size());

        LlmToolDefinition definition = definitions.get(0);

        if (!TOOL_NAME.equals(definition.getName())) throw new IllegalStateException("Unexpected Tool name: " + definition.getName());

        if (!provider.containsTool(TOOL_NAME)) throw new IllegalStateException("Provider does not contain echo Tool.");

        if (provider.getToolDefinition(TOOL_NAME) == null) throw new IllegalStateException("Echo Tool definition was not found.");

        if (provider.getToolDefinitionCount() != 1) throw new IllegalStateException("Unexpected Tool definition count.");

        if (!provider.hasToolDefinitions()) throw new IllegalStateException("Tool definitions must not be empty.");

        System.out.println("[GomsBook AI Core] ToolDefinitionProviderSmokeTest success");
    }

    private static AgentTool createEchoTool() {

        return new AgentTool() {

            @Override
            public String getName() {
                return TOOL_NAME;
            }

            @Override
            public String getDescription() {
                return "입력받은 message 문자열을 그대로 반환하는 테스트 Tool입니다.";
            }

            @Override
            public Map<String, Object> getInputSchema() {
                return Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string", "description", "반환할 문자열")), "required", List.of("message"));
            }

            @Override
            public ToolResult execute(ToolRequest request, ToolContext context) {
                String message = String.valueOf(request.getArgument("message"));
                return ToolResult.success(TOOL_NAME).requestId(request.getRequestId()).toolCallId(request.getToolCallId()).data("message", message).build();
            }
        };
    }

    private ToolDefinitionProviderSmokeTest() {
    }
}