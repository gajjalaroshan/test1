package com.kgk.logsentinel.service.agent;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import org.springframework.stereotype.Service;

@Service
public class DualAgentOrchestrator {

    private final RemediationPlannerAgent remediationPlannerAgent;
    private final ExecutiveReportAgent executiveReportAgent;

    public DualAgentOrchestrator(
            RemediationPlannerAgent remediationPlannerAgent, ExecutiveReportAgent executiveReportAgent) {
        this.remediationPlannerAgent = remediationPlannerAgent;
        this.executiveReportAgent = executiveReportAgent;
    }

    public DualAgentPipelineResult run(LogAnalysisResult javaResult) {
        String javaSummary = javaResult.toAgentPrompt();
        AgentStepResult agent1 = remediationPlannerAgent.plan(javaResult);
        AgentStepResult agent2 = executiveReportAgent.report(javaSummary);
        return new DualAgentPipelineResult(javaResult, agent1, agent2);
    }

    public record DualAgentPipelineResult(
            LogAnalysisResult javaAnalysis, AgentStepResult agent1, AgentStepResult agent2) {}
}
