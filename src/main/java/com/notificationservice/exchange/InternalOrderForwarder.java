package com.notificationservice.exchange;

import com.notificationservice.domain.OrderUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class InternalOrderForwarder {

    private final RestTemplate restTemplate;

    @Value("${internal.api-key}")
    private String internalApiKey;

    public void forwardOrderUpdate(OrderUpdate orderUpdate) {
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

            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class, orderUpdate.getOrder_id());
            log.info("Order update forwarded successfully for order: {}", orderUpdate.getOrder_id());
        } catch (Exception e) {
            log.error("Failed to forward order update for order: {}", orderUpdate.getOrder_id(), e);
        }
    }
}
