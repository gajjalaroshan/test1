package com.kgk.logsentinel.dto;

import com.kgk.logsentinel.service.agent.AgentMarkdownFormatter;

public record AgentReports(
        String remediationPlanMarkdown,
        String executiveReportMarkdown,
        int remediationPlannerTokens,
        int executiveReportTokens,
        String remediationPlannerError,
        String executiveReportError) {

    public static AgentReports from(AgentStepResult remediation, AgentStepResult executive) {
        return new AgentReports(
                AgentMarkdownFormatter.normalize(remediation.output()),
                AgentMarkdownFormatter.normalize(executive.output()),
                remediation.totalTokens(),
                executive.totalTokens(),
                remediation.error(),
                executive.error());
    }
}
