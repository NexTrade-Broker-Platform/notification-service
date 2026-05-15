package com.notificationservice.exchange;

import java.math.BigDecimal;

/**
 * Internal DTO sent to order-service PUT /api/orders/{id}/status.
 * Field names match order-service's OrderStatusUpdateDto exactly.
 */
public record OrderStatusUpdateRequest(
        String status,
        BigDecimal filledQuantity,
        BigDecimal averageFillPrice,
        BigDecimal executionQuantity,
        BigDecimal executionPrice,
        BigDecimal exchangeFee
) {}
