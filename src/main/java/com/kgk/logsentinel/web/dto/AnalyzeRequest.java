package com.kgk.logsentinel.web.dto;

public record AnalyzeRequest(String logFilePath, Boolean useLlm) {}
