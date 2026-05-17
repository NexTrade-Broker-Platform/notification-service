package com.notificationservice.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.domain.MarketEvent;
import com.notificationservice.domain.OrderUpdate;
import com.notificationservice.domain.PriceUpdate;
import com.notificationservice.service.ExchangeMarketStatusService;
import com.notificationservice.service.MarketEventCacheService;
import com.notificationservice.service.NotificationDispatcherService;
import com.notificationservice.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ExchangeWebSocketHandler extends TextWebSocketHandler {

    private final NotificationDispatcherService notificationDispatcherService;
    private final InternalOrderForwarder internalOrderForwarder;
    private final ObjectMapper objectMapper;
    private final StockCacheService stockCacheService;
    private final MarketEventCacheService marketEventCacheService;
    private final ExchangeMarketStatusService exchangeMarketStatusService;
    private final List<String> tickers;
    private final Runnable onConnected;
    private final Runnable onDisconnect;

    private final AtomicBoolean disconnectFired = new AtomicBoolean(false);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Connected to exchange WebSocket. Subscribing to {} tickers...", tickers.size());
        onConnected.run();

        String priceSubscription;
        try {
            // Create the payload for the subscription message
            java.util.Map<String, Object> payloadMap = new java.util.HashMap<>();
            payloadMap.put("channel", "PRICE_FEED");
            payloadMap.put("tickers", tickers);

            // Create the top-level message envelope
            java.util.Map<String, Object> messageMap = new java.util.HashMap<>();
            messageMap.put("type", "SUBSCRIBE");
            messageMap.put("payload", payloadMap);

            priceSubscription = objectMapper.writeValueAsString(messageMap);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to serialize price subscription message", e);
            // Handle error appropriately, maybe close session or return
            return;
        }
        String orderSubscription = "{\"type\": \"SUBSCRIBE\", \"payload\": {\"channel\": \"ORDER_UPDATES\"}}";
        String marketSubscription = "{\"type\": \"SUBSCRIBE\", \"payload\": {\"channel\": \"MARKET_EVENTS\"}}";

        if (!tickers.isEmpty()) {
            session.sendMessage(new TextMessage(priceSubscription));
        } else {
            log.warn("No tickers available — skipping PRICE_FEED subscription");
        }
        session.sendMessage(new TextMessage(orderSubscription));
        session.sendMessage(new TextMessage(marketSubscription));

        log.info("Subscribed to PRICE_FEED for: {}", tickers);
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
        JsonNode p = json.path("payload");
        exchangeMarketStatusService.recordConnected(
                p.path("platform_id").asText(null),
                p.path("server_market_time").asText(null)
        );
        log.info("Connected to exchange. Platform ID: {}, Server Market Time: {}",
                p.path("platform_id").asText("?"),
                p.path("server_market_time").asText("?"));
    }

    private void handlePriceUpdate(JsonNode json) {
        JsonNode p = json.path("payload");
        String ticker = p.path("ticker").asText();
        BigDecimal price = new BigDecimal(p.path("price").asText("0"));
        int volume = (int) p.path("volume").asLong();
        exchangeMarketStatusService.recordMarketTime(p.path("market_time").asText(null));

        stockCacheService.updatePrice(ticker, price, volume);

        PriceUpdate priceUpdate = PriceUpdate.builder()
                .ticker(ticker)
                .price(price)
                .change(new BigDecimal(p.path("change").asText("0")))
                .change_pct(new BigDecimal(p.path("change_pct").asText("0")))
                .volume(volume)
                .market_time(p.path("market_time").asText())
                .build();

        notificationDispatcherService.dispatchPriceUpdate(priceUpdate);
    }

    private void handleMarketEvent(JsonNode json) {
        JsonNode p = json.path("payload");
        exchangeMarketStatusService.recordMarketTime(p.path("market_time").asText(null));
        MarketEvent marketEvent = MarketEvent.builder()
                .event_id(p.path("event_id").asText())
                .event_type(p.path("event_type").asText())
                .headline(p.path("headline").asText())
                .scope(p.path("scope").asText())
                .target(p.path("target").asText())
                .magnitude(p.path("magnitude").asDouble())
                .duration_ticks((int) p.path("duration_ticks").asLong())
                .market_time(p.path("market_time").asText())
                .build();

        marketEventCacheService.add(marketEvent);
        notificationDispatcherService.dispatchMarketEvent(marketEvent);
    }

    private void handleOrderUpdate(JsonNode json) {
        JsonNode p = json.path("payload");
        exchangeMarketStatusService.recordMarketTime(p.path("market_time").asText(null));
        OrderUpdate orderUpdate = OrderUpdate.builder()
                .order_id(p.path("order_id").asText())
                .platform_user_id(p.has("platform_user_id") ? p.path("platform_user_id").asText() : null)
                .status(p.path("status").asText())
                .filled_quantity((int) p.path("filled_quantity").asLong())
                .average_fill_price(new BigDecimal(p.path("average_fill_price").asText("0")))
                .exchange_fee(new BigDecimal(p.path("exchange_fee").asText("0")))
                .market_time(p.path("market_time").asText())
                .build();

        OrderUpdate enriched = internalOrderForwarder.forwardOrderUpdate(orderUpdate);

        if (enriched.getPlatform_user_id() != null && !enriched.getPlatform_user_id().isEmpty()) {
            notificationDispatcherService.dispatchOrderUpdate(enriched.getPlatform_user_id(), enriched);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("Exchange WebSocket closed: {}", status);
        exchangeMarketStatusService.recordDisconnected();
        fireDisconnect();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Exchange WebSocket transport error: {}", exception.getMessage());
        try {
            session.close();
        } catch (Exception ignored) {}
        exchangeMarketStatusService.recordDisconnected();
        fireDisconnect();
    }

    private void fireDisconnect() {
        if (disconnectFired.compareAndSet(false, true)) {
            onDisconnect.run();
        }
    }
}
