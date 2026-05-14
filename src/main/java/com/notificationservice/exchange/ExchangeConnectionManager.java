package com.notificationservice.exchange;

import tools.jackson.databind.ObjectMapper;
import com.notificationservice.service.NotificationDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExchangeConnectionManager implements CommandLineRunner {

    private final NotificationDispatcherService notificationDispatcherService;
    private final InternalOrderForwarder internalOrderForwarder;
    private final ObjectMapper objectMapper;

    @Value("${exchange.ws-host}")
    private String exchangeWsHost;

    @Value("${exchange.api-key}")
    private String exchangeApiKey;

    @Value("${exchange.api-secret}")
    private String exchangeApiSecret;

    @Override
    public void run(String... args) {
        log.info("Initializing exchange WebSocket connection...");

        new Thread(() -> {
            try {
                StandardWebSocketClient client = new StandardWebSocketClient();
                String exchangeUri = exchangeWsHost + "?api_key=" + exchangeApiKey + "&api_secret=" + exchangeApiSecret;

                ExchangeWebSocketHandler handler = new ExchangeWebSocketHandler(
                        notificationDispatcherService,
                        internalOrderForwarder,
                        objectMapper
                );

                client.execute(handler, exchangeUri).get();
                log.info("Successfully connected to exchange WebSocket");
            } catch (Exception e) {
                log.error("Failed to connect to exchange", e);
            }
        }).start();
    }
}

