package com.ghana.dashboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class KhataDtos {

    private KhataDtos() {
    }

    public record PartyRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank String type,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 300) String address,
            @DecimalMin("0") BigDecimal creditLimit,
            Integer paymentTermsDays,
            BigDecimal openingBalance
    ) {
    }

    public record PartyResponse(
            Long id,
            Long customerId,
            String name,
            String type,
            String phone,
            String address,
            BigDecimal creditLimit,
            int paymentTermsDays,
            BigDecimal openingBalance,
            boolean active,
            BigDecimal balance,
            BigDecimal overdueAmount,
            boolean overdue,
            int overdueDays,
            LocalDate dueDate,
            LocalDate lastPaymentDate,
            BigDecimal availableCredit,
            String createdAt
    ) {
    }

    public record CreditSaleItem(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") BigDecimal qty,
            @NotNull @DecimalMin("0") BigDecimal rate
    ) {
    }

    public record CreditSaleRequest(
            @NotEmpty @Valid List<CreditSaleItem> items,
            LocalDate dueDate,
            @Size(max = 500) String note,
            Boolean overrideCreditLimit
    ) {
    }

    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String mode,
            LocalDate date,
            @Size(max = 100) String referenceNo,
            @Size(max = 500) String note
    ) {
    }

    public record AdjustmentRequest(
            @NotNull BigDecimal amount,
            @NotBlank @Size(max = 500) String note
    ) {
    }

    public record EntryItem(
            Long productId,
            String productName,
            BigDecimal qty,
            String unit,
            BigDecimal rate,
            BigDecimal amount
    ) {
    }

    public record LedgerEntry(
            Long id,
            String type,
            LocalDate date,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal delta,
            BigDecimal runningBalance,
            String billNo,
            String sourceType,
            Long sourceId,
            String paymentMode,
            String note,
            List<EntryItem> items
    ) {
    }

    public record LedgerResponse(
            PartyResponse party,
            List<LedgerEntry> entries
    ) {
    }

    public record SummaryResponse(
            BigDecimal totalOutstanding,
            BigDecimal overdueAmount,
            int partiesWithBalance,
            int overdueParties,
            BigDecimal collectedThisMonth,
            BigDecimal creditSalesThisMonth,
            List<PartyResponse> topDebtors
    ) {
    }

    public record ReminderResponse(
            String url,
            String message,
            String language
    ) {
    }
}
