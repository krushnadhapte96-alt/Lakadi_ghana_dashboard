package com.ghana.dashboard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SalesDtos {

    private SalesDtos() {
    }

    // ==================== Customer DTOs ====================

    public record CustomerRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 100) String email,
            @Size(max = 50) String gstNo
    ) {
    }

    public record CustomerResponse(
            Long id,
            Long shopId,
            String name,
            String phone,
            String email,
            String gstNo,
            boolean active,
            String createdAt
    ) {
    }

    public record CustomerSummaryResponse(
            Long id,
            String name,
            String phone,
            String email
    ) {
    }

    // ==================== Customer Address DTOs ====================

    public record CustomerAddressRequest(
            @NotNull Long customerId,
            @NotBlank String addressType,
            @NotBlank @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @NotBlank @Size(max = 50) String city,
            @NotBlank @Size(max = 50) String state,
            @NotBlank @Size(max = 10) String pincode,
            @Size(max = 100) String landmark,
            boolean isDefault
    ) {
    }

    public record CustomerAddressResponse(
            Long id,
            Long customerId,
            String addressType,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String pincode,
            String landmark,
            boolean isDefault,
            boolean active,
            String createdAt
    ) {
    }

    // ==================== Sale Item DTOs ====================

    public record SaleItemRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal qty,
            @NotNull @DecimalMin("0") BigDecimal rate
    ) {
    }

    public record SaleItemResponse(
            Long id,
            Long saleId,
            Long productId,
            String sku,
            String productName,
            String batchId,
            BigDecimal qty,
            String unit,
            BigDecimal rate,
            BigDecimal amount,
            BigDecimal costPrice,
            BigDecimal profit,
            String createdAt
    ) {
    }

    // ==================== Sale DTOs ====================

    public record SaleRequest(
            Long customerId,
            String customerPhone,
            @NotNull List<SaleItemRequest> items,
            @NotNull @DecimalMin("0") BigDecimal discount,
            @NotBlank String discountType,
            @NotBlank String paymentMode,
            BigDecimal paidAmount,
            String paymentReference,
            String notes,
            Boolean overrideCreditLimit
    ) {
    }

    public record SaleResponse(
            Long id,
            Long shopId,
            String billNo,
            Long customerId,
            String customerName,
            String customerPhone,
            Long userId,
            String userName,
            BigDecimal subtotal,
            BigDecimal discount,
            String discountType,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            String paymentMode,
            String paymentStatus,
            String paymentReference,
            boolean isVoid,
            String voidReason,
            Long voidedBy,
            String voidedByName,
            String voidedAt,
            boolean active,
            String createdAt,
            List<SaleItemResponse> items
    ) {
    }

    public record SaleSummaryResponse(
            Long id,
            String billNo,
            String customerName,
            String customerPhone,
            BigDecimal totalAmount,
            String paymentMode,
            String paymentStatus,
            boolean isVoid,
            String createdAt
    ) {
    }

    // ==================== Order Item DTOs ====================

    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal qty,
            @NotNull @DecimalMin("0") BigDecimal rate
    ) {
    }

    public record OrderItemResponse(
            Long id,
            Long orderId,
            Long productId,
            String sku,
            String productName,
            BigDecimal qty,
            String unit,
            BigDecimal rate,
            BigDecimal amount,
            BigDecimal costPrice,
            BigDecimal profit,
            String createdAt
    ) {
    }

    // ==================== Order DTOs ====================

    public record OrderRequest(
            @NotNull Long customerId,
            @NotNull Long addressId,
            @NotNull List<OrderItemRequest> items,
            @NotNull @DecimalMin("0") BigDecimal deliveryCharge,
            @NotNull @DecimalMin("0") BigDecimal discount,
            @NotBlank String discountType,
            @NotBlank String paymentMode,
            BigDecimal paidAmount,
            String paymentReference,
            LocalDate expectedDeliveryDate,
            String notes,
            Boolean overrideCreditLimit
    ) {
    }

    public record OrderUpdateRequest(
            @NotNull Long customerId,
            @NotNull Long addressId,
            @NotNull List<OrderItemRequest> items,
            @NotNull @DecimalMin("0") BigDecimal deliveryCharge,
            @NotNull @DecimalMin("0") BigDecimal discount,
            @NotBlank String discountType,
            @NotBlank String paymentMode,
            BigDecimal paidAmount,
            String paymentReference,
            LocalDate expectedDeliveryDate,
            String notes
    ) {
    }

    public record OrderResponse(
            Long id,
            Long shopId,
            String billNo,
            Long customerId,
            String customerName,
            String customerPhone,
            Long addressId,
            String addressSummary,
            Long userId,
            String userName,
            BigDecimal subtotal,
            BigDecimal deliveryCharge,
            BigDecimal discount,
            String discountType,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            String paymentMode,
            String paymentStatus,
            String paymentReference,
            String status,
            LocalDate expectedDeliveryDate,
            LocalDate actualDeliveryDate,
            String notes,
            String cancelReason,
            Long cancelledBy,
            String cancelledByName,
            String cancelledAt,
            boolean active,
            String createdAt,
            List<OrderItemResponse> items,
            List<OrderStatusHistoryResponse> statusHistory
    ) {
    }

    public record OrderSummaryResponse(
            Long id,
            String billNo,
            String customerName,
            String customerPhone,
            BigDecimal totalAmount,
            String paymentMode,
            String paymentStatus,
            String status,
            LocalDate expectedDeliveryDate,
            String createdAt
    ) {
    }

    // ==================== Order Status History DTOs ====================

    public record OrderStatusHistoryResponse(
            Long id,
            Long orderId,
            String oldStatus,
            String newStatus,
            Long changedBy,
            String changedByName,
            String notes,
            String createdAt
    ) {
    }

    public record OrderStatusUpdateRequest(
            @NotBlank String newStatus,
            String notes
    ) {
    }

    // ==================== Order Cancel DTO ====================

    public record OrderCancelRequest(
            @NotBlank String reason
    ) {
    }

    // ==================== Sale Void DTO ====================

    public record SaleVoidRequest(
            @NotBlank String reason
    ) {
    }

    // ==================== Filter DTOs ====================

    public record SaleFilter(
            Long customerId,
            String paymentMode,
            String paymentStatus,
            String dateFrom,
            String dateTo,
            Integer page,
            Integer size
    ) {
    }

    public record OrderFilter(
            String status,
            Long customerId,
            String dateFrom,
            String dateTo,
            Integer page,
            Integer size
    ) {
    }

    // ==================== Payment Status DTO ====================

    public record UpdatePaymentStatusRequest(
            @NotBlank String paymentStatus,
            BigDecimal paidAmount,
            String paymentReference
    ) {
    }
}
