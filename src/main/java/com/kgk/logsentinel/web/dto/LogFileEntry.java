package com.kgk.logsentinel.web.dto;

public record LogFileEntry(String path, String name, long size, long lastModified) {}
