package com.kgk.logsentinel.web.controller;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import com.kgk.logsentinel.service.agent.DualAgentOrchestrator;
import com.kgk.logsentinel.service.analysis.LogFileAnalyzer;
import com.kgk.logsentinel.web.dto.AnalyzeRequest;
import com.kgk.logsentinel.web.dto.AnalyzeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/logs")
public class LogAnalysisController {

    private final LogFileAnalyzer analyzer;
    private final DualAgentOrchestrator orchestrator;
    private final Path defaultLogFile;

    public LogAnalysisController(
            LogFileAnalyzer analyzer,
            DualAgentOrchestrator orchestrator,
            @Value("${logsentinel.log-file}") String logFilePath) {
        this.analyzer = analyzer;
        this.orchestrator = orchestrator;
        this.defaultLogFile = Path.of(logFilePath);
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody(required = false) AnalyzeRequest request) {
        boolean useLlm = request == null || request.useLlm();
        Path logFile = request != null && request.logFilePath() != null && !request.logFilePath().isBlank()
                ? Path.of(request.logFilePath())
                : defaultLogFile;

        if (!Files.exists(logFile)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AnalyzeResponse.error("Log file not found: " + logFile));
        }
        try {
            LogAnalysisResult javaResult = analyzer.analyze(logFile);
            if (useLlm) {
                var pipeline = orchestrator.run(javaResult);
                return ResponseEntity.ok(AnalyzeResponse.fromPipeline(pipeline));
            }
            return ResponseEntity.ok(AnalyzeResponse.javaOnly(javaResult));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AnalyzeResponse.error(e.getMessage()));
        }
    }
}
