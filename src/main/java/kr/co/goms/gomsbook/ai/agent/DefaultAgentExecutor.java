/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.prompt.ToolResponsePromptResolver;
import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.llm.LlmMessage;
import kr.co.goms.gomsbook.ai.llm.LlmRequest;
import kr.co.goms.gomsbook.ai.llm.LlmResponse;
import kr.co.goms.gomsbook.ai.llm.LlmToolCall;
import kr.co.goms.gomsbook.ai.llm.LlmToolDefinition;
import kr.co.goms.gomsbook.ai.llm.model.ChatModelProvider;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolDefinitionProvider;
import kr.co.goms.gomsbook.ai.tool.ToolExecutor;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

/**
 * 기본 Agent 실행기입니다.
 *
 * <p>
 * LLM 호출 → Tool Call 확인 → Tool 실행 →
 * Tool 결과를 대화에 추가 → LLM 재호출 과정을 반복합니다.
 * </p>
 */
public final class DefaultAgentExecutor implements AgentExecutor {

    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private static final String RUN_ID_ATTRIBUTE = "runId";

    private static final String PROJECT_ID_ATTRIBUTE = "projectId";

    private final LlmClient llmClient;

    private final ToolExecutor toolExecutor;

    private final ToolDefinitionProvider toolDefinitionProvider;

    private final ChatModelProvider chatModelProvider;

    private final ToolResponsePromptResolver toolResponsePromptResolver;

    private final int maxIterations;

    private final Gson gson = new Gson();

    private final List<AgentToolResultListener> toolResultListeners = new CopyOnWriteArrayList<>();

    public DefaultAgentExecutor(
            LlmClient llmClient,
            ToolExecutor toolExecutor,
            ToolDefinitionProvider toolDefinitionProvider,
            ChatModelProvider chatModelProvider) {

        this(
                llmClient,
                toolExecutor,
                toolDefinitionProvider,
                chatModelProvider,
                DEFAULT_MAX_ITERATIONS,
                null
        );
    }

    public DefaultAgentExecutor(
            LlmClient llmClient,
            ToolExecutor toolExecutor,
            ToolDefinitionProvider toolDefinitionProvider,
            ChatModelProvider chatModelProvider,
            int maxIterations) {

        this(
                llmClient,
                toolExecutor,
                toolDefinitionProvider,
                chatModelProvider,
                maxIterations,
                null
        );
    }

    public DefaultAgentExecutor(
            LlmClient llmClient,
            ToolExecutor toolExecutor,
            ToolDefinitionProvider toolDefinitionProvider,
            ChatModelProvider chatModelProvider,
            ToolResponsePromptResolver toolResponsePromptResolver) {

        this(
                llmClient,
                toolExecutor,
                toolDefinitionProvider,
                chatModelProvider,
                DEFAULT_MAX_ITERATIONS,
                toolResponsePromptResolver
        );
    }

    public DefaultAgentExecutor(
            LlmClient llmClient,
            ToolExecutor toolExecutor,
            ToolDefinitionProvider toolDefinitionProvider,
            ChatModelProvider chatModelProvider,
            int maxIterations,
            ToolResponsePromptResolver toolResponsePromptResolver) {

        this.llmClient = Objects.requireNonNull(llmClient, "llmClient must not be null");

        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor must not be null");

        this.toolDefinitionProvider = Objects.requireNonNull(toolDefinitionProvider, "toolDefinitionProvider must not be null");

        this.chatModelProvider = Objects.requireNonNull(chatModelProvider, "chatModelProvider must not be null");

        if (maxIterations <= 0) throw new IllegalArgumentException("maxIterations must be greater than zero");

        this.maxIterations = maxIterations;

        this.toolResponsePromptResolver = toolResponsePromptResolver;
    }

    /**
     * AgentExecutor 인터페이스의 기본 진입점입니다.
     */
    @Override
    public AgentResponse execute(AgentRequest request) {

        Objects.requireNonNull(request, "request must not be null");

        return execute(new AgentContext(request));
    }

    /**
     * AgentContext 기반으로 Agent를 실행합니다.
     */
    @Override
    public AgentResponse execute(AgentContext context) {

        Objects.requireNonNull(context, "context must not be null");

        AgentRequest request = Objects.requireNonNull(context.getRequest(), "context.request must not be null");

        long startedNanos = System.nanoTime();

        try {

            List<LlmMessage> messages = createInitialMessages(request);

            List<LlmToolDefinition> tools = resolveToolDefinitions();

            System.out.println("[GomsBook AI] Available tools = " + tools.size());

            for (LlmToolDefinition tool : tools) System.out.println("[GomsBook AI] Tool = " + tool.getName());

            List<ToolResult> toolResults = new ArrayList<>();

            List<String> pendingToolResponsePrompts = new ArrayList<>();

            LlmResponse lastResponse = null;

            for (int iteration = 1; iteration <= maxIterations; iteration++) {

                System.out.println("[GomsBook AI] Agent iteration = " + iteration);

                /*
                 * Tool 실행 후 생성된 Prompt Rule은
                 * 다음 LLM 호출에만 일시적으로 적용합니다.
                 */
                List<LlmMessage> llmMessages = createLlmMessages(messages, pendingToolResponsePrompts);

                /*
                 * 이번 LLM 요청에 적용한 Prompt Rule은 즉시 제거합니다.
                 * 다음 Tool 실행 결과에서 필요한 Rule을 다시 수집합니다.
                 */
                pendingToolResponsePrompts.clear();

                LlmRequest llmRequest = createLlmRequest(request, llmMessages, tools);

                lastResponse = llmClient.chat(llmRequest);

                if (lastResponse == null) throw new AgentException("LLM returned null response.");

                System.out.println("[GomsBook AI] LLM response model = " + lastResponse.getModel());

                System.out.println("[GomsBook AI] LLM tool call count = " + lastResponse.getToolCallCount());

                if (!lastResponse.hasToolCalls()) {

                    System.out.println("[GomsBook AI] No Tool Call. Agent execution completed.");

                    return createCompletedResponse(
                            request,
                            lastResponse,
                            toolResults,
                            iteration,
                            startedNanos
                    );
                }

                messages.add(createAssistantMessage(lastResponse));

                for (LlmToolCall toolCall : lastResponse.getToolCalls()) {

                    System.out.println("[GomsBook AI] Tool Call = " + toolCall.getToolName());

                    System.out.println("[GomsBook AI] Tool Call ID = " + toolCall.getId());

                    System.out.println("[GomsBook AI] Tool Arguments = " + toolCall.getArguments());

                    ToolResult toolResult = executeTool(request, context, toolCall);

                    System.out.println("[GomsBook AI] Tool Result = " + toolResult);

                    toolResults.add(toolResult);

                    messages.add(createToolMessage(toolCall, toolResult));

                    addToolResponsePrompt(
                            pendingToolResponsePrompts,
                            toolCall.getToolName()
                    );
                }
            }

            System.out.println("[GomsBook AI] Agent iteration limit reached.");

            return createIterationLimitResponse(
                    request,
                    lastResponse,
                    toolResults,
                    startedNanos
            );

        } catch (AgentException exception) {

            throw exception;

        } catch (RuntimeException exception) {

            System.err.println(
                    "[GomsBook AI] Agent RuntimeException"
                            + " | type=" + exception.getClass().getName()
                            + " | message=" + exception.getMessage()
            );

            exception.printStackTrace();

            throw new AgentException(
                    "Agent execution failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Agent 실행을 위한 초기 LLM 메시지를 생성합니다.
     */
    private List<LlmMessage> createInitialMessages(AgentRequest request) {

        Objects.requireNonNull(request, "request must not be null");

        List<LlmMessage> messages = new ArrayList<>();

        if (request.hasSystemPrompt()) messages.add(LlmMessage.system(request.getSystemPrompt()));

        if (request.hasMessages()) messages.addAll(request.getMessages());

        messages.add(LlmMessage.user(request.getInstruction()));

        return messages;
    }

    /**
     * 현재 등록된 Tool 정의를 가져옵니다.
     */
    private List<LlmToolDefinition> resolveToolDefinitions() {

        List<LlmToolDefinition> definitions = toolDefinitionProvider.getToolDefinitions();

        if (definitions == null || definitions.isEmpty()) return List.of();

        return List.copyOf(definitions);
    }

    /**
     * LLM 요청을 생성합니다.
     */
    private LlmRequest createLlmRequest(
            AgentRequest request,
            List<LlmMessage> messages,
            List<LlmToolDefinition> tools) {

        LlmRequest.Builder builder = LlmRequest.builder()
                .messages(messages)
                .stream(false);

        if (request.hasModel()) {

            builder.model(request.getModel());

        } else {

            String model = chatModelProvider.getModel();

            if (model != null && !model.isBlank()) builder.model(model);
        }

        if (tools != null && !tools.isEmpty()) builder.tools(tools);

        return builder.build();
    }

    /**
     * Tool을 실행합니다.
     */
    private ToolResult executeTool(
            AgentRequest agentRequest,
            AgentContext agentContext,
            LlmToolCall toolCall) {

        if (toolCall == null) throw new AgentException("LLM returned null Tool Call.");

        if (!toolCall.isFunctionCall()) throw new AgentException("Unsupported Tool Call type: " + toolCall.getType());

        String toolName = toolCall.getToolName();

        if (toolName == null || toolName.isBlank()) throw new AgentException("Tool Call name must not be blank.");

        Map<String, Object> arguments = toolCall.getArguments();

        ToolContext toolContext = createToolContext(agentRequest, agentContext);

        ToolRequest toolRequest = ToolRequest.builder()
                .requestId(agentRequest.getRequestId())
                .toolCallId(toolCall.getId())
                .toolName(toolName)
                .arguments(arguments == null ? Map.of() : arguments)
                .build();

        ToolResult result = toolExecutor.execute(toolRequest, toolContext);

        if (result == null) throw new AgentException("Tool executor returned null. tool=" + toolName);

        notifyToolResult(result);

        return result;
    }

    /**
     * AgentContext를 ToolContext로 변환합니다.
     */
    private ToolContext createToolContext(
            AgentRequest request,
            AgentContext agentContext) {

        ToolContext.Builder builder = ToolContext.builder();

        if (request.hasRequestId()) builder.requestId(request.getRequestId());

        if (request.getAttributes() != null && !request.getAttributes().isEmpty()) builder.attributes(request.getAttributes());

        String runId = getStringAttribute(request, RUN_ID_ATTRIBUTE);

        String projectId = getStringAttribute(request, PROJECT_ID_ATTRIBUTE);

        if (runId != null) builder.runId(runId);

        if (projectId != null) builder.projectId(projectId);

        return builder.build();
    }

    /**
     * LLM Tool Call 응답을 Assistant 메시지로 변환합니다.
     */
    private LlmMessage createAssistantMessage(LlmResponse response) {

        Objects.requireNonNull(response, "response must not be null");

        String content = response.getContent();

        if (content == null) content = "";

        if (response.hasToolCalls()) return LlmMessage.assistantToolCalls(content, response.getToolCalls());

        if (content.isBlank()) throw new AgentException("LLM Assistant response content is empty.");

        return LlmMessage.assistant(content);
    }

    /**
     * Tool 실행 결과를 LLM Tool 메시지로 변환합니다.
     */
    private LlmMessage createToolMessage(
            LlmToolCall toolCall,
            ToolResult toolResult) {

        Objects.requireNonNull(toolCall, "toolCall must not be null");

        Objects.requireNonNull(toolResult, "toolResult must not be null");

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("toolName", toolResult.getToolName());

        payload.put("status", toolResult.getStatus());

        if (toolResult.hasMessage()) payload.put("message", toolResult.getMessage());

        if (toolResult.hasData()) {

            Map<String, Object> data = new LinkedHashMap<>(toolResult.getData());

            /*
             * InspectEpubTool의 inspectionResult 객체에는 Path 등
             * 복합 객체가 포함되므로 이미 평탄화된 개별 data 값만
             * LLM에 전달합니다.
             */
            data.remove("inspectionResult");

            payload.put("data", data);
        }

        if (toolResult.hasError()) {

            payload.put("errorCode", toolResult.getErrorCode());

            payload.put("errorMessage", toolResult.getErrorMessage());
        }

        String content = gson.toJson(payload);

        System.out.println("[GomsBook AI] Tool Message = " + content);

        return LlmMessage.toolResult(
                toolCall.getId(),
                toolCall.getToolName(),
                content
        );
    }

    /**
     * Tool에 등록된 추가 Prompt Rule을 수집합니다.
     */
    private void addToolResponsePrompt(
            List<String> prompts,
            String toolName) {

        if (prompts == null) return;

        if (toolResponsePromptResolver == null) return;

        if (toolName == null || toolName.isBlank()) return;

        String prompt = toolResponsePromptResolver.resolve(toolName);

        if (prompt == null || prompt.isBlank()) return;

        if (prompts.contains(prompt)) return;

        prompts.add(prompt);
    }

    /**
     * 다음 LLM 호출에만 Tool별 추가 Prompt Rule을 적용합니다.
     */
    private List<LlmMessage> createLlmMessages(
            List<LlmMessage> messages,
            List<String> toolResponsePrompts) {

        if (toolResponsePrompts == null || toolResponsePrompts.isEmpty()) return messages;

        List<LlmMessage> llmMessages = new ArrayList<>(messages);

        for (String prompt : toolResponsePrompts) {

            if (prompt == null || prompt.isBlank()) continue;

            llmMessages.add(LlmMessage.system(prompt));
        }

        return llmMessages;
    }

    /**
     * 정상 완료 응답을 생성합니다.
     */
    private AgentResponse createCompletedResponse(
            AgentRequest request,
            LlmResponse llmResponse,
            List<ToolResult> toolResults,
            int iterations,
            long startedNanos) {

        return AgentResponse.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .status(AgentStatus.COMPLETED)
                .content(llmResponse.getContent())
                .model(llmResponse.getModel())
                .toolResults(toolResults)
                .iterations(iterations)
                .build();
    }

    /**
     * 최대 Tool 호출 반복 횟수에 도달한 응답을 생성합니다.
     */
    private AgentResponse createIterationLimitResponse(
            AgentRequest request,
            LlmResponse lastResponse,
            List<ToolResult> toolResults,
            long startedNanos) {

        return AgentResponse.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .status(AgentStatus.ITERATION_LIMIT_REACHED)
                .content(lastResponse == null ? "" : lastResponse.getContent())
                .model(lastResponse == null ? null : lastResponse.getModel())
                .toolResults(toolResults)
                .iterations(maxIterations)
                .errorCode("AGENT_ITERATION_LIMIT_REACHED")
                .errorMessage("Agent reached maximum Tool Call iterations.")
                .build();
    }

    @Override
    public void addToolResultListener(AgentToolResultListener listener) {

        if (listener == null) return;

        toolResultListeners.add(listener);
    }

    @Override
    public void removeToolResultListener(AgentToolResultListener listener) {

        if (listener == null) return;

        toolResultListeners.remove(listener);
    }

    private void notifyToolResult(ToolResult result) {

        if (result == null) return;

        for (AgentToolResultListener listener : toolResultListeners) notifyToolResult(listener, result);
    }

    private void notifyToolResult(
            AgentToolResultListener listener,
            ToolResult result) {

        try {

            listener.onToolResult(result);

        } catch (RuntimeException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new AgentException(
                    "Tool result listener failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private String getStringAttribute(
            AgentRequest request,
            String name) {

        if (request == null || request.getAttributes() == null) return null;

        Object value = request.getAttributes().get(name);

        if (!(value instanceof String text) || text.isBlank()) return null;

        return text.trim();
    }
}