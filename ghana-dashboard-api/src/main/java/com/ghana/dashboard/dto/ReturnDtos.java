package com.ghana.dashboard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReturnDtos {

    private ReturnDtos() {
    }

    // ---- Lookup (search bill/order) ----

    public record LookupItem(
            Long productId,
            String productName,
            String sku,
            BigDecimal qty,
            String unit,
            BigDecimal rate,
            String batchId
    ) {
    }

    public record LookupResponse(
            String sourceType,
            Long sourceId,
            String billNo,
            Long customerId,
            String customerName,
            List<LookupItem> items
    ) {
    }

    // ---- Create ----

    public record ReturnItemInput(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal qty
    ) {
    }

    public record CreateReturnRequest(
            @NotNull String sourceType,
            @NotNull Long sourceId,
            @NotNull String type,
            @Size(max = 1000) String description,
            @NotEmpty List<ReturnItemInput> items
    ) {
    }

    // ---- Responses ----

    public record ReturnItemResponse(
            Long id,
            Long productId,
            String productName,
            String sku,
            String batchId,
            BigDecimal qty,
            BigDecimal unitPrice,
            String action
    ) {
    }

    public record ReturnPhotoResponse(
            Long id,
            String fileName,
            long fileSize,
            String fileType
    ) {
    }

    public record ReturnSummaryResponse(
            Long id,
            String returnNo,
            String sourceType,
            Long sourceId,
            String sourceBillNo,
            Long customerId,
            String customerName,
            String type,
            String status,
            String resolution,
            int itemCount,
            BigDecimal totalValue,
            List<Long> photoIds,
            String createdAt
    ) {
    }

    public record ReturnDetailResponse(
            Long id,
            String returnNo,
            String sourceType,
            Long sourceId,
            String sourceBillNo,
            Long customerId,
            String customerName,
            String type,
            String status,
            String resolution,
            String description,
            BigDecimal refundAmount,
            Long replacementSaleId,
            List<ReturnItemResponse> items,
            List<ReturnPhotoResponse> photos,
            String reportedByName,
            String resolvedByName,
            String resolvedAt,
            String createdAt
    ) {
    }

    // ---- Review / Resolve ----

    public record ReviewRequest(
            String note
    ) {
    }

    public record ResolveItemInput(
            @NotNull Long returnItemId,
            @NotNull String action
    ) {
    }

    public record ResolveRequest(
            @NotNull String resolution,
            List<ResolveItemInput> items,
            BigDecimal refundAmount,
            String paymentMode,
            String note
    ) {
    }

    // ---- Filter ----

    public record ReturnFilter(
            String status,
            String type,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
    }

    // ---- Stats ----

    public record ReturnTypeStat(
            String type,
            long count,
            BigDecimal totalValue
    ) {
    }

    public record ReturnBatchStat(
            String batchId,
            String productName,
            long count,
            BigDecimal totalValue
    ) {
    }

    public record ReturnStatsResponse(
            List<ReturnTypeStat> byType,
            List<ReturnBatchStat> byBatch
    ) {
    }
}
