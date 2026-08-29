package kr.co.goms.gomsbook.ai.test;

import java.time.Duration;
import java.util.List;

import kr.co.goms.gomsbook.ai.agent.AgentRequest;
import kr.co.goms.gomsbook.ai.agent.AgentResponse;
import kr.co.goms.gomsbook.ai.agent.DefaultAgentExecutor;
import kr.co.goms.gomsbook.ai.json.GsonJsonMapper;
import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.llm.model.ChatModelProvider;
import kr.co.goms.gomsbook.ai.llm.ollama.OllamaConfiguration;
import kr.co.goms.gomsbook.ai.llm.ollama.OllamaLlmClient;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolDefinitionProvider;
import kr.co.goms.gomsbook.ai.tool.ToolExecutor;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

public final class DefaultAgentExecutorSmokeTest {

    private static final String MODEL =
            "gemma4:31b-cloud";

    public static void main(String[] args) {

        System.out.println(
                "[GomsBook AI Core] Agent smoke test start"
        );

        /*
         * =====================================================
         * 1. Ollama Configuration
         * =====================================================
         */
        OllamaConfiguration configuration =
                OllamaConfiguration.builder()
                        .baseUrl(
                                "http://localhost:11434"
                        )
                        .model(MODEL)
                        .chatModel(MODEL)
                        .embeddingModel(
                                "nomic-embed-text"
                        )
                        .build();

        /*
         * =====================================================
         * 2. JSON
         * =====================================================
         */
        JsonMapper jsonMapper =
                new GsonJsonMapper();

        /*
         * =====================================================
         * 3. LLM Client
         * =====================================================
         */
        LlmClient llmClient =
                new OllamaLlmClient(
                        configuration,
                        jsonMapper
                );

        /*
         * =====================================================
         * 4. Chat Model Provider
         * =====================================================
         */
        ChatModelProvider chatModelProvider =
                () -> MODEL;

        /*
         * =====================================================
         * 5. Empty Tool Definition Provider
         *
         * 이번 테스트에서는 Tool Calling을 하지 않습니다.
         * =====================================================
         */
        ToolDefinitionProvider toolDefinitionProvider =
                () -> List.of();

        /*
         * =====================================================
         * 6. Tool Executor
         *
         * Tool 정의가 없으므로 호출되면 안 됩니다.
         * 호출되면 테스트 실패로 처리합니다.
         * =====================================================
         */
        ToolExecutor toolExecutor =
                new ToolExecutor() {

                    @Override
                    public ToolResult execute(
                            ToolRequest request,
                            ToolContext context) {

                        throw new IllegalStateException(
                                "ToolExecutor must not be called "
                                        + "during this smoke test."
                        );
                    }

                    @Override
                    public boolean canExecute(
                            String toolName) {

                        return false;
                    }
                };

        /*
         * =====================================================
         * 7. Agent Executor
         * =====================================================
         */
        DefaultAgentExecutor agentExecutor =
                new DefaultAgentExecutor(
                        llmClient,
                        toolExecutor,
                        toolDefinitionProvider,
                        chatModelProvider
                );

        /*
         * =====================================================
         * 8. Agent Request
         * =====================================================
         */
        AgentRequest request =
                AgentRequest.builder()
                        .requestId(
                                "core-agent-smoke-001"
                        )
                        .sessionId(
                                "core-smoke-session"
                        )
                        .systemPrompt(
                                "당신은 GomsBook AI Agent입니다."
                        )
                        .instruction(
                                "안녕하세요. 한 문장으로 응답해주세요."
                        )
                        .model(MODEL)
                        .toolCallingEnabled(false)
                        .validationEnabled(false)
                        .build();

        /*
         * =====================================================
         * 9. Execute
         * =====================================================
         */
        AgentResponse response =
                agentExecutor.execute(
                        request
                );

        /*
         * =====================================================
         * 10. Result
         * =====================================================
         */
        System.out.println(
                "[GomsBook AI Core] -------------------------"
        );

        System.out.println(
                "[GomsBook AI Core] Request ID = "
                        + response.getRequestId()
        );

        System.out.println(
                "[GomsBook AI Core] Status = "
                        + response.getStatus()
        );

        System.out.println(
                "[GomsBook AI Core] Model = "
                        + response.getModel()
        );

        System.out.println(
                "[GomsBook AI Core] Content = "
                        + response.getContent()
        );

        System.out.println(
                "[GomsBook AI Core] Iterations = "
                        + response.getIterations()
        );

        System.out.println(
                "[GomsBook AI Core] Tool Results = "
                        + response.getToolResults().size()
        );

        Duration duration =
                response.getDuration();

        System.out.println(
                "[GomsBook AI Core] Duration = "
                        + (duration == null
                                ? "N/A"
                                : duration.toMillis()
                                        + " ms")
        );

        System.out.println(
                "[GomsBook AI Core] -------------------------"
        );

        /*
         * =====================================================
         * 11. Validation
         * =====================================================
         */
        if (!response.isSuccess()) {

            throw new IllegalStateException(
                    "Agent execution failed. status="
                            + response.getStatus()
                            + ", error="
                            + response.getErrorMessage()
            );
        }

        if (!response.hasContent()) {

            throw new IllegalStateException(
                    "Agent response content is empty."
            );
        }

        if (response.hasToolResults()) {

            throw new IllegalStateException(
                    "Unexpected Tool result detected."
            );
        }

        if (response.getIterations() != 1) {

            throw new IllegalStateException(
                    "Expected 1 Agent iteration but was "
                            + response.getIterations()
            );
        }

        System.out.println(
                "[GomsBook AI Core] "
                        + "DefaultAgentExecutor smoke test success"
        );
    }

    private DefaultAgentExecutorSmokeTest() {
    }
}