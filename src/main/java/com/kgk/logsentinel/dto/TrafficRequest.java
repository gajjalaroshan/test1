package com.kgk.logsentinel.dto;

import java.util.List;

public record TrafficRequest(
        String customerId, String orderId, double amountInr, List<String> scenarios) {}
