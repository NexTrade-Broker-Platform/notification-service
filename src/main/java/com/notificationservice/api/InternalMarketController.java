package com.notificationservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.service.ExchangeMarketStatusService;
import com.notificationservice.service.MarketEventCacheService;
import com.notificationservice.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.*;

@RestController
@RequestMapping("/internal/market")
@RequiredArgsConstructor
@Slf4j
public class InternalMarketController {

    private final StockCacheService stockCacheService;
    private final MarketEventCacheService marketEventCacheService;
    private final ExchangeMarketStatusService exchangeMarketStatusService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${exchange.rest-url}")
    private String exchangeRestUrl;

    @Value("${exchange.api-key}")
    private String exchangeApiKey;

    @Value("${exchange.api-secret}")
    private String exchangeApiSecret;

    @GetMapping("/stocks")
    public ResponseEntity<Map<String, Object>> getStocks() {
        return ResponseEntity.ok(Map.of("stocks", stockCacheService.getAll()));
    }

    @GetMapping("/stocks/{ticker}")
    public ResponseEntity<Map<String, Object>> getStockDetail(@PathVariable String ticker) {
        return stockCacheService.get(ticker.toUpperCase())
                .map(stock -> {
                    List<Map<String, Object>> history = fetchHistory(ticker.toUpperCase());
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("stock", stock);
                    body.put("chart_data", history);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().<Map<String, Object>>build());
    }

    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> getMarketEvents() {
        return ResponseEntity.ok(Map.of("events", marketEventCacheService.getAll()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMarketStatus() {
        return ResponseEntity.ok(exchangeMarketStatusService.snapshot());
    }

    private HttpEntity<Void> exchangeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("API-KEY", exchangeApiKey);
        headers.set("API-SECRET", exchangeApiSecret);
        return new HttpEntity<>(headers);
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> getOptions() {
        try {
            String url = exchangeRestUrl + "/api/v1/market/options";
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, exchangeHeaders(), String.class);
            String response = resp.getBody();
            if (response == null) return ResponseEntity.ok(Map.of("options", List.of()));
            com.fasterxml.jackson.databind.JsonNode array = objectMapper.readTree(response);
            List<Map<String, Object>> options = new ArrayList<>();
            if (array.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : array) {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("option_id",         node.path("option_id").asText());
                    option.put("underlying_ticker",  node.path("underlying_ticker").asText());
                    option.put("option_type",        node.path("option_type").asText());
                    option.put("strike_price",       node.path("strike_price").asDouble());
                    option.put("expiry_time",        node.path("expiry_time").asText());
                    option.put("premium",            node.path("premium").asDouble());
                    option.put("is_active",          node.path("is_active").asBoolean());
                    options.add(option);
                }
            }
            return ResponseEntity.ok(Map.of("options", options));
        } catch (Exception e) {
            log.warn("Failed to fetch options from exchange: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("options", List.of()));
        }
    }

    private List<Map<String, Object>> fetchHistory(String ticker) {
        try {
            String url = exchangeRestUrl + "/api/v1/market/stocks/" + ticker + "/history";
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, exchangeHeaders(), String.class);
            String response = resp.getBody();
            if (response == null) return List.of();
            JsonNode array = objectMapper.readTree(response);
            List<Map<String, Object>> result = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode node : array) {
                    Map<String, Object> candle = new LinkedHashMap<>();
                    candle.put("timestamp", node.path("timestamp").asText());
                    candle.put("open", node.path("open_price").asDouble());
                    candle.put("high", node.path("high_price").asDouble());
                    candle.put("low", node.path("low_price").asDouble());
                    candle.put("close", node.path("current_price").asDouble());
                    candle.put("volume", node.path("volume").asLong());
                    result.add(candle);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch history for {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }
}
