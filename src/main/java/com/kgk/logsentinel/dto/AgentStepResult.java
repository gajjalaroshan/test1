package com.kgk.logsentinel.dto;

public record AgentStepResult(String agentName, String output, int totalTokens, String error) {

    public static AgentStepResult success(String name, String output, int tokens) {
        return new AgentStepResult(name, output, tokens, null);
    }

    public static AgentStepResult skipped(String message) {
        return new AgentStepResult("skipped", message, 0, null);
    }

    public static AgentStepResult failed(String message) {
        return new AgentStepResult("failed", null, 0, message);
    }

}
