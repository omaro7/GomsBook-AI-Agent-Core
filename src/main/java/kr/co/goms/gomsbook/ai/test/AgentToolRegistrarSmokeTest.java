package kr.co.goms.gomsbook.ai.test;

import kr.co.goms.gomsbook.ai.tool.AgentToolRegistrar;
import kr.co.goms.gomsbook.ai.tool.DefaultAgentToolRegistrar;
import kr.co.goms.gomsbook.ai.tool.ToolRegistry;

public final class AgentToolRegistrarSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] AgentToolRegistrar smoke test start");

        ToolRegistry registry = new ToolRegistry();

        AgentToolRegistrar registrar = new DefaultAgentToolRegistrar();

        registrar.registerTools(registry);

        System.out.println("[GomsBook AI Core] Registry Size = " + registry.size());
        System.out.println("[GomsBook AI Core] Tool Names = " + registry.getToolNames());

        if (registry.size() != 1) throw new IllegalStateException("Expected ToolRegistry size=1, but was " + registry.size());

        if (!registry.contains("echo")) throw new IllegalStateException("Echo Tool is not registered.");

        if (registry.get("echo") == null) throw new IllegalStateException("Echo Tool cannot be resolved.");

        if (!registry.get("echo").isAvailable()) throw new IllegalStateException("Echo Tool is not available.");

        System.out.println("[GomsBook AI Core] AgentToolRegistrarSmokeTest success");
    }

    private AgentToolRegistrarSmokeTest() {
    }
}