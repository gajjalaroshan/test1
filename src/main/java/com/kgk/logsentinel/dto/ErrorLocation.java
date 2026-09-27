package com.kgk.logsentinel.dto;

/**
 * Source location derived from a stack trace frame (column is rarely present in Java stacks).
 */
public record ErrorLocation(String className, Integer line, Integer column) {}
