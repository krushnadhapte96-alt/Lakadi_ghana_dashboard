package com.ghana.dashboard.dto;

import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.domain.CashBookDay;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PaymentDtos {

    // ========== Payment DTOs ==========

    public record PaymentRequest(
            @NotNull(message = "Direction is required")
            Payment.Direction direction,
            @NotNull(message = "Payment mode is required")
            Payment.PaymentMode mode,
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be greater than zero")
            BigDecimal amount,
            @NotNull(message = "Source type is required")
            Payment.SourceType sourceType,
            @NotNull(message = "Source id is required")
            Long sourceId,
            @Size(max = 100) String referenceNo,
            @NotNull(message = "Payment date is required")
            LocalDate paymentDate,
            String note
    ) {}

    public record PaymentResponse(
            Long id,
            Long shopId,
            Payment.Direction direction,
            Payment.PaymentMode mode,
            BigDecimal amount,
            Payment.SourceType sourceType,
            Long sourceId,
            String referenceNo,
            LocalDate paymentDate,
            String note,
            Long createdBy,
            String createdByName,
            String createdAt,
            String updatedAt
    ) {}

    public record PaymentSummaryResponse(
            Long id,
            Payment.Direction direction,
            Payment.PaymentMode mode,
            BigDecimal amount,
            Payment.SourceType sourceType,
            Long sourceId,
            String referenceNo,
            LocalDate paymentDate,
            String createdAt
    ) {}

    // ========== Pending Payment DTOs ==========

    public record PendingPaymentResponse(
            Long id,
            String billNo,
            String customerName,
            String customerPhone,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal dueAmount,
            Payment.SourceType sourceType,
            LocalDate dueDate,
            int daysOverdue,
            String paymentMode,
            String paymentStatus
    ) {}

    // ========== Cash Book DTOs ==========

    public record CashBookDayResponse(
            Long id,
            Long shopId,
            LocalDate dayDate,
            CashBookDay.Status status,
            BigDecimal openingBalance,
            BigDecimal totalIn,
            BigDecimal totalOut,
            BigDecimal closingBalance,
            Long closedBy,
            String closedByName,
            String closedAt,
            Long reopenedBy,
            String reopenedByName,
            String reopenedAt,
            String notes,
            boolean canEdit
    ) {}

    public record CashBookEntryResponse(
            Long id,
            Payment.Direction direction,
            Payment.PaymentMode mode,
            BigDecimal amount,
            Payment.SourceType sourceType,
            Long sourceId,
            String referenceNo,
            String description,
            String createdAt,
            String createdByName
    ) {}

    public record CashBookSummaryResponse(
            BigDecimal cashIn,
            BigDecimal upiIn,
            BigDecimal cardIn,
            BigDecimal bankIn,
            BigDecimal totalIn,
            BigDecimal totalOut,
            BigDecimal netBalance,
            LocalDate date
    ) {}

    // ========== Filters ==========

    public record PaymentFilter(
            LocalDate dateFrom,
            LocalDate dateTo,
            Payment.Direction direction,
            Payment.PaymentMode mode,
            Payment.SourceType sourceType,
            int page,
            int size
    ) {}

    public record CashBookFilter(
            LocalDate date,
            CashBookDay.Status status,
            int page,
            int size
    ) {}

    // ========== Close/Reopen DTOs ==========

    public record CloseCashBookRequest(
            BigDecimal closingBalance,
            String notes
    ) {}

    public record ReopenCashBookRequest(
            String reason
    ) {}
}
