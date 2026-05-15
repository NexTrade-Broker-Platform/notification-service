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
        String sessionId = session.getId();
        webSocketNotificationSender.addSession(sessionId, session);

        String userId = (String) session.getAttributes().get("userId");
        if (userId != null && !userId.isEmpty()) {
            sessionRegistryService.registerSession(userId, sessionId);
            log.info("Authenticated frontend client connected. User ID: {}, Session ID: {}", userId, sessionId);
        } else {
            log.info("Anonymous frontend client connected. Session ID: {}", sessionId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        webSocketNotificationSender.removeSession(sessionId);

        String userId = (String) session.getAttributes().get("userId");
        if (userId != null && !userId.isEmpty()) {
            sessionRegistryService.removeSession(userId, sessionId);
            log.info("Authenticated frontend client disconnected. User ID: {}, Session ID: {}", userId, sessionId);
        } else {
            log.info("Anonymous frontend client disconnected. Session ID: {}", sessionId);
        }
    }
}

