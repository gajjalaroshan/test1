package com.kgk.logsentinel.web.controller;

import com.kgk.logsentinel.domain.analysis.LogAnalysisResult;
import com.kgk.logsentinel.service.agent.DualAgentOrchestrator;
import com.kgk.logsentinel.service.analysis.LogDirectoryService;
import com.kgk.logsentinel.service.analysis.LogFileAnalyzer;
import com.kgk.logsentinel.web.dto.AnalyzeRequest;
import com.kgk.logsentinel.web.dto.AnalyzeResponse;
import com.kgk.logsentinel.web.dto.LogFileEntry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
public class LogAnalysisController {

    private final LogFileAnalyzer analyzer;
    private final DualAgentOrchestrator orchestrator;
    private final LogDirectoryService logDirectoryService;

    public LogAnalysisController(
            LogFileAnalyzer analyzer,
            DualAgentOrchestrator orchestrator,
            LogDirectoryService logDirectoryService) {
        this.analyzer = analyzer;
        this.orchestrator = orchestrator;
        this.logDirectoryService = logDirectoryService;
    }

    @GetMapping("/files")
    public ResponseEntity<List<LogFileEntry>> listLogFiles() {
        try {
            return ResponseEntity.ok(logDirectoryService.listLogFiles());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody(required = false) AnalyzeRequest request) {
        boolean useLlm = request == null || request.shouldUseLlm();
        List<Path> paths = resolveLogPaths(request);

        if (paths.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AnalyzeResponse.error("No log file path(s) specified"));
        }
        for (Path logFile : paths) {
            if (!Files.exists(logFile)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AnalyzeResponse.error("Log file not found: " + logFile));
            }
        }
        try {
            LogAnalysisResult javaResult = paths.size() == 1 ? analyzer.analyze(paths.getFirst()) : analyzer.analyze(paths);
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

    private List<Path> resolveLogPaths(AnalyzeRequest request) {
        if (request != null && request.logFilePaths() != null && !request.logFilePaths().isEmpty()) {
            List<Path> paths = new ArrayList<>();
            for (String p : request.logFilePaths()) {
                if (p != null && !p.isBlank()) {
                    paths.add(Path.of(p));
                }
            }
            return paths;
        }
        if (request != null && request.logFilePath() != null && !request.logFilePath().isBlank()) {
            return List.of(Path.of(request.logFilePath()));
        }
        return List.of(logDirectoryService.activeLogFile());
    }
}
