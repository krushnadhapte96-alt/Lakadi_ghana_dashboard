package com.ghana.dashboard.event;

import java.math.BigDecimal;

/**
 * Published once per product touched by a sale, immediately after FIFO stock was deducted.
 *
 * <p>The pre-deduction level travels with the event so the listener can tell a genuine threshold
 * crossing from a product that was already short before this sale - only the former is news worth
 * interrupting someone for. The post-deduction level is deliberately not included: the listener
 * re-reads it inside the same transaction, which is what makes the check accurate for a sale that
 * touched the same product on several lines.
 */
public record StockLevelChangedEvent(
        Long shopId,
        Long productId,
        String productName,
        String sku,
        BigDecimal availableBefore,
        BigDecimal threshold
) {
}