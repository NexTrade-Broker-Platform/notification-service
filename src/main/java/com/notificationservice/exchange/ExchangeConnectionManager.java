package com.notificationservice.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.domain.StockData;
import com.notificationservice.service.ExchangeMarketStatusService;
import com.notificationservice.service.MarketEventCacheService;
import com.notificationservice.service.NotificationDispatcherService;
import com.notificationservice.service.StockCacheService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExchangeConnectionManager implements CommandLineRunner {

    private final NotificationDispatcherService notificationDispatcherService;
    private final InternalOrderForwarder internalOrderForwarder;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final StockCacheService stockCacheService;
    private final MarketEventCacheService marketEventCacheService;
    private final ExchangeMarketStatusService exchangeMarketStatusService;

    @Value("${exchange.ws-host}")
    private String exchangeWsHost;

    @Value("${exchange.api-key}")
    private String exchangeApiKey;

    @Value("${exchange.api-secret}")
    private String exchangeApiSecret;

    @Value("${exchange.rest-url}")
    private String exchangeRestUrl;

    private static final int RETRY_BASE_SECONDS = 1;
    private static final int RETRY_MAX_SECONDS = 30;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger retryDelay = new AtomicInteger(RETRY_BASE_SECONDS);

    @Override
    public void run(String... args) {
        log.info("Initializing exchange WebSocket connection...");
        connect();
    }

    private void connect() {
        try {
            List<String> tickers = fetchAllTickers();
            log.info("Connecting to exchange WS, {} tickers", tickers.size());

            StandardWebSocketClient client = new StandardWebSocketClient();
            String exchangeUri = exchangeWsHost + "?api_key=" + exchangeApiKey + "&api_secret=" + exchangeApiSecret;

            ExchangeWebSocketHandler handler = new ExchangeWebSocketHandler(
                    notificationDispatcherService,
                    internalOrderForwarder,
                    objectMapper,
                    stockCacheService,
                    marketEventCacheService,
                    exchangeMarketStatusService,
                    tickers,
                    this::onConnected,
                    this::scheduleReconnect
            );

            client.execute(handler, exchangeUri).whenComplete((session, ex) -> {
                if (ex != null) {
                    log.error("Exchange WS connection failed: {}", ex.getMessage());
                    scheduleReconnect();
                }
            });
        } catch (Exception e) {
            log.error("Unexpected error initiating exchange connection: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void onConnected() {
        retryDelay.set(RETRY_BASE_SECONDS);
    }

    void scheduleReconnect() {
        int delay = retryDelay.get();
        log.warn("Exchange WS disconnected — reconnecting in {}s", delay);
        scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
        retryDelay.set(Math.min(delay * 2, RETRY_MAX_SECONDS));
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private HttpEntity<Void> exchangeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("API-KEY", exchangeApiKey);
        headers.set("API-SECRET", exchangeApiSecret);
        return new HttpEntity<>(headers);
    }

    private List<String> fetchAllTickers() {
        try {
            String url = exchangeRestUrl + "/api/v1/market/stocks";
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, exchangeHeaders(), String.class);
            String response = resp.getBody();
            if (response == null) return List.of();

            JsonNode array = objectMapper.readTree(response);
            List<String> tickers = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode node : array) {
                    String ticker = node.path("ticker").asText(null);
                    if (ticker != null && !ticker.isBlank()) {
                        tickers.add(ticker);
                        cacheStock(node, ticker);
                    }
                }
            }
            log.info("Cached {} stocks from exchange", stockCacheService.getAll().size());
            return tickers;
        } catch (Exception e) {
            log.error("Failed to fetch tickers from exchange REST API: {}", e.getMessage());
            return List.of();
        }
    }

    private void cacheStock(JsonNode node, String ticker) {
        try {
            StockData stock = StockData.builder()
                    .ticker(ticker)
                    .name(node.path("name").asText(""))
                    .sector(node.path("sector").asText(""))
                    .currentPrice(new BigDecimal(node.path("current_price").asText("0")))
                    .openPrice(new BigDecimal(node.path("open_price").asText("0")))
                    .highPrice(new BigDecimal(node.path("high_price").asText("0")))
                    .lowPrice(new BigDecimal(node.path("low_price").asText("0")))
                    .volume(node.path("volume").asLong(0))
                    .listedAt(node.path("listed_at").asText(""))
                    .build();
            stockCacheService.put(stock);
        } catch (Exception e) {
            log.warn("Failed to cache stock {}: {}", ticker, e.getMessage());
        }
    }
}
