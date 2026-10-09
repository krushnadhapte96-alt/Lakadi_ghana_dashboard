package com.ghana.dashboard.event;

/** Published when a sale/order is voided so dependent ledgers (khata) can reverse their entries. */
public record SaleVoidedEvent(Long shopId, Long transactionId, String transactionType) {
}
