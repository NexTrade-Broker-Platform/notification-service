package com.notificationservice.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.core.NotificationSender;
import com.notificationservice.domain.MessageEnvelope;
import com.notificationservice.service.SessionRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of NotificationSender for WebSocket-based communication with frontend clients.
 * Manages active WebSocket sessions and sends messages to specific users or broadcasts to all.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketNotificationSender implements NotificationSender {

    private final SessionRegistryService sessionRegistryService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Adds a new WebSocket session to the active sessions map.
     *
     * @param sessionId The unique identifier for the session.
     * @param session   The WebSocketSession to register.
     */
    public void addSession(String sessionId, WebSocketSession session) {
        activeSessions.put(sessionId, session);
        log.debug("Added WebSocket session: {}", sessionId);
    }

    /**
     * Removes a WebSocket session from the active sessions map.
     *
     * @param sessionId The unique identifier for the session.
     */
    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
        log.debug("Removed WebSocket session: {}", sessionId);
    }

    @Override
    public void sendToUser(String platformUserId, MessageEnvelope<?> message) {
        Set<String> sessionIds = sessionRegistryService.getActiveSessions(platformUserId);

        for (String sessionId : sessionIds) {
            WebSocketSession session = activeSessions.get(sessionId);
            if (session != null && session.isOpen()) {
                sendMessage(session, message);
            }
        }
    }

    @Override
    public void broadcast(MessageEnvelope<?> message) {
        for (WebSocketSession session : activeSessions.values()) {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        }
    }

    private void sendMessage(WebSocketSession session, MessageEnvelope<?> message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
            log.debug("Message sent to session: {}", session.getId());
        } catch (IllegalStateException e) {
            log.warn("Failed to send message to session {} (already closed): {}", session.getId(), e.getMessage());
        } catch (IOException e) {
            log.error("Failed to send message to session: {}", session.getId(), e);
        }
    }
}

