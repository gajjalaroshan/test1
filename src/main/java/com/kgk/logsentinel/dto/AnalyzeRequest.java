package com.kgk.logsentinel.dto;

import java.util.List;

public record AnalyzeRequest(String logFilePath, List<String> logFilePaths, Boolean useLlm) {

    public boolean shouldUseLlm() {
        return useLlm == null || useLlm;
    }
}
