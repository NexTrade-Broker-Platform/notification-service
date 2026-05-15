package com.notificationservice.domain;

import lombok.Value;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Represents an update on the status of a user's order.
 * This DTO is immutable and created using the Builder pattern.
 */
@Value
@Builder
public class OrderUpdate {

    /**
     * The unique identifier for the order.
     */
    String order_id;

    /**
     * The platform user ID who owns the order.
     */
    String platform_user_id;

    /**
     * The current status of the order (e.g., "FILLED", "PARTIALLY_FILLED", "CANCELED").
     */
    String status;

    /**
     * The number of shares or units that have been filled.
     */
    Integer filled_quantity;

    /**
     * The average price at which the order was filled.
     */
    BigDecimal average_fill_price;

    /**
     * Any exchange fees incurred for the transaction.
     */
    BigDecimal exchange_fee;

    /**
     * The timestamp of the last order update, in ISO-8601 format.
     */
    String market_time;
}
