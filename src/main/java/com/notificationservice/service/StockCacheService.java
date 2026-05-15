package com.notificationservice.service;

import com.notificationservice.domain.StockData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockCacheService {

    private final Map<String, StockData> cache = new ConcurrentHashMap<>();

    public void put(StockData stock) {
        cache.put(stock.getTicker(), stock);
    }

    public void updatePrice(String ticker, BigDecimal price, int volume) {
        StockData existing = cache.get(ticker);
        if (existing != null) {
            existing.setCurrentPrice(price);
            if (volume > 0) existing.setVolume(volume);
        }
    }

    public List<StockData> getAll() {
        return new ArrayList<>(cache.values());
    }

    public Optional<StockData> get(String ticker) {
        return Optional.ofNullable(cache.get(ticker));
    }

    public boolean isEmpty() {
        return cache.isEmpty();
    }
}
