package com.kgk.logsentinel.service.agent;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.Objects;

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
        AgentStepResult agent1 = remediationPlannerAgent.plan(javaResult);
        String remediationPlan =
                Objects.requireNonNullElse(AgentMarkdownFormatter.normalize(agent1.output()), "");
        AgentStepResult agent2 = executiveReportAgent.report(remediationPlan, javaResult.toAgentPrompt());
        return new DualAgentPipelineResult(javaResult, agent1, agent2);
    }

    public record DualAgentPipelineResult(
            LogAnalysisResult javaAnalysis, AgentStepResult agent1, AgentStepResult agent2) {}
}
