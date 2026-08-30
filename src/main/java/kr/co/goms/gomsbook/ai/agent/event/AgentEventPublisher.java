package kr.co.goms.gomsbook.ai.agent.event;

public interface AgentEventPublisher {

    void publish(
            AgentEvent event
    );
}