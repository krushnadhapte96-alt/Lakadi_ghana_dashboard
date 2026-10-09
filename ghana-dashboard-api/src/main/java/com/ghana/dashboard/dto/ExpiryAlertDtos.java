package com.ghana.dashboard.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ExpiryAlertDtos {

    private ExpiryAlertDtos() {
    }

    public record ExpiryAlertItemResponse(
            Long batchStockId,
            String batchId,
            Long productId,
            String productName,
            String sku,
            BigDecimal qtyAvailable,
            String unit,
            BigDecimal costPrice,
            BigDecimal valueAtRisk,
            LocalDate expiryDate,
            long daysLeft,
            String bucket,
            BigDecimal activeDiscountPct
    ) {
    }

    public record ExpiryBucketResponse(
            String bucket,
            String label,
            BigDecimal totalUnits,
            BigDecimal totalValue,
            List<ExpiryAlertItemResponse> items
    ) {
    }

    public record ExpiryAlertsResponse(
            BigDecimal totalUnitsAtRisk,
            BigDecimal totalValueAtRisk,
            List<ExpiryBucketResponse> buckets
    ) {
    }

    public record DiscountRequest(
            @NotNull @DecimalMin("0.01") @DecimalMax("90") BigDecimal discountPct,
            String reason
    ) {
    }

    public record WriteOffRequest(
            @NotNull @DecimalMin("0.001") BigDecimal qty,
            String note
    ) {
    }

    public record SnoozeRequest(
            @NotNull @Min(1) Integer days
    ) {
    }
}
