package com.notificationservice.config;

import com.notificationservice.frontend.FrontendWebSocketHandler;
import com.notificationservice.frontend.WebSocketNotificationSender;
import com.notificationservice.service.SessionRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketServerConfig implements WebSocketConfigurer {

    private final WebSocketNotificationSender webSocketNotificationSender;
    private final SessionRegistryService sessionRegistryService;
    private final JwtValidator jwtValidator;

    @Value("${cors.allowed-origin-patterns}")
    private String allowedOriginPatterns;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        FrontendWebSocketHandler handler = new FrontendWebSocketHandler(
                webSocketNotificationSender,
                sessionRegistryService
        );

        registry.addHandler(handler, "/ws/notifications")
                .setAllowedOriginPatterns(allowedOriginPatterns.split(","))
                .addInterceptors(new JwtHandshakeInterceptor(jwtValidator));
    }
}
