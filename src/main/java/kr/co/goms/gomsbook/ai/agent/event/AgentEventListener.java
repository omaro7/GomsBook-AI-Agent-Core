package kr.co.goms.gomsbook.ai.agent.event;

public interface AgentEventListener {

    void onEvent(
            AgentEvent event
    );
}