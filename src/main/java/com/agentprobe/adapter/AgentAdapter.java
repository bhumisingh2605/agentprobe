package com.agentprobe.adapter;

public interface AgentAdapter {

    AgentReply send(AgentTarget target, String message, String sessionId);
}