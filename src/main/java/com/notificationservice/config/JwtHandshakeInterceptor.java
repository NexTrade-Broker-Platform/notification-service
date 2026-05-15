package com.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtValidator jwtValidator;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token != null) {
            String userId = jwtValidator.extractUserId(token);
            if (userId != null) {
                attributes.put("userId", userId);
                log.debug("Authenticated WebSocket handshake for user: {}", userId);
            } else {
                log.debug("WebSocket handshake with invalid/expired token");
            }
        } else {
            log.debug("Anonymous WebSocket handshake");
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}
