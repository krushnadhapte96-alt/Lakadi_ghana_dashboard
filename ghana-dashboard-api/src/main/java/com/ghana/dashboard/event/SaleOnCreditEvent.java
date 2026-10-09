package com.ghana.dashboard.event;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when a sale or order is created with UDHAAR payment mode.
 * This event will be consumed in Prompt 14 for credit management.
 */
public class SaleOnCreditEvent extends ApplicationEvent {

    private final Long shopId;
    private final Long transactionId;
    private final String billNo;
    private final Long customerId;
    private final String customerName;
    private final String customerPhone;
    private final BigDecimal totalAmount;
    private final String transactionType; // SALE or ORDER
    private final LocalDateTime createdAt;

    public SaleOnCreditEvent(Object source, Long shopId, Long transactionId, String billNo,
                           Long customerId, String customerName, String customerPhone,
                           BigDecimal totalAmount, String transactionType, LocalDateTime createdAt) {
        super(source);
        this.shopId = shopId;
        this.transactionId = transactionId;
        this.billNo = billNo;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.totalAmount = totalAmount;
        this.transactionType = transactionType;
        this.createdAt = createdAt;
    }

    public Long getShopId() {
        return shopId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getBillNo() {
        return billNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
