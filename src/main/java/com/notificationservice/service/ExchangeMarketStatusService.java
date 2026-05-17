package com.notificationservice.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Service
public class ExchangeMarketStatusService {

    private static final String STATUS_CONNECTED = "CONNECTED";
    private static final String STATUS_DISCONNECTED = "DISCONNECTED";

    private final Object lock = new Object();

    private String connectionStatus = STATUS_DISCONNECTED;
    private boolean exchangeConnected;
    private String platformId;
    private String lastSyncMarketTime;
    private String lastSyncAt;
    private BigDecimal speedMultiplier;

    public void recordConnected(String platformId, String marketTime) {
        synchronized (lock) {
            connectionStatus = STATUS_CONNECTED;
            exchangeConnected = true;
            this.platformId = platformId;
            updateSyncTimes(marketTime);
        }
    }

    public void recordDisconnected() {
        synchronized (lock) {
            connectionStatus = STATUS_DISCONNECTED;
            exchangeConnected = false;
            touchSyncClock();
        }
    }

    public void recordMarketTime(String marketTime) {
        synchronized (lock) {
            updateSyncTimes(marketTime);
        }
    }

    public ExchangeMarketStatusSnapshot snapshot() {
        synchronized (lock) {
            return new ExchangeMarketStatusSnapshot(
                    connectionStatus,
                    exchangeConnected,
                    platformId,
                    lastSyncMarketTime,
                    lastSyncAt,
                    lastSyncMarketTime,
                    toMarketDate(lastSyncMarketTime),
                    speedMultiplier
            );
        }
    }

    private void updateSyncTimes(String marketTime) {
        if (marketTime != null && !marketTime.isBlank()) {
            lastSyncMarketTime = marketTime;
        }
        touchSyncClock();
    }

    private void touchSyncClock() {
        lastSyncAt = Instant.now().toString();
    }

    private String toMarketDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(raw).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(raw).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(raw).toString();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record ExchangeMarketStatusSnapshot(
            String connectionStatus,
            boolean exchangeConnected,
            String platformId,
            String lastSyncMarketTime,
            String lastSyncAt,
            String marketTime,
            String marketDate,
            BigDecimal speedMultiplier
    ) {
    }
}
