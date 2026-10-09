package com.ghana.dashboard.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Published when a new customer order is placed. This is the one notification that is meant to
 * interrupt: the bell plays a sound for it, so every device in the shop should hear about a new
 * order the moment it arrives.
 */
public record OrderPlacedEvent(
        Long shopId,
        Long orderId,
        String billNo,
        String customerName,
        BigDecimal totalAmount,
        int itemCount,
        LocalDate expectedDeliveryDate
) {
}