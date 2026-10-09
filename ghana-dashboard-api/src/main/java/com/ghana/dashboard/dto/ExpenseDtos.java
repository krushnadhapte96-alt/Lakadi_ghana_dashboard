package com.ghana.dashboard.dto;

import com.ghana.dashboard.domain.ExpenseCategory;
import com.ghana.dashboard.domain.LossLedger;
import com.ghana.dashboard.domain.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ExpenseDtos {

    public record ExpenseCategoryResponse(
            Long id,
            String nameEn,
            String nameHi,
            ExpenseCategory.CategoryType categoryType,
            String description,
            String colour,
            boolean active,
            int sortOrder
    ) {}

    /** Bound from multipart/form-data fields so the bill file travels with the expense. */
    public record ExpenseForm(
            @NotNull(message = "Category is required")
            Long categoryId,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimals")
            BigDecimal amount,
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title is too long")
            String title,
            @Size(max = 2000, message = "Description is too long")
            String description,
            @NotNull(message = "Expense date is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate expenseDate,
            @NotNull(message = "Payment mode is required")
            Payment.PaymentMode paymentMode,
            @Size(max = 100, message = "Reference is too long")
            String referenceNo,
            MultipartFile billFile,
            Boolean removeBill
    ) {}

    public record ExpenseResponse(
            Long id,
            Long categoryId,
            String categoryName,
            BigDecimal amount,
            String title,
            String description,
            String billFileName,
            Long billFileSize,
            String billFileType,
            boolean hasBill,
            LocalDate expenseDate,
            String paymentMode,
            String referenceNo,
            Long createdBy,
            String createdByName,
            String createdAt
    ) {}

    public record ExpenseSummaryResponse(
            Long id,
            Long categoryId,
            String categoryName,
            String categoryColour,
            BigDecimal amount,
            String title,
            LocalDate expenseDate,
            String paymentMode,
            String referenceNo,
            boolean hasBill,
            String createdAt
    ) {}

    public record ExpenseBreakdownResponse(
            Long categoryId,
            String categoryName,
            String categoryColour,
            BigDecimal amount,
            int count
    ) {}

    public record LossLedgerResponse(
            Long id,
            LossLedger.LossType lossType,
            Long productId,
            String productName,
            String batchId,
            BigDecimal qty,
            BigDecimal value,
            String referenceNo,
            String description,
            LocalDate lossDate,
            String recordedByName,
            String createdAt
    ) {}

    public record PnLSummaryResponse(
            LocalDate fromDate,
            LocalDate toDate,
            String groupBy,
            BigDecimal totalRevenue,
            BigDecimal totalCogs,
            BigDecimal grossProfit,
            BigDecimal totalExpenses,
            BigDecimal totalLosses,
            BigDecimal netProfit,
            BigDecimal grossMarginPercent,
            BigDecimal netMarginPercent,
            List<PnLGroupResponse> groups
    ) {}

    public record PnLGroupResponse(
            String groupKey,
            String groupLabel,
            BigDecimal revenue,
            BigDecimal cogs,
            BigDecimal grossProfit,
            BigDecimal expenses,
            BigDecimal losses,
            BigDecimal netProfit
    ) {}
}
