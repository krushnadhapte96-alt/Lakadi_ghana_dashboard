package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.domain.ShopSettings;
import com.ghana.dashboard.dto.SettingsDtos;
import com.ghana.dashboard.repository.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shop settings: identity/branding on the {@code shops} row, and the tunable
 * preferences on the previously unused {@code app_settings} key/value table.
 *
 * <p>Bill rendering reads {@link #billBranding} on every print, so that method does
 * no writes and swallows its own failures - a settings problem must never stop a
 * customer taking their bill.
 */
@Service
public class SettingsService {

    private static final String LOGO_FOLDER = "settings";

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    // app_settings keys. Grouped so the fallbacks below read as one block.
    // Kept for backward compatibility fallback only.
    private static final String K_BILL_PREFIX = "billing.bill_prefix";
    private static final String K_FOOTER = "billing.footer_message";
    private static final String K_SHOW_BATCH = "billing.show_batch_number";
    private static final String K_SHOW_QR = "billing.show_upi_qr";
    private static final String K_SHOW_BANK = "billing.show_bank_details";
    private static final String K_LOW_STOCK = "defaults.low_stock_threshold";
    private static final String K_BEST_BEFORE = "defaults.best_before_days";
    private static final String K_CREDIT_ALLOWED = "defaults.credit_sales_allowed";
    private static final String K_CREDIT_DAYS = "defaults.credit_period_days";
    private static final String K_CREDIT_LIMIT = "defaults.credit_limit";
    private static final String K_ALERT_EMAIL = "notifications.alert_email";
    private static final String K_LOW_STOCK_ALERTS = "notifications.low_stock";
    private static final String K_EXPIRY_ALERTS = "notifications.expiry";
    private static final String K_LICENCE_ALERTS = "notifications.licences";
    private static final String K_PAYMENT_ALERTS = "notifications.payments";
    private static final String K_DAILY_DIGEST = "notifications.daily_digest";

    private static final String SHOP_SELECT = """
            SELECT id, name, mobile, email, address, gstin, fssai_number, upi_id,
                   logo_path, logo_name, logo_type, bank_details_enc, currency_code
            FROM shops WHERE id = :shop AND deleted_at IS NULL""";

    private final JdbcSupport db;
    private final FileStorageService storage;
    private final AesCryptoService crypto;
    private final ShopSettingsRepository shopSettingsRepository;

    public SettingsService(JdbcSupport db, FileStorageService storage, AesCryptoService crypto,
                           ShopSettingsRepository shopSettingsRepository) {
        this.db = db;
        this.storage = storage;
        this.crypto = crypto;
        this.shopSettingsRepository = shopSettingsRepository;
    }

    // ==================== shop profile ====================

    @Transactional(readOnly = true)
    public SettingsDtos.ShopSettingsResponse shop(Long shopId) {
        ShopRow row = loadShop(shopId);
        String masked = mask(row.bankDetailsEnc);
        return new SettingsDtos.ShopSettingsResponse(row.id, row.name, row.phone, row.email, row.address,
                row.gstin, row.fssaiNumber, row.upiId, row.logoPath != null, row.logoName,
                row.logoPath != null ? "/api/v1/settings/shop/logo" : null,
                row.bankDetailsEnc != null && !row.bankDetailsEnc.isBlank(), masked, row.currencyCode);
    }

    @Transactional
    public SettingsDtos.ShopSettingsResponse updateShop(Long shopId, Long userId,
                                                      SettingsDtos.ShopSettingsRequest request) {
        ShopRow before = loadShop(shopId);

        // Bank details are write-only: a blank value means "keep what is stored" so the
        // masked-only response can be round-tripped without wiping the real value.
        String bankEnc = before.bankDetailsEnc;
        String submitted = request.bankDetails() == null ? "" : request.bankDetails().trim();
        if (!submitted.isEmpty()) {
            bankEnc = crypto.encrypt(submitted);
        }

        db.jdbc().update("""
                UPDATE shops SET name = :name, mobile = :mobile, email = :email, address = :address,
                       gstin = :gstin, fssai_number = :fssai, upi_id = :upi,
                       bank_details_enc = :bank, updated_at = :now
                WHERE id = :shop AND deleted_at IS NULL""", new MapSqlParameterSource()
                .addValue("name", request.name().trim())
                .addValue("mobile", request.phone().trim())
                .addValue("email", blankToNull(request.email()))
                .addValue("address", blankToNull(request.address()))
                .addValue("gstin", blankToNull(request.gstin()))
                .addValue("fssai", blankToNull(request.fssaiNumber()))
                .addValue("upi", blankToNull(request.upiId()))
                .addValue("bank", bankEnc)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()))
                .addValue("shop", shopId));
        return shop(shopId);
    }

    @Transactional
    public SettingsDtos.ShopSettingsResponse uploadLogo(Long shopId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(400, "Choose a logo image to upload");
        }
        ShopRow before = loadShop(shopId);
        FileStorageService.Stored stored = storage.store(shopId, LOGO_FOLDER, file, false);
        if (stored.contentType() == null || !stored.contentType().startsWith("image/")) {
            storage.delete(stored.path());
            throw new DomainException(400, "The logo must be a JPG, PNG or WEBP image");
        }
        db.jdbc().update("""
                UPDATE shops SET logo_path = :path, logo_name = :name, logo_type = :type, updated_at = :now
                WHERE id = :shop AND deleted_at IS NULL""", new MapSqlParameterSource()
                .addValue("path", stored.path().toString())
                .addValue("name", stored.originalName())
                .addValue("type", stored.contentType())
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()))
                .addValue("shop", shopId));
        if (before.logoPath != null) {
            storage.delete(before.logoPath);
        }
        return shop(shopId);
    }

    @Transactional
    public SettingsDtos.ShopSettingsResponse removeLogo(Long shopId) {
        ShopRow before = loadShop(shopId);
        db.jdbc().update("""
                UPDATE shops SET logo_path = NULL, logo_name = NULL, logo_type = NULL, updated_at = :now
                WHERE id = :shop AND deleted_at IS NULL""", Map.of(
                "now", Timestamp.valueOf(LocalDateTime.now()), "shop", shopId));
        if (before.logoPath != null) {
            storage.delete(before.logoPath);
        }
        return shop(shopId);
    }

    @Transactional(readOnly = true)
    public FileStorageService.Download readLogo(Long shopId) {
        ShopRow row = loadShop(shopId);
        if (row.logoPath == null) {
            throw new DomainException(404, "No logo has been uploaded yet");
        }
        return storage.read(row.logoPath, row.logoName, row.logoType);
    }

// ==================== billing ====================

    @Transactional(readOnly = true)
    public SettingsDtos.BillingSettingsResponse billing(Long shopId) {
        ShopSettings ss = getOrCreateShopSettings(shopId);
        return new SettingsDtos.BillingSettingsResponse(
                ss.getBillPrefix(),
                ss.getFooterMessage() == null ? "" : ss.getFooterMessage(),
                ss.getShowBatchNumber(),
                ss.getShowUpiQr(),
                ss.getShowBankDetails());
    }

    @Transactional
    public SettingsDtos.BillingSettingsResponse updateBilling(Long shopId, Long userId,
                                                              SettingsDtos.BillingSettingsRequest request) {
        String prefix = request.billPrefix().trim().toUpperCase();
        if (prefix.isEmpty()) {
            throw new DomainException(400, "Bill prefix cannot be empty");
        }
        ShopSettings ss = getOrCreateShopSettings(shopId);
        ss.setBillPrefix(prefix);
        ss.setFooterMessage(orEmpty(request.footerMessage()));
        ss.setShowBatchNumber(request.showBatchNumber());
        ss.setShowUpiQr(request.showUpiQr());
        ss.setShowBankDetails(request.showBankDetails());
        shopSettingsRepository.save(ss);
        return billing(shopId);
    }

// ==================== defaults ====================

    @Transactional(readOnly = true)
    public SettingsDtos.DefaultsResponse defaults(Long shopId) {
        ShopSettings ss = getOrCreateShopSettings(shopId);
        return new SettingsDtos.DefaultsResponse(
                ss.getLowStockThreshold(),
                ss.getBestBeforeDays(),
                ss.getCreditSalesAllowed(),
                ss.getCreditPeriodDays(),
                ss.getCreditLimit());
    }

    @Transactional
    public SettingsDtos.DefaultsResponse updateDefaults(Long shopId, Long userId,
                                                        SettingsDtos.DefaultsRequest request) {
        ShopSettings ss = getOrCreateShopSettings(shopId);
        ss.setLowStockThreshold(request.defaultLowStockThreshold());
        ss.setBestBeforeDays(request.defaultBestBeforeDays());
        ss.setCreditSalesAllowed(request.creditSalesAllowed());
        ss.setCreditPeriodDays(request.defaultCreditPeriodDays());
        ss.setCreditLimit(request.defaultCreditLimit());
        shopSettingsRepository.save(ss);
        return defaults(shopId);
    }

// ==================== notifications ====================

    @Transactional(readOnly = true)
    public SettingsDtos.NotificationsResponse notifications(Long shopId) {
        ShopSettings ss = getOrCreateShopSettings(shopId);
        return new SettingsDtos.NotificationsResponse(
                ss.getAlertEmail() == null ? "" : ss.getAlertEmail(),
                ss.getLowStockAlerts(),
                ss.getExpiryAlerts(),
                ss.getLicenceReminders(),
                ss.getPaymentReminders(),
                ss.getDailyDigest());
    }

    @Transactional
    public SettingsDtos.NotificationsResponse updateNotifications(Long shopId, Long userId,
                                                                   SettingsDtos.NotificationsRequest request) {
        ShopSettings ss = getOrCreateShopSettings(shopId);
        ss.setAlertEmail(orEmpty(request.alertEmail()));
        ss.setLowStockAlerts(request.lowStockAlerts());
        ss.setExpiryAlerts(request.expiryAlerts());
        ss.setLicenceReminders(request.licenceReminders());
        ss.setPaymentReminders(request.paymentReminders());
        ss.setDailyDigest(request.dailyDigest());
        shopSettingsRepository.save(ss);
        return notifications(shopId);
    }

    // ==================== my account ====================

    @Transactional(readOnly = true)
    public SettingsDtos.MyAccountResponse myAccount(Long userId) {
        return db.jdbc().query("""
                SELECT u.id, u.name, u.phone, u.email, u.role, u.active, u.last_login_at,
                       u.preferred_language, s.name AS shop_name
                FROM users u JOIN shops s ON s.id = u.shop_id
                WHERE u.id = :user AND u.deleted_at IS NULL""", Map.of("user", userId), (rs, i) ->
                new SettingsDtos.MyAccountResponse(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                        rs.getString("email"), rs.getString("role"), rs.getBoolean("active"),
                        rs.getString("shop_name"), JdbcSupport.dateTime(rs, "last_login_at"),
                        rs.getString("preferred_language"))).stream().findFirst()
                .orElseThrow(() -> new DomainException(404, "Account not found"));
    }

    @Transactional
    public SettingsDtos.MyAccountResponse updateMyAccount(Long userId, SettingsDtos.MyAccountRequest request) {
        String language = request.preferredLanguage() == null || request.preferredLanguage().isBlank()
                ? null : request.preferredLanguage().trim().toLowerCase();
        db.jdbc().update("UPDATE users SET preferred_language = :lang, updated_at = :now WHERE id = :user",
                new MapSqlParameterSource()
                        .addValue("lang", language, java.sql.Types.VARCHAR)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now()))
                        .addValue("user", userId));
        return myAccount(userId);
    }

    // ==================== bill rendering input ====================

    /** Everything the PDF renderer needs about branding, resolved in one pass. */
    public record BillBranding(
            String shopName,
            byte[] logoPng,
            String upiId,
            String footerMessage,
            boolean showBatchNumber,
            boolean showUpiQr,
            boolean showBankDetails,
            String bankDetails,
            String billPrefix) {
    }

    /**
     * Resolves branding for a bill. Deliberately total: any failure degrades to "print
     * the bill without the extras" rather than propagating, because this runs inside
     * the customer's download.
     */
    @Transactional(readOnly = true)
    public BillBranding billBranding(Long shopId) {
        ShopRow row = loadShop(shopId);
        ShopSettings ss = getOrCreateShopSettings(shopId);
        try {
            byte[] logo = readLogoBytes(row.logoPath);
            String bank = null;
            if (row.bankDetailsEnc != null) {
                try {
                    bank = crypto.decrypt(row.bankDetailsEnc);
                } catch (RuntimeException e) {
                    log.warn("Bank details unreadable for shop {}; printing without them", shopId);
                }
            }
            return new BillBranding(row.name, logo, row.upiId,
                    ss.getFooterMessage() == null ? "" : ss.getFooterMessage(),
                    ss.getShowBatchNumber(), ss.getShowUpiQr(), ss.getShowBankDetails(),
                    bank, ss.getBillPrefix());
        } catch (RuntimeException e) {
            log.warn("Falling back to unbranded bills for shop {}", shopId, e);
            return new BillBranding(row.name, null, null, "", false, false, false, null, "S");
        }
    }

    /** Reads logo bytes straight off disk, re-checking the path stays inside the root. */
    private byte[] readLogoBytes(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        try {
            Path root = storage.root();
            Path path = Path.of(storedPath).toAbsolutePath().normalize();
            if (!path.startsWith(root) || !Files.isReadable(path)) {
                return null;
            }
            try (InputStream in = Files.newInputStream(path)) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("Could not read logo at {}", storedPath, e);
            return null;
        }
    }

    // ==================== app_settings plumbing ====================

    @Transactional(readOnly = true)
    public String text(Long shopId, String key, String fallback) {
        List<String> values = db.jdbc().queryForList("""
                SELECT setting_value FROM app_settings
                WHERE shop_id = :shop AND setting_key = :key AND deleted_at IS NULL""",
                Map.of("shop", shopId, "key", key), String.class);
        String value = values.isEmpty() ? null : values.get(0);
        return value == null || value.isBlank() ? fallback : value;
    }

    public boolean flag(Long shopId, String key, boolean fallback) {
        return Boolean.parseBoolean(text(shopId, key, String.valueOf(fallback)));
    }

    public int integer(Long shopId, String key, int fallback) {
        try {
            return Integer.parseInt(text(shopId, key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public BigDecimal decimal(Long shopId, String key, String fallback) {
        try {
            return new BigDecimal(text(shopId, key, fallback).trim());
        } catch (NumberFormatException e) {
            return new BigDecimal(fallback);
        }
    }

    private void putAll(Long shopId, Long userId, Map<String, String> values) {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            int updated = db.jdbc().update("""
                    UPDATE app_settings SET setting_value = :value, updated_by = :by, updated_at = :now
                    WHERE shop_id = :shop AND setting_key = :key AND deleted_at IS NULL""",
                    Map.of("value", entry.getValue(), "by", String.valueOf(userId),
                            "now", Timestamp.valueOf(now), "shop", shopId, "key", entry.getKey()));
            if (updated == 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("shop_id", shopId);
                row.put("setting_key", entry.getKey());
                row.put("setting_value", entry.getValue());
                row.put("updated_by", String.valueOf(userId));
                row.put("created_at", Timestamp.valueOf(now));
                row.put("updated_at", Timestamp.valueOf(now));
                try {
                    db.insert("app_settings", row);
                } catch (RuntimeException e) {
                    // Two concurrent saves raced on the unique key; the other writer won.
                    db.jdbc().update("""
                            UPDATE app_settings SET setting_value = :value, updated_by = :by, updated_at = :now
                            WHERE shop_id = :shop AND setting_key = :key AND deleted_at IS NULL""",
                            Map.of("value", entry.getValue(), "by", String.valueOf(userId),
                                    "now", Timestamp.valueOf(now), "shop", shopId, "key", entry.getKey()));
                }
            }
        }
    }

    // ==================== helpers ====================

    private record ShopRow(Long id, String name, String phone, String email, String address, String gstin,
                           String fssaiNumber, String upiId, String logoPath, String logoName, String logoType,
                           String bankDetailsEnc, String currencyCode) {
    }

    private ShopRow loadShop(Long shopId) {
        return db.jdbc().query(SHOP_SELECT, Map.of("shop", shopId), (rs, i) -> new ShopRow(
                rs.getLong("id"), rs.getString("name"), rs.getString("mobile"), rs.getString("email"),
                rs.getString("address"), rs.getString("gstin"), rs.getString("fssai_number"),
                rs.getString("upi_id"), rs.getString("logo_path"), rs.getString("logo_name"),
                rs.getString("logo_type"), rs.getString("bank_details_enc"), rs.getString("currency_code")))
                .stream().findFirst()
                .orElseThrow(() -> new DomainException(404, "Shop not found"));
    }

    /** Ensures a ShopSettings row exists for the shop (seeded by V19). */
    private ShopSettings getOrCreateShopSettings(Long shopId) {
        return shopSettingsRepository.findByShopId(shopId);
    }

    /**
     * Renders bank details for display without revealing the account number. Shows the
     * holder, bank and the last four digits only - enough to confirm the right account
     * was saved, not enough to be useful if the row leaks.
     */
    private String mask(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        String plain;
        try {
            plain = crypto.decrypt(encrypted);
        } catch (RuntimeException e) {
            return "Stored (unreadable)";
        }
        if (plain == null || plain.isBlank()) {
            return null;
        }
        // Expect lines like "HDFC Bank" / "Account 1234567890" / "IFSC HDFC0001234".
        StringBuilder out = new StringBuilder();
        for (String rawLine : plain.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String maskedLine = line.replaceAll("(?<=\\d)(\\d)(?=\\d{3})", "*");
            if (out.length() > 0) {
                out.append(" • ");
            }
            out.append(maskedLine);
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Renders a boxed switch as the stored "true"/"false". An absent switch is treated
     * as off, which is what a form that never rendered the toggle means.
     */
    private static String flagValue(Boolean value) {
        return String.valueOf(Boolean.TRUE.equals(value));
    }
}