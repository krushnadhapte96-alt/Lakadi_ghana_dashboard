package com.ghana.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class LicenceDtos {

    private LicenceDtos() {
    }

    public record LicenceCreateRequest(
            @NotBlank @Size(max = 50) String licenceNumber,
            @NotBlank String type,
            @Size(max = 120) String issuer,
            @NotNull LocalDate issueDate,
            @NotNull LocalDate expiryDate,
            @Size(max = 100) String fssaiGstin,
            @Size(max = 500) String notes
    ) {
    }

    public record LicenceUpdateRequest(
            @NotBlank @Size(max = 50) String licenceNumber,
            @NotBlank String type,
            @Size(max = 120) String issuer,
            @NotNull LocalDate issueDate,
            @NotNull LocalDate expiryDate,
            @Size(max = 100) String fssaiGstin,
            @Size(max = 500) String notes
    ) {
    }

    /** {@code daysLeft} is negative once the licence has lapsed. */
    public record LicenceResponse(
            Long id,
            String licenceNumber,
            String type,
            String issuer,
            LocalDate issueDate,
            LocalDate expiryDate,
            long daysLeft,
            String status,
            String fssaiGstin,
            boolean hasDocument,
            String documentName,
            String documentType,
            String notes,
            long renewalCount,
            LocalDate lastRenewedOn,
            String createdAt,
            String updatedAt
    ) {
    }

    public record RenewalRequest(
            @Size(max = 50) String newNumber,
            @NotNull LocalDate issueDate,
            @NotNull LocalDate expiryDate,
            @Size(max = 500) String notes
    ) {
    }

    public record RenewalResponse(
            Long id,
            Long licenceId,
            String previousNumber,
            LocalDate previousExpiryDate,
            String newNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            boolean hasDocument,
            String documentName,
            String notes,
            String renewedBy,
            String createdAt
    ) {
    }

    /**
     * Dashboard strip above the cards. The two registration numbers are the
     * ones printed on bills, so they are resolved here rather than per card.
     */
    public record LicenceSummaryResponse(
            long total,
            long active,
            long expiringSoon,
            long expired,
            long expiringIn7Days,
            long expiringIn30Days,
            long expiringIn60Days,
            LocalDate nextExpiry,
            String fssaiNumber,
            String gstin,
            List<String> missingTypes
    ) {
    }
}