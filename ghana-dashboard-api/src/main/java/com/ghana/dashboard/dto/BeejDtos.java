package com.ghana.dashboard.dto;

import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.domain.SeedPurchase;
import com.ghana.dashboard.domain.SeedStockMovement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class BeejDtos {

    private BeejDtos() {
    }

    // ---- Seed Type ----

    public record SeedTypeResponse(
            Long id,
            Long oilTypeId,
            String oilTypeName,
            String nameEn,
            String nameHi,
            boolean active
    ) {
    }

    public record RateHistoryPoint(
            Long purchaseId,
            LocalDate purchaseDate,
            BigDecimal ratePerKg,
            BigDecimal qtyKg,
            String supplierName
    ) {
    }

    // ---- Supplier ----

    public record SupplierRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20) String phone,
            @Size(max = 500) String address,
            @Size(max = 20) String gstin
    ) {
    }

    public record SupplierResponse(
            Long id,
            String name,
            String phone,
            String address,
            String gstin,
            boolean active,
            BigDecimal totalPurchases,
            BigDecimal totalPaid,
            BigDecimal balance,
            String createdAt
    ) {
    }

    public record SupplierSummaryResponse(
            Long id,
            String name,
            String phone,
            boolean active
    ) {
    }

    // ---- Seed Purchase ----

    /** Bound from multipart/form-data fields so the bill file travels with the purchase. */
    public record SeedPurchaseForm(
            @NotNull(message = "Seed type is required")
            Long seedTypeId,
            @NotNull(message = "Supplier is required")
            Long supplierId,
            @NotNull(message = "Quantity is required")
            @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
            BigDecimal qtyKg,
            @NotNull(message = "Rate is required")
            @DecimalMin(value = "0", message = "Rate cannot be negative")
            BigDecimal ratePerKg,
            @DecimalMin(value = "0", message = "Moisture % cannot be negative")
            @DecimalMax(value = "100", message = "Moisture % cannot exceed 100")
            BigDecimal moisturePercent,
            @NotNull(message = "Grade is required")
            SeedPurchase.Grade grade,
            @NotNull(message = "Purchase date is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate purchaseDate,
            @NotNull(message = "Amount paid is required")
            @DecimalMin(value = "0", message = "Amount paid cannot be negative")
            BigDecimal amountPaid,
            Payment.PaymentMode paymentMode,
            MultipartFile billFile
    ) {
    }

    /** Purchase quantity/rate/seed-type are immutable once stock has moved; only these fields can change. */
    public record SeedPurchaseUpdateForm(
            @NotNull(message = "Supplier is required")
            Long supplierId,
            @DecimalMin(value = "0", message = "Moisture % cannot be negative")
            @DecimalMax(value = "100", message = "Moisture % cannot exceed 100")
            BigDecimal moisturePercent,
            @NotNull(message = "Grade is required")
            SeedPurchase.Grade grade,
            @NotNull(message = "Purchase date is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate purchaseDate,
            @NotNull(message = "Amount paid is required")
            @DecimalMin(value = "0", message = "Amount paid cannot be negative")
            BigDecimal amountPaid,
            Payment.PaymentMode paymentMode,
            MultipartFile billFile,
            Boolean removeBill
    ) {
    }

    public record SeedPurchaseResponse(
            Long id,
            Long seedTypeId,
            String seedTypeName,
            Long supplierId,
            String supplierName,
            BigDecimal qtyKg,
            BigDecimal ratePerKg,
            BigDecimal totalAmount,
            BigDecimal moisturePercent,
            String grade,
            LocalDate purchaseDate,
            boolean hasBill,
            String billFileName,
            String paymentStatus,
            BigDecimal amountPaid,
            Long createdBy,
            String createdByName,
            String createdAt
    ) {
    }

    public record SeedPurchaseSummaryResponse(
            Long id,
            Long seedTypeId,
            String seedTypeName,
            Long supplierId,
            String supplierName,
            BigDecimal qtyKg,
            BigDecimal ratePerKg,
            BigDecimal totalAmount,
            String grade,
            LocalDate purchaseDate,
            String paymentStatus,
            BigDecimal amountPaid,
            boolean hasBill
    ) {
    }

    // ---- Seed Stock ----

    public record SeedStockResponse(
            Long seedTypeId,
            String seedTypeName,
            BigDecimal availableKg,
            BigDecimal avgRate,
            BigDecimal lowStockThreshold,
            boolean lowStock,
            List<BigDecimal> stockTrend
    ) {
    }

    public record SeedStockAdjustRequest(
            @NotNull(message = "Quantity is required")
            BigDecimal qty,
            @NotNull(message = "Movement type is required")
            SeedStockMovement.MovementType type,
            @NotBlank(message = "Reason is required")
            String reason,
            String note
    ) {
    }

    // ---- Supplier Payment ----

    public record SupplierPaymentRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            BigDecimal amount,
            @NotNull(message = "Payment mode is required")
            Payment.PaymentMode mode,
            @NotNull(message = "Payment date is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate paymentDate,
            @Size(max = 100) String referenceNo,
            String note
    ) {
    }

    public record SupplierPaymentResponse(
            Long id,
            Long supplierId,
            Long seedPurchaseId,
            BigDecimal amount,
            String mode,
            LocalDate paymentDate,
            String referenceNo,
            String note,
            String createdByName,
            String createdAt
    ) {
    }

    // ---- Supplier Ledger ----

    public record SupplierLedgerEntry(
            LocalDate date,
            String type,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance
    ) {
    }

    public record SupplierLedgerResponse(
            Long supplierId,
            String supplierName,
            BigDecimal totalPurchases,
            BigDecimal totalPaid,
            BigDecimal balance,
            List<SupplierLedgerEntry> entries
    ) {
    }
}
