package com.notificationservice.frontend;

import com.notificationservice.service.SessionRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@RequiredArgsConstructor
public class FrontendWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketNotificationSender webSocketNotificationSender;
    private final SessionRegistryService sessionRegistryService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");

        if (userId != null) {
            String sessionId = session.getId();
            sessionRegistryService.registerSession(userId, sessionId);
            webSocketNotificationSender.addSession(sessionId, session);
            log.info("Frontend client connected. User ID: {}, Session ID: {}", userId, sessionId);
        } else {
            session.close(CloseStatus.POLICY_VIOLATION);
            log.warn("Closing session: userId attribute missing after handshake");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String userId = (String) session.getAttributes().get("userId");

        if (userId != null) {
            String sessionId = session.getId();
            sessionRegistryService.removeSession(userId, sessionId);
            webSocketNotificationSender.removeSession(sessionId);
            log.info("Frontend client disconnected. User ID: {}, Session ID: {}", userId, sessionId);
        }
    }
}

