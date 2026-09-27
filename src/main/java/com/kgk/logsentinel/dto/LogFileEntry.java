package com.kgk.logsentinel.dto;

public record LogFileEntry(String path, String name, long size, long lastModified) {}
