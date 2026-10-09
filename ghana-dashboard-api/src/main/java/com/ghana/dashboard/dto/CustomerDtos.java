package com.ghana.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 100) String email,
            @Size(max = 50) String gstNo,
            List<String> tags,
            String preferredLanguage
    ) {
    }

    public record NoteRequest(@NotBlank @Size(max = 1000) String note) {
    }

    public record NoteResponse(Long id, String note, String createdByName, String createdAt) {
    }

    public record ListItem(
            Long id,
            String name,
            String phone,
            List<String> tags,
            String preferredLanguage,
            int totalOrders,
            BigDecimal totalSpent,
            String lastPurchaseAt,
            Integer daysSinceLastPurchase,
            LocalDate predictedRefillDate,
            boolean refillDue,
            String favouriteProductName
    ) {
    }

    public record HistoryItem(
            String type,
            Long id,
            String billNo,
            String date,
            BigDecimal amount,
            String status,
            String paymentStatus,
            String itemsSummary
    ) {
    }

    public record Detail(
            Long id,
            String name,
            String phone,
            String email,
            String gstNo,
            List<String> tags,
            String preferredLanguage,
            boolean active,
            String createdAt,
            int totalOrders,
            BigDecimal totalSpent,
            String lastPurchaseAt,
            Integer daysSinceLastPurchase,
            BigDecimal avgGapDays,
            LocalDate predictedRefillDate,
            boolean refillDue,
            Long favouriteProductId,
            String favouriteProductName,
            BigDecimal khataBalance,
            List<SalesDtos.CustomerAddressResponse> addresses,
            List<NoteResponse> notes,
            List<HistoryItem> history
    ) {
    }
}
