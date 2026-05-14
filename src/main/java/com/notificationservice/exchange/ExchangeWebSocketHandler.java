package com.notificationservice.exchange;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.notificationservice.domain.MarketEvent;
import com.notificationservice.domain.OrderUpdate;
import com.notificationservice.domain.PriceUpdate;
import com.notificationservice.service.NotificationDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;

/**
 * WebSocket handler for receiving messages from the external stock exchange.
 * Parses incoming messages, maps them to domain objects, and forwards them to appropriate services.
 */
@Slf4j
@RequiredArgsConstructor
public class ExchangeWebSocketHandler extends TextWebSocketHandler {

    private final NotificationDispatcherService notificationDispatcherService;
    private final InternalOrderForwarder internalOrderForwarder;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Connected to exchange WebSocket. Subscribing to channels...");

        String priceSubscription = "{\"type\": \"SUBSCRIBE\", \"payload\": {\"channel\": \"PRICE_FEED\", \"tickers\": [\"ARKA\", \"MNVS\"]}}";
        String orderSubscription = "{\"type\": \"SUBSCRIBE\", \"payload\": {\"channel\": \"ORDER_UPDATES\"}}";
        String marketSubscription = "{\"type\": \"SUBSCRIBE\", \"payload\": {\"channel\": \"MARKET_EVENTS\"}}";

        session.sendMessage(new TextMessage(priceSubscription));
        session.sendMessage(new TextMessage(orderSubscription));
        session.sendMessage(new TextMessage(marketSubscription));

        log.info("Subscription messages sent successfully.");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message from exchange: {}", payload);

        JsonNode json = objectMapper.readTree(payload);
        String messageType = json.get("type").asText();

        switch (messageType) {
            case "CONNECTED" -> handleConnected(json);
            case "PRICE_UPDATE" -> handlePriceUpdate(json);
            case "MARKET_EVENT" -> handleMarketEvent(json);
            case "ORDER_UPDATE" -> handleOrderUpdate(json);
            default -> log.warn("Unknown message type: {}", messageType);
        }
    }

    private void handleConnected(JsonNode json) {
        String platformId = json.get("platform_id").asText();
        String serverMarketTime = json.get("server_market_time").asText();
        log.info("Successfully connected to exchange. Platform ID: {}, Server Market Time: {}", platformId, serverMarketTime);
    }

    private void handlePriceUpdate(JsonNode json) {
        PriceUpdate priceUpdate = PriceUpdate.builder()
                .ticker(json.get("ticker").asText())
                .price(new BigDecimal(json.get("price").asDouble()))
                .change(new BigDecimal(json.get("change").asDouble()))
                .change_pct(new BigDecimal(json.get("change_pct").asDouble()))
                .volume(json.get("volume").asInt())
                .market_time(json.get("market_time").asText())
                .build();

        notificationDispatcherService.dispatchPriceUpdate(priceUpdate);
    }

    private void handleMarketEvent(JsonNode json) {
        MarketEvent marketEvent = MarketEvent.builder()
                .event_id(json.get("event_id").asText())
                .event_type(json.get("event_type").asText())
                .headline(json.get("headline").asText())
                .scope(json.get("scope").asText())
                .target(json.get("target").asText())
                .magnitude(json.get("magnitude").asDouble())
                .duration_ticks(json.get("duration_ticks").asInt())
                .market_time(json.get("market_time").asText())
                .build();

        notificationDispatcherService.dispatchMarketEvent(marketEvent);
    }

    private void handleOrderUpdate(JsonNode json) {
        OrderUpdate orderUpdate = OrderUpdate.builder()
                .order_id(json.get("order_id").asText())
                .status(json.get("status").asText())
                .filled_quantity(json.get("filled_quantity").asInt())
                .average_fill_price(new BigDecimal(json.get("average_fill_price").asDouble()))
                .exchange_fee(new BigDecimal(json.get("exchange_fee").asDouble()))
                .market_time(json.get("market_time").asText())
                .build();

        internalOrderForwarder.forwardOrderUpdate(orderUpdate);
    }
}

