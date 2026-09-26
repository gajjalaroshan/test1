package com.kgk.logsentinel.service.logging;

import jakarta.servlet.http.HttpServletRequest;

public final class ApiRequestContext {

    public static final String CUSTOMER_ID = "logsentinel.customerId";
    public static final String ORDER_ID = "logsentinel.orderId";
    public static final String AMOUNT_INR = "logsentinel.amountInr";

    private ApiRequestContext() {}

    public static void bindTraffic(
            HttpServletRequest request, String customerId, String orderId, double amountInr) {
        request.setAttribute(CUSTOMER_ID, customerId);
        request.setAttribute(ORDER_ID, orderId);
        request.setAttribute(AMOUNT_INR, String.valueOf(amountInr));
    }
}
