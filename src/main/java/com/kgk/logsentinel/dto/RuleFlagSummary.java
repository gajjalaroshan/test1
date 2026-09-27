package com.kgk.logsentinel.dto;

import com.kgk.logsentinel.dto.LogAnalysisResult;

import java.util.List;

/** Top-level view of Java rule outcomes for demos and agents. */
public record RuleFlagSummary(
        List<String> flaggedCustomerIds,
        List<String> flaggedOrderIds,
        List<LogAnalysisResult.FlaggedCustomer> flaggedCustomers,
        List<LogAnalysisResult.FlaggedOrder> flaggedOrders) {

    public static RuleFlagSummary from(LogAnalysisResult result) {
        List<LogAnalysisResult.FlaggedCustomer> customers = result.flaggedCustomers();
        List<LogAnalysisResult.FlaggedOrder> orders = result.flaggedOrders();
        return new RuleFlagSummary(
                customers.stream().map(LogAnalysisResult.FlaggedCustomer::customerId).distinct().toList(),
                orders.stream().map(LogAnalysisResult.FlaggedOrder::orderId).distinct().toList(),
                customers,
                orders);
    }
}
