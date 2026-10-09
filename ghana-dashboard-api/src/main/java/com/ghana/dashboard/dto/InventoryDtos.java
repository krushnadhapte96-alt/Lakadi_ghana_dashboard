package com.ghana.dashboard.dto;

import com.ghana.dashboard.domain.Product;
import com.ghana.dashboard.domain.StockMovement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    // ---- Oil Type DTOs ----

public record OilTypeRequest(
            @NotBlank @Size(max = 50) String nameEn,
            @NotBlank @Size(max = 50) String nameHi,
            @NotNull Double density,
            @NotNull Integer bestBeforeDays,
            @NotBlank @Size(max = 20) String colourTag,
            // Deleting an oil type only clears `active`, so without this the row could
            // never be brought back. Boxed so older clients that omit it still save.
            Boolean active) {
    }

    public record OilTypeResponse(
            Long id,
            String nameEn,
            String nameHi,
            Double density,
            Integer bestBeforeDays,
            String colourTag,
            boolean active,
            String createdAt
    ) {
    }

    // ---- Product DTOs ----

    public record ProductRequest(
            Long oilTypeId,
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("0.001") BigDecimal packSize,
            @NotBlank String unit,
            @NotBlank String container,
            @NotNull @DecimalMin("0") BigDecimal sellingPrice,
            @NotNull @DecimalMin("0") BigDecimal mrp,
            @NotNull @DecimalMin("0") BigDecimal costPrice,
            @NotNull @DecimalMin("0") BigDecimal lowStockThreshold,
            boolean isKhali
    ) {
    }

    public record ProductResponse(
            Long id,
            Long shopId,
            Long oilTypeId,
            String oilTypeName,
            String name,
            String sku,
            BigDecimal packSize,
            String unit,
            String container,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            BigDecimal costPrice,
            BigDecimal lowStockThreshold,
            boolean active,
            boolean isKhali,
            String createdAt
    ) {
    }

    public record ProductSummaryResponse(
            Long id,
            String name,
            String sku,
            String oilTypeName,
            BigDecimal packSize,
            String unit,
            String container,
            boolean isKhali
    ) {
    }

    // ---- Product Price Update ----

    public record UpdateProductPriceRequest(
            @NotNull @DecimalMin("0") BigDecimal sellingPrice,
            @NotNull @DecimalMin("0") BigDecimal mrp,
            @NotNull @DecimalMin("0") BigDecimal costPrice
    ) {
    }

    // ---- Product Threshold Update ----

    public record UpdateProductThresholdRequest(
            @NotNull @DecimalMin("0") BigDecimal lowStockThreshold
    ) {
    }

    // ---- Packaging Item DTOs ----

    public record PackagingItemRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank String type,
            @NotBlank @Size(max = 50) String size,
            @NotNull @DecimalMin("0") BigDecimal stockQty,
            @NotNull @DecimalMin("0") BigDecimal costPerUnit,
            @NotNull @DecimalMin("0") BigDecimal lowThreshold
    ) {
    }

    public record PackagingItemResponse(
            Long id,
            Long shopId,
            String name,
            String type,
            String size,
            BigDecimal stockQty,
            BigDecimal costPerUnit,
            BigDecimal lowThreshold,
            boolean active,
            boolean lowStock,
            String createdAt
    ) {
    }

    // ---- Packaging Purchase ----

    public record PackagingPurchaseRequest(
            @NotNull @DecimalMin("0.001") BigDecimal qty,
            @NotNull @DecimalMin("0") BigDecimal costPerUnit,
            String note
    ) {
    }

    // ---- Packaging Stock Adjustment ----

    public record PackagingStockAdjustmentRequest(
            @NotNull BigDecimal qty,
            @NotBlank String reason,
            String note
    ) {
    }

    // ---- Packaging Movement DTOs ----

    public record PackagingMovementResponse(
            Long id,
            Long itemId,
            String itemName,
            String itemType,
            BigDecimal changeQty,
            String type,
            BigDecimal unitCost,
            BigDecimal totalCost,
            String reason,
            String note,
            String referenceType,
            Long referenceId,
            Long userId,
            String userName,
            String createdAt
    ) {
    }

    public record PackagingMovementFilter(
            Long itemId,
            String type,
            String dateFrom,
            String dateTo,
            Integer page,
            Integer size
    ) {
    }

    // ---- Batch Stock DTOs ----

    public record BatchStockResponse(
            Long id,
            String batchId,
            BigDecimal qtyAvailable,
            BigDecimal qtyReserved,
            BigDecimal costPrice,
            LocalDate receivedDate,
            LocalDate expiryDate,
            LocalDate bestBeforeDate,
            boolean active,
            boolean isLowStock
    ) {
    }

    public record CreateBatchStockRequest(
            String batchId,
            @NotNull @DecimalMin("0.001") BigDecimal qty,
            @NotNull @DecimalMin("0") BigDecimal costPrice,
            LocalDate receivedDate,
            LocalDate expiryDate
    ) {
    }

    // ---- Inventory Aggregated DTO ----

    public record InventoryItemResponse(
            Long productId,
            String name,
            String sku,
            String oilTypeName,
            BigDecimal packSize,
            String unit,
            String container,
            boolean isKhali,
            BigDecimal qtyAvailable,
            BigDecimal qtyReserved,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            BigDecimal lowStockThreshold,
            boolean lowStock,
            boolean active
    ) {
    }

    public record AggregatedInventoryResponse(
            List<InventoryItemResponse> oilProducts,
            List<InventoryItemResponse> khaliProducts,
            List<PackagingItemResponse> packagingItems
    ) {
    }

    // ---- Stock Adjustment ----

    public record StockAdjustmentRequest(
            @NotNull BigDecimal qty,
            @NotBlank String reason,
            String note
    ) {
    }

    public record OpeningStockRequest(
            @NotNull @DecimalMin("0.001") BigDecimal qty,
            @NotNull @DecimalMin("0") BigDecimal costPrice,
            LocalDate receivedDate,
            LocalDate expiryDate
    ) {
    }

    // ---- Stock Movement DTOs ----

    public record StockMovementResponse(
            Long id,
            Long productId,
            String productName,
            String batchId,
            BigDecimal changeQty,
            String type,
            BigDecimal unitPrice,
            BigDecimal totalValue,
            String reason,
            String note,
            String referenceType,
            Long referenceId,
            Long userId,
            String userName,
            String createdAt
    ) {
    }

    public record StockMovementFilter(
            Long productId,
            String type,
            String dateFrom,
            String dateTo,
            Integer page,
            Integer size
    ) {
    }

    // ---- Exception ----

    public static class InsufficientStockException extends RuntimeException {
        private final Long productId;
        private final String sku;
        private final BigDecimal available;
        private final BigDecimal requested;

        public InsufficientStockException(Long productId, String sku, BigDecimal available, BigDecimal requested) {
            super(String.format("Insufficient stock for product %s. Available: %s, Requested: %s", sku, available, requested));
            this.productId = productId;
            this.sku = sku;
            this.available = available;
            this.requested = requested;
        }

        public Long getProductId() {
            return productId;
        }

        public String getSku() {
            return sku;
        }

        public BigDecimal getAvailable() {
            return available;
        }

        public BigDecimal getRequested() {
            return requested;
        }
    }
}
