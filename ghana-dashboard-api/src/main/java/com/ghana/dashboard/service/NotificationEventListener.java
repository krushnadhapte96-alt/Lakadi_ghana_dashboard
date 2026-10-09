package com.ghana.dashboard.service;

import com.ghana.dashboard.dto.NotificationDtos.NewNotification;
import com.ghana.dashboard.dto.NotificationDtos.NotificationType;
import com.ghana.dashboard.dto.NotificationDtos.Severity;
import com.ghana.dashboard.event.OrderPlacedEvent;
import com.ghana.dashboard.event.StockLevelChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Turns in-app events into notifications.
 *
 * <p>These listeners run synchronously inside the publishing transaction, which is the behaviour we
 * want: if the sale or order rolls back, the notification it produced rolls back with it instead of
 * alerting the shop about something that never happened.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notifications;
    private final InventoryService inventory;

    public NotificationEventListener(NotificationService notifications, InventoryService inventory) {
        this.notifications = notifications;
        this.inventory = inventory;
    }

    /**
     * Fires when a sale leaves a product below its low-stock threshold.
     *
     * <p>The threshold is compared against available + reserved, matching the dashboard's own low
     * stock card, so the bell and the home screen can never disagree about whether something is
     * short. Only a crossing notifies: a product that was already below its threshold before this
     * sale has been reported already, and the 24-hour de-duplication would suppress it anyway.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void onStockLevelChanged(StockLevelChangedEvent event) {
        BigDecimal threshold = event.threshold();
        if (threshold == null) {
            return;
        }
        BigDecimal after = inventory.getAvailableStock(event.shopId(), event.productId());

        boolean crossed = event.availableBefore() != null
                && event.availableBefore().compareTo(threshold) >= 0
                && after.compareTo(threshold) < 0;
        if (!crossed) {
            return;
        }

        notifications.create(new NewNotification(
                event.shopId(),
                null,
                NotificationType.LOW_STOCK,
                Severity.WARNING,
                "Low stock: " + event.productName(),
                "Stock fell to " + after.stripTrailingZeros().toPlainString() + " (threshold "
                        + threshold.stripTrailingZeros().toPlainString() + ") after a sale.",
                "/inventory?filter=low&product=" + event.productId(),
                "LOW_STOCK:" + event.productId()));
    }

    /**
     * A new order is addressed to the whole shop rather than one user: whoever is free should pick
     * it up, and every device gets the sound.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void onOrderPlaced(OrderPlacedEvent event) {
        String customer = event.customerName() == null || event.customerName().isBlank()
                ? "Walk-in customer"
                : event.customerName();
        String amount = event.totalAmount() == null
                ? ""
                : " for " + event.totalAmount().stripTrailingZeros().toPlainString();

        notifications.create(new NewNotification(
                event.shopId(),
                null,
                NotificationType.NEW_ORDER,
                Severity.INFO,
                "New order " + event.billNo(),
                customer + " placed " + event.itemCount() + " item(s)" + amount + ".",
                "/orders?order=" + event.orderId(),
                "NEW_ORDER:" + event.orderId()));
        log.debug("Queued new-order notification for order {}", event.orderId());
    }
}