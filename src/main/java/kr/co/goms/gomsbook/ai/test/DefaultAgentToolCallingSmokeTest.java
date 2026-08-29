package kr.co.goms.gomsbook.ai.test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import kr.co.goms.gomsbook.ai.agent.AgentRequest;
import kr.co.goms.gomsbook.ai.agent.AgentResponse;
import kr.co.goms.gomsbook.ai.agent.DefaultAgentExecutor;
import kr.co.goms.gomsbook.ai.json.GsonJsonMapper;
import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.llm.LlmToolDefinition;
import kr.co.goms.gomsbook.ai.llm.model.ChatModelProvider;
import kr.co.goms.gomsbook.ai.llm.ollama.OllamaConfiguration;
import kr.co.goms.gomsbook.ai.llm.ollama.OllamaLlmClient;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolDefinitionProvider;
import kr.co.goms.gomsbook.ai.tool.ToolExecutor;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

public final class DefaultAgentToolCallingSmokeTest {

    private static final String MODEL = "gemma4:31b-cloud";
    private static final String TOOL_NAME = "echo";

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] Agent Tool Calling smoke test start");

        OllamaConfiguration configuration = OllamaConfiguration.builder().baseUrl("http://localhost:11434").model(MODEL).chatModel(MODEL).embeddingModel("nomic-embed-text").build();

        JsonMapper jsonMapper = new GsonJsonMapper();

        LlmClient llmClient = new OllamaLlmClient(configuration, jsonMapper);

        ChatModelProvider chatModelProvider = () -> MODEL;

        LlmToolDefinition echoDefinition = createEchoToolDefinition();

        ToolDefinitionProvider toolDefinitionProvider = () -> List.of(echoDefinition);

        AtomicInteger toolExecutionCount = new AtomicInteger();

        ToolExecutor toolExecutor = new ToolExecutor() {

            @Override
            public ToolResult execute(ToolRequest request, ToolContext context) {

                System.out.println("[GomsBook AI Core] ToolExecutor called");
                System.out.println("[GomsBook AI Core] Tool Name = " + request.getToolName());
                System.out.println("[GomsBook AI Core] Arguments = " + request.getArguments());

                if (!TOOL_NAME.equals(request.getToolName())) throw new IllegalStateException("Unexpected Tool: " + request.getToolName());

                Object message = request.getArgument("message");

                if (message == null) throw new IllegalStateException("echo.message argument is missing.");

                toolExecutionCount.incrementAndGet();

                return ToolResult.success(TOOL_NAME).requestId(request.getRequestId()).toolCallId(request.getToolCallId()).message("Echo Tool executed successfully.").data("message", String.valueOf(message)).build();
            }

            @Override
            public boolean canExecute(String toolName) {
                return TOOL_NAME.equals(toolName);
            }
        };

        DefaultAgentExecutor agentExecutor = new DefaultAgentExecutor(llmClient, toolExecutor, toolDefinitionProvider, chatModelProvider);

        AgentRequest request = AgentRequest.builder()
                .requestId("core-tool-smoke-001")
                .sessionId("core-tool-smoke-session")
                .model(MODEL)
                .systemPrompt("당신은 Tool Calling 테스트용 AI Agent입니다. 사용자가 echo 도구 사용을 요청하면 반드시 echo 도구를 호출해야 합니다. 도구 실행 결과를 받은 후에는 도구를 다시 호출하지 말고 결과를 사용자에게 한 문장으로 답변하세요.")
                .instruction("반드시 echo 도구를 한 번 사용해서 message 인자에 \"hello-gomsbook\"을 전달하세요. 도구 실행 결과를 받은 후 그 결과를 알려주세요.")
                .toolCallingEnabled(true)
                .validationEnabled(false)
                .build();

        AgentResponse response = agentExecutor.execute(request);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Request ID = " + response.getRequestId());
        System.out.println("[GomsBook AI Core] Status = " + response.getStatus());
        System.out.println("[GomsBook AI Core] Model = " + response.getModel());
        System.out.println("[GomsBook AI Core] Content = " + response.getContent());
        System.out.println("[GomsBook AI Core] Iterations = " + response.getIterations());
        System.out.println("[GomsBook AI Core] Tool Results = " + response.getToolResults().size());
        System.out.println("[GomsBook AI Core] Tool Execution Count = " + toolExecutionCount.get());

        if (!response.getToolResults().isEmpty()) {

            ToolResult firstToolResult = response.getToolResults().get(0);

            System.out.println("[GomsBook AI Core] Tool Result Name = " + firstToolResult.getToolName());
            System.out.println("[GomsBook AI Core] Tool Result Status = " + firstToolResult.getStatus());
            System.out.println("[GomsBook AI Core] Tool Result Data = " + firstToolResult.getData());
        }

        System.out.println("[GomsBook AI Core] -------------------------");

        if (!response.isSuccess()) throw new IllegalStateException("Agent execution failed. status=" + response.getStatus() + ", error=" + response.getErrorMessage());

        if (!response.hasContent()) throw new IllegalStateException("Final Agent response is empty.");

        if (toolExecutionCount.get() != 1) throw new IllegalStateException("Expected exactly 1 Tool execution, but was " + toolExecutionCount.get());

        if (response.getToolResults().size() != 1) throw new IllegalStateException("Expected exactly 1 ToolResult, but was " + response.getToolResults().size());

        ToolResult toolResult = response.getToolResults().get(0);

        if (!toolResult.isSuccess()) throw new IllegalStateException("Echo Tool execution failed.");

        if (!TOOL_NAME.equals(toolResult.getToolName())) throw new IllegalStateException("Unexpected ToolResult name: " + toolResult.getToolName());

        String echoedMessage = toolResult.getData("message", String.class);

        if (!"hello-gomsbook".equals(echoedMessage)) throw new IllegalStateException("Unexpected echo result: " + echoedMessage);

        if (response.getIterations() < 2) throw new IllegalStateException("Tool Calling requires at least 2 Agent iterations.");

        System.out.println("[GomsBook AI Core] DefaultAgentToolCallingSmokeTest success");
    }

    private static LlmToolDefinition createEchoToolDefinition() {

        Map<String, Object> messageProperty = new LinkedHashMap<>();
        messageProperty.put("type", "string");
        messageProperty.put("description", "그대로 반환할 문자열");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("message", messageProperty);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("message"));
        parameters.put("additionalProperties", false);

        return LlmToolDefinition.function(TOOL_NAME, "입력받은 message 문자열을 그대로 반환하는 테스트 도구입니다.", parameters);
    }

    private DefaultAgentToolCallingSmokeTest() {
    }
}