package com.notificationservice.api;

import com.notificationservice.domain.OrderUpdate;
import com.notificationservice.service.NotificationDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
@Slf4j
public class InternalOrderController {

    private final NotificationDispatcherService notificationDispatcherService;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private void validateKey(String key) {
        if (!Objects.equals(internalApiKey, key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid secret API key");
        }
    }

    /**
     * Called by order-service immediately after a new order is saved, so the
     * frontend receives an ORDER_UPDATE PENDING before the exchange does anything.
     * This makes LIMIT BUY (spoof) orders visible in the bot dashboard/chart.
     */
    @PostMapping("/placed")
    public ResponseEntity<Void> notifyOrderPlaced(
            @RequestHeader("X-INTERNAL-KEY") String key,
            @RequestBody Map<String, String> body) {

        validateKey(key);

        String orderId        = body.get("order_id") != null ? body.get("order_id") : body.get("orderId");
        String platformUserId = body.get("platform_user_id") != null ? body.get("platform_user_id") : body.get("platformUserId");
        String side           = body.get("side");
        String orderType      = body.get("order_type") != null ? body.get("order_type") : body.get("orderType");
        String instrumentId   = body.get("instrument_id") != null ? body.get("instrument_id") : body.get("instrumentId");

        if (platformUserId == null || platformUserId.isBlank()) {
            log.warn("notifyOrderPlaced: missing platformUserId for order {}", orderId);
            return ResponseEntity.badRequest().build();
        }

        OrderUpdate update = OrderUpdate.builder()
                .order_id(orderId)
                .platform_user_id(platformUserId)
                .status("PENDING")
                .filled_quantity(0)
                .average_fill_price(BigDecimal.ZERO)
                .exchange_fee(BigDecimal.ZERO)
                .market_time(LocalDateTime.now().toString())
                .side(side)
                .order_type(orderType)
                .instrument_id(instrumentId)
                .build();

        notificationDispatcherService.dispatchOrderUpdate(platformUserId, update);
        log.info("Dispatched ORDER_UPDATE PENDING for order {} to user {}", orderId, platformUserId);
        return ResponseEntity.ok().build();
    }
}
