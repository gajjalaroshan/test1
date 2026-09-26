package com.kgk.logsentinel.web.dto;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import com.kgk.logsentinel.service.agent.DualAgentOrchestrator;

public record AnalyzeResponse(
    LogAnalysisResult javaAnalysis,
    RuleFlagSummary ruleFlags,
    AgentReports agents,
    String error) {

    public static AnalyzeResponse javaOnly(LogAnalysisResult javaResult) {
        return new AnalyzeResponse(javaResult, RuleFlagSummary.from(javaResult), null, null);
    }

    public static AnalyzeResponse fromPipeline(DualAgentOrchestrator.DualAgentPipelineResult pipeline) {
        LogAnalysisResult javaResult = pipeline.javaAnalysis();
        String topLevelError = pipeline.agent2().error() == null
            ? pipeline.agent1().error()
            : pipeline.agent2().error();
        return new AnalyzeResponse(
            javaResult,
            RuleFlagSummary.from(javaResult),
            AgentReports.from(pipeline.agent1(), pipeline.agent2()),
            topLevelError);
    }

    public static AnalyzeResponse error(String message) {
        return new AnalyzeResponse(null, null, null, message);
    }
}
