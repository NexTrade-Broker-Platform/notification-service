package com.notificationservice.service;

import com.notificationservice.core.NotificationSender;
import com.notificationservice.domain.MarketEvent;
import com.notificationservice.domain.MessageEnvelope;
import com.notificationservice.domain.OrderUpdate;
import com.notificationservice.domain.PriceUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for dispatching various types of notifications to users.
 * It uses a {@link NotificationSender} to send messages and may consult the
 * {@link SessionRegistryService} to determine active sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcherService {

    private final NotificationSender notificationSender;
    private final SessionRegistryService sessionRegistryService;

    /**
     * Dispatches an order update to a specific user.
     *
     * @param platformUserId The ID of the user to notify.
     * @param update         The order update payload.
     */
    public void dispatchOrderUpdate(String platformUserId, OrderUpdate update) {
        log.info("DISPATCHING ORDER_UPDATE: order={}, user={}, status={}, type={}", 
            update.getOrder_id(), platformUserId, update.getStatus(), update.getOrder_type());
        MessageEnvelope<OrderUpdate> envelope = MessageEnvelope.<OrderUpdate>builder()
                .type("ORDER_UPDATE")
                .payload(update)
                .build();
        notificationSender.sendToUser(platformUserId, envelope);
    }

    /**
     * Dispatches a price update to all active users (broadcast).
     *
     * @param update The price update payload.
     */
    public void dispatchPriceUpdate(PriceUpdate update) {
        MessageEnvelope<PriceUpdate> envelope = MessageEnvelope.<PriceUpdate>builder()
                .type("PRICE_UPDATE")
                .payload(update)
                .build();
        notificationSender.broadcast(envelope);
    }

    /**
     * Dispatches a market event to all active users (broadcast).
     *
     * @param event The market event payload.
     */
    public void dispatchMarketEvent(MarketEvent event) {
        MessageEnvelope<MarketEvent> envelope = MessageEnvelope.<MarketEvent>builder()
                .type("MARKET_EVENT")
                .payload(event)
                .build();
        notificationSender.broadcast(envelope);
    }
}
