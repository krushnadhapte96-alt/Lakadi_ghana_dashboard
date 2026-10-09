package com.ghana.dashboard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CostingDtos {

    private CostingDtos() {
    }

    // ---- Cost Defaults ----

    public record CostDefaultsRequest(
            @NotNull String electricityMode,
            @NotNull @DecimalMin("0") BigDecimal electricityValue,
            @NotNull String labourMode,
            @NotNull @DecimalMin("0") BigDecimal labourValue,
            @NotNull String otherMode,
            @NotNull @DecimalMin("0") BigDecimal otherValue,
            @NotNull @DecimalMin("0") BigDecimal khaliValuePerKg,
            @NotNull @DecimalMin("0") BigDecimal targetMarginPct
    ) {
    }

    public record CostDefaultsResponse(
            String electricityMode,
            BigDecimal electricityValue,
            String labourMode,
            BigDecimal labourValue,
            String otherMode,
            BigDecimal otherValue,
            BigDecimal khaliValuePerKg,
            BigDecimal targetMarginPct,
            LocalDateTime updatedAt
    ) {
    }

    // ---- Batch Cost ----

    public record BatchCostResponse(
            Long batchId,
            String batchCode,
            BigDecimal seedKg,
            BigDecimal oilLitres,
            BigDecimal khaliKg,
            BigDecimal seedCost,
            BigDecimal electricityCost,
            BigDecimal labourCost,
            BigDecimal otherCost,
            BigDecimal khaliCredit,
            BigDecimal totalCost,
            BigDecimal costPerLitre,
            boolean manualOverride,
            LocalDateTime computedAt
    ) {
    }

    public record BatchCostUpdateRequest(
            BigDecimal electricityCost,
            BigDecimal labourCost,
            BigDecimal otherCost,
            BigDecimal khaliCredit
    ) {
    }

    // ---- Costing Products ----

    public record CostingProductResponse(
            Long productId,
            String productName,
            String sku,
            boolean isKhali,
            BigDecimal unitCost,
            BigDecimal sellingPrice,
            BigDecimal marginPct,
            BigDecimal targetMarginPct,
            BigDecimal suggestedPrice,
            boolean belowTarget,
            Long sourceBatchId,
            String sourceBatchCode
    ) {
    }

    public record ApplySuggestedPriceResponse(
            Long productId,
            BigDecimal sellingPrice
    ) {
    }
}
