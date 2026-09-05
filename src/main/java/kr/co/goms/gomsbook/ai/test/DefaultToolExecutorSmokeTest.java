package kr.co.goms.gomsbook.ai.test;

import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.logging.ExecutionLogger;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.DefaultToolExecutor;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolRegistry;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;
import kr.co.goms.gomsbook.ai.logging.NoOpExecutionLogger;

public final class DefaultToolExecutorSmokeTest {

    private static final String TOOL_NAME = "echo";
    private static final String MESSAGE = "hello-gomsbook";

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] DefaultToolExecutor smoke test start");

        ToolRegistry toolRegistry = new ToolRegistry();
        AgentTool echoTool = createEchoTool();
        toolRegistry.register(echoTool);
        
        ExecutionLogger executionLogger = new NoOpExecutionLogger();

        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolRegistry, executionLogger);

        ToolRequest request = ToolRequest.builder().requestId("core-tool-executor-smoke-001").toolCallId("tool-call-001").toolName(TOOL_NAME).argument("message", MESSAGE).build();

        ToolContext context = ToolContext.builder().build();

        ToolResult result = toolExecutor.execute(request, context);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Registry Size = " + toolRegistry.size());
        System.out.println("[GomsBook AI Core] Tool Names = " + toolRegistry.getToolNames());
        System.out.println("[GomsBook AI Core] Can Execute = " + toolExecutor.canExecute(TOOL_NAME));
        System.out.println("[GomsBook AI Core] Result Tool = " + result.getToolName());
        System.out.println("[GomsBook AI Core] Result Status = " + result.getStatus());
        System.out.println("[GomsBook AI Core] Result Data = " + result.getData());
        System.out.println("[GomsBook AI Core] -------------------------");

        if (toolRegistry.size() != 1) throw new IllegalStateException("Expected ToolRegistry size=1, but was " + toolRegistry.size());

        if (!toolRegistry.contains(TOOL_NAME)) throw new IllegalStateException("Echo Tool is not registered.");

        if (!toolExecutor.canExecute(TOOL_NAME)) throw new IllegalStateException("Echo Tool cannot be executed.");

        if (!result.isSuccess()) throw new IllegalStateException("Echo Tool execution failed.");

        if (!TOOL_NAME.equals(result.getToolName())) throw new IllegalStateException("Unexpected ToolResult name: " + result.getToolName());

        String message = result.getData("message", String.class);

        if (!MESSAGE.equals(message)) throw new IllegalStateException("Unexpected echo result: " + message);

        System.out.println("[GomsBook AI Core] DefaultToolExecutorSmokeTest success");
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
            public ToolValidationResult validate(ToolRequest request, ToolContext context) {

                Object message = request.getArgument("message");

                if (message == null) return ToolValidationResult.invalid("message is required.");

                if (String.valueOf(message).isBlank()) return ToolValidationResult.invalid("message must not be blank.");

                return ToolValidationResult.valid();
            }

            @Override
            public ToolResult execute(ToolRequest request, ToolContext context) {

                String message = String.valueOf(request.getArgument("message"));

                return ToolResult.success(TOOL_NAME).requestId(request.getRequestId()).toolCallId(request.getToolCallId()).message("Echo Tool executed successfully.").data("message", message).build();
            }
        };
    }

    private DefaultToolExecutorSmokeTest() {
    }
}