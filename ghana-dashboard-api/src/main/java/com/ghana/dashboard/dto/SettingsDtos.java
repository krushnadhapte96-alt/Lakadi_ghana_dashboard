package com.ghana.dashboard.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Settings payloads. Every record here is owner-only except {@link MyAccountRequest},
 * which a signed-in user may submit for their own profile.
 */
public final class SettingsDtos {

    private SettingsDtos() {
    }

    // ==================== shop profile ====================

    /**
     * Shop profile fields. {@code gstin} and {@code fssaiNumber} are the manual
     * fallback printed on bills; a live licence in the licence register always wins
     * over them (see LicenceService.billIdentifiers).
     */
    public record ShopSettingsRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String phone,
            @Email @Size(max = 191) String email,
            @Size(max = 255) String address,
            @Size(max = 20) String gstin,
            @Size(max = 100) String fssaiNumber,
            @Size(max = 100) String upiId,
            String bankDetails) {
    }

    /**
     * Bank details come back masked only - the plaintext is never sent to a client.
     * {@code bankDetailsMasked} is null when nothing is stored, and a non-null value
     * with {@code bankDetailsOnFile} true means "leave the stored value alone".
     */
    public record ShopSettingsResponse(
            Long id,
            String name,
            String phone,
            String email,
            String address,
            String gstin,
            String fssaiNumber,
            String upiId,
            boolean hasLogo,
            String logoName,
            String logoUrl,
            boolean bankDetailsOnFile,
            String bankDetailsMasked,
            String currencyCode) {
    }

    // ==================== billing ====================

    /**
     * Switches are boxed rather than primitive on every request record: a client that
     * omits one would otherwise fail Jackson deserialisation on a null-to-boolean
     * mapping and surface as a 500 instead of saving cleanly.
     */
    public record BillingSettingsRequest(
            @NotBlank @Size(max = 10) String billPrefix,
            @Size(max = 300) String footerMessage,
            Boolean showBatchNumber,
            Boolean showUpiQr,
            Boolean showBankDetails) {
    }

    public record BillingSettingsResponse(
            String billPrefix,
            String footerMessage,
            boolean showBatchNumber,
            boolean showUpiQr,
            boolean showBankDetails) {
    }

    // ==================== defaults ====================

    /**
     * Shop-wide operational defaults. Per-oil best-before days live on the oil type
     * itself; {@code defaultBestBeforeDays} is what a newly created oil type starts
     * with, and {@code defaultLowStockThreshold} pre-fills new products.
     */
    public record DefaultsRequest(
            @NotNull @Digits(integer = 12, fraction = 3) @Min(0) BigDecimal defaultLowStockThreshold,
            @NotNull @Min(1) @Max(3650) Integer defaultBestBeforeDays,
            Boolean creditSalesAllowed,
            @NotNull @Min(0) @Max(365) Integer defaultCreditPeriodDays,
            @NotNull @Digits(integer = 12, fraction = 2) @Min(0) BigDecimal defaultCreditLimit) {
    }

    public record DefaultsResponse(
            BigDecimal defaultLowStockThreshold,
            int defaultBestBeforeDays,
            boolean creditSalesAllowed,
            int defaultCreditPeriodDays,
            BigDecimal defaultCreditLimit) {
    }

    // ==================== notifications ====================

    /**
     * Delivery preferences. There is no notification centre yet, so these are the
     * switches a later prompt will read when it wires the channels up.
     */
    public record NotificationsRequest(
            @Email @Size(max = 191) String alertEmail,
            Boolean lowStockAlerts,
            Boolean expiryAlerts,
            Boolean licenceReminders,
            Boolean paymentReminders,
            Boolean dailyDigest) {
    }

    public record NotificationsResponse(
            String alertEmail,
            boolean lowStockAlerts,
            boolean expiryAlerts,
            boolean licenceReminders,
            boolean paymentReminders,
            boolean dailyDigest) {
    }

    // ==================== staff ====================

    public record StaffCreateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "^[0-9]{7,15}$", message = "must be a valid phone number") String phone,
            @Email @Size(max = 191) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record StaffUpdateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "^[0-9]{7,15}$", message = "must be a valid phone number") String phone,
            @Email @Size(max = 191) String email) {
    }

    public record StaffResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    /** Never carries the password hash - only whether one is set, which is always true. */
    public record StaffResponse(
            Long id,
            String name,
            String phone,
            String email,
            String role,
            boolean active,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt) {
    }

    // ==================== my account ====================

    /** Self-service, so it deliberately cannot change name, phone, email or role. */
    public record MyAccountRequest(
            @Size(max = 191) String preferredLanguage) {
    }

    public record MyAccountResponse(
            Long id,
            String name,
            String phone,
            String email,
            String role,
            boolean active,
            String shopName,
            LocalDateTime lastLoginAt,
            String preferredLanguage) {
    }

    // ==================== export ====================

    /** One selectable dataset in the export zip. */
    public record ExportManifestEntry(String key, String label, long rows) {
    }

    public record ExportManifest(String generatedAt, List<ExportManifestEntry> entries) {
    }
}