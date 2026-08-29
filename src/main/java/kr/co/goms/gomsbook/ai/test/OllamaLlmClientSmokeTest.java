package kr.co.goms.gomsbook.ai.test;

import kr.co.goms.gomsbook.ai.json.GsonJsonMapper;
import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.llm.LlmResponse;
import kr.co.goms.gomsbook.ai.llm.ollama.OllamaConfiguration;
import kr.co.goms.gomsbook.ai.llm.ollama.OllamaLlmClient;

public final class OllamaLlmClientSmokeTest {

    private static final String MODEL =
            "gemma4:31b-cloud";

    public static void main(String[] args) {

        System.out.println(
                "[GomsBook AI Core] Ollama smoke test start"
        );

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

        JsonMapper jsonMapper =
                new GsonJsonMapper();

        OllamaLlmClient llmClient =
                new OllamaLlmClient(
                        configuration,
                        jsonMapper
                );

        System.out.println(
                "[GomsBook AI Core] Base URL = "
                        + configuration.getBaseUrl()
        );

        System.out.println(
                "[GomsBook AI Core] Model = "
                        + configuration.getModel()
        );

        LlmResponse response =
                llmClient.chat(
                        "당신은 GomsBook AI Agent입니다.",
                        "안녕하세요. 한 문장으로 응답해주세요."
                );

        System.out.println(
                "[GomsBook AI Core] -------------------------"
        );

        System.out.println(
                "[GomsBook AI Core] Response Model = "
                        + response.getModel()
        );

        System.out.println(
                "[GomsBook AI Core] Content = "
                        + response.getContent()
        );

        System.out.println(
                "[GomsBook AI Core] Done = "
                        + response.isDone()
        );

        System.out.println(
                "[GomsBook AI Core] Prompt Tokens = "
                        + response.getPromptTokenCount()
        );

        System.out.println(
                "[GomsBook AI Core] Completion Tokens = "
                        + response.getCompletionTokenCount()
        );

        System.out.println(
                "[GomsBook AI Core] Tool Calls = "
                        + response.getToolCallCount()
        );

        System.out.println(
                "[GomsBook AI Core] -------------------------"
        );

        System.out.println(
                "[GomsBook AI Core] Ollama smoke test success"
        );
    }

    private OllamaLlmClientSmokeTest() {
    }
}