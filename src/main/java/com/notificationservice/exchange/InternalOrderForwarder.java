package com.notificationservice.exchange;

import com.notificationservice.domain.OrderUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class InternalOrderForwarder {

    private final RestTemplate restTemplate;

    @Value("${internal.api-key}")
    private String internalApiKey;

    public OrderUpdate forwardOrderUpdate(OrderUpdate orderUpdate) {
        try {
            String url = "http://order-service:9003/api/orders/{orderId}/status";

            BigDecimal filledQty = orderUpdate.getFilled_quantity() != null
                    ? new BigDecimal(orderUpdate.getFilled_quantity())
                    : BigDecimal.ZERO;

            OrderStatusUpdateRequest body = new OrderStatusUpdateRequest(
                    orderUpdate.getStatus(),
                    filledQty,
                    orderUpdate.getAverage_fill_price(),
                    filledQty,
                    orderUpdate.getAverage_fill_price(),
                    orderUpdate.getExchange_fee()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-INTERNAL-KEY", internalApiKey);
            headers.set("Content-Type", "application/json");
            HttpEntity<OrderStatusUpdateRequest> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                    url, HttpMethod.PUT, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {},
                    orderUpdate.getOrder_id()
            );
            log.info("Order update forwarded successfully for order: {}", orderUpdate.getOrder_id());

            Map<String, Object> orderData = response.getBody();
            if (orderData != null) {
                Object userId = orderData.get("platformUserId");
                return OrderUpdate.builder()
                        .order_id(orderUpdate.getOrder_id())
                        .platform_user_id(userId != null ? userId.toString() : orderUpdate.getPlatform_user_id())
                        .status(orderUpdate.getStatus())
                        .filled_quantity(orderUpdate.getFilled_quantity())
                        .average_fill_price(orderUpdate.getAverage_fill_price())
                        .exchange_fee(orderUpdate.getExchange_fee())
                        .market_time(orderUpdate.getMarket_time())
                        .side((String) orderData.get("side"))
                        .order_type((String) orderData.get("orderType"))
                        .instrument_id((String) orderData.get("instrumentId"))
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to forward order update for order: {}", orderUpdate.getOrder_id(), e);
        }

        return orderUpdate;
    }
}
