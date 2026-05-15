package com.notificationservice.service;

import com.notificationservice.domain.MarketEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Service
public class MarketEventCacheService {

    private static final int MAX_SIZE = 100;
    private final LinkedList<MarketEvent> events = new LinkedList<>();

    public synchronized void add(MarketEvent event) {
        events.addFirst(event);
        if (events.size() > MAX_SIZE) {
            events.removeLast();
        }
    }

    public synchronized List<MarketEvent> getAll() {
        return new ArrayList<>(events);
    }
}
