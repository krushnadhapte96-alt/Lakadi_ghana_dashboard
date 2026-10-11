package com.ghana.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Entity
@Table(name = "shop_settings")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopSettings {

    @Id
    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "bill_prefix", length = 10, nullable = false)
    private String billPrefix;

    @Column(name = "footer_message", length = 300)
    private String footerMessage;

    @Column(name = "show_batch_number", nullable = false)
    private Boolean showBatchNumber;

    @Column(name = "show_upi_qr", nullable = false)
    private Boolean showUpiQr;

    @Column(name = "show_bank_details", nullable = false)
    private Boolean showBankDetails;

    @Column(name = "low_stock_threshold", nullable = false, precision = 10, scale = 3)
    private BigDecimal lowStockThreshold;

    @Column(name = "best_before_days", nullable = false)
    private Integer bestBeforeDays;

    @Column(name = "credit_sales_allowed", nullable = false)
    private Boolean creditSalesAllowed;

    @Column(name = "credit_period_days", nullable = false)
    private Integer creditPeriodDays;

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "alert_email", length = 255)
    private String alertEmail;

    @Column(name = "low_stock_alerts", nullable = false)
    private Boolean lowStockAlerts;

    @Column(name = "expiry_alerts", nullable = false)
    private Boolean expiryAlerts;

    @Column(name = "licence_reminders", nullable = false)
    private Boolean licenceReminders;

    @Column(name = "payment_reminders", nullable = false)
    private Boolean paymentReminders;

    @Column(name = "daily_digest", nullable = false)
    private Boolean dailyDigest;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}