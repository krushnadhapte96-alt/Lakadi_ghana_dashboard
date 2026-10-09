package com.ghana.dashboard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class BatchDtos {

    private BatchDtos() {
    }

    // ---- Batch Status ----

    public enum BatchStatus {
        PRESSING, SETTLING, READY, BOTTLED, SOLD_OUT
    }

    // ---- Create Batch ----

    public record CreateBatchRequest(
            @NotNull Long oilTypeId,
            @NotNull Long seedTypeId,
            @NotNull @DecimalMin("0.001") BigDecimal seedKg,
            @NotNull LocalDateTime pressStart,
            String notes
    ) {
    }

    public record CreateBatchResponse(
            Long id,
            String code,
            String oilTypeName,
            String seedTypeName,
            BigDecimal seedKg,
            BigDecimal oilLitres,
            BigDecimal khaliKg,
            BigDecimal yieldPct,
            String status,
            LocalDateTime pressStart,
            LocalDateTime createdAt
    ) {
    }

    // ---- Record Output ----

    public record RecordOutputRequest(
            @NotNull @DecimalMin("0.001") BigDecimal oilLitres,
            @NotNull @DecimalMin("0.001") BigDecimal khaliKg
    ) {
    }

    public record RecordOutputResponse(
            Long batchId,
            String code,
            BigDecimal oilLitres,
            BigDecimal khaliKg,
            BigDecimal yieldPct,
            String status
    ) {
    }

    // ---- Mark Ready ----

    public record MarkReadyRequest(
            @NotNull LocalDate readyDate
    ) {
    }

    public record MarkReadyResponse(
            Long batchId,
            String code,
            LocalDate readyDate,
            String status
    ) {
    }

    // ---- Bottle Batch ----

    public record BottleItem(
            Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal units
    ) {
    }

    public record BottleBatchRequest(
            @NotNull List<BottleItem> items,
            @NotNull @DecimalMin("0.001") BigDecimal totalLitresUsed
    ) {
    }

    public record BottleItemResult(
            Long productId,
            String productName,
            BigDecimal unitsBottled,
            BigDecimal packagingRemaining,
            boolean success,
            String message
    ) {
    }

    public record BottleBatchResponse(
            Long batchId,
            String code,
            List<BottleItemResult> results,
            String status,
            boolean fullyBottled
    ) {
    }

    // ---- Batch Detail ----

    public record BatchDetailResponse(
            Long id,
            String code,
            String oilTypeName,
            String seedTypeName,
            BigDecimal seedKg,
            BigDecimal oilLitres,
            BigDecimal khaliKg,
            BigDecimal yieldPct,
            String status,
            LocalDateTime pressStart,
            LocalDateTime settlingStart,
            LocalDate readyDate,
            LocalDate mfgDate,
            LocalDate bestBeforeDate,
            String notes,
            List<BatchQualityNoteResponse> qualityNotes,
            String createdAt,
            String updatedAt
    ) {
    }

    public record BatchQualityNoteResponse(
            Long id,
            String note,
            String checkedBy,
            String qualityLevel,
            String createdAt
    ) {
    }

    // ---- Batch Summary (list) ----

    public record BatchSummaryResponse(
            Long id,
            String code,
            String oilTypeName,
            BigDecimal seedKg,
            BigDecimal oilLitres,
            BigDecimal khaliKg,
            BigDecimal yieldPct,
            String status,
            Integer daysSettling,
            LocalDateTime pressStart,
            String createdAt
    ) {
    }

    // ---- Batch List Filter ----

    public record BatchFilter(
            String status,
            Long oilTypeId,
            String code,
            String dateFrom,
            String dateTo
    ) {
    }

    // ---- Label PDF ----

    public record LabelData(
            String code,
            String oilTypeName,
            String mfgDate,
            String bestBeforeDate,
            BigDecimal oilLitres,
            String batchId
    ) {
    }

    // ---- Packaging Mapping ----

    public record PackagingMappingRequest(
            @NotNull Long productId,
            @NotNull Long packagingItemId,
            @NotNull @DecimalMin("0.001") BigDecimal unitsPerPack
    ) {
    }

    public record PackagingMappingResponse(
            Long id,
            Long productId,
            String productName,
            Long packagingItemId,
            String packagingName,
            String packagingType,
            String packagingSize,
            BigDecimal unitsPerPack,
            boolean active
    ) {
    }
}
