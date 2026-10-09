package com.ghana.dashboard.event;

/**
 * Published whenever something happens that changes a customer's purchase history: a sale is rung up
 * or voided, an order is delivered or cancelled, or a refund is paid out. The customer statistics
 * listener recomputes the numbers from the source tables so they can never drift.
 */
public record CustomerActivityEvent(Long shopId, Long customerId) {
}
