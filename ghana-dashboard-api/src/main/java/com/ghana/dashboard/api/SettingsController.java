package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.SettingsDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.ExportService;
import com.ghana.dashboard.service.FileStorageService;
import com.ghana.dashboard.service.SettingsService;
import com.ghana.dashboard.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Settings. Class-level owner guard, because every settings surface is the owner's
 * business except {@link #myAccount()} / {@link #updateMyAccount()}, which override it
 * with their own narrower guards.
 */
@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasRole('OWNER')")
public class SettingsController {

    private final SettingsService settings;
    private final StaffService staff;
    private final ExportService exports;

    public SettingsController(SettingsService settings, StaffService staff, ExportService exports) {
        this.settings = settings;
        this.staff = staff;
        this.exports = exports;
    }

    // ==================== shop profile ====================

    @GetMapping("/shop")
    public ResponseEntity<ApiResponse<SettingsDtos.ShopSettingsResponse>> shop() {
        return ResponseEntity.ok(ApiResponse.ok(settings.shop(CurrentUser.getShopId())));
    }

    /**
     * Logo changes ride along in a multipart request so the owner saves the picture and
     * the details in one go; edits that do not touch the logo use the JSON handler below.
     */
    @PutMapping(value = "/shop", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SettingsDtos.ShopSettingsResponse>> updateShopWithLogo(
            @RequestPart("shop") @Valid SettingsDtos.ShopSettingsRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        SettingsDtos.ShopSettingsResponse saved = settings.updateShop(shopId, userId, request);
        if (logo != null && !logo.isEmpty()) {
            saved = settings.uploadLogo(shopId, logo);
        }
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PutMapping(value = "/shop", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<SettingsDtos.ShopSettingsResponse>> updateShop(
            @Valid @RequestBody SettingsDtos.ShopSettingsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(settings.updateShop(CurrentUser.getShopId(), CurrentUser.getUserId(), request)));
    }

    @PostMapping(value = "/shop/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SettingsDtos.ShopSettingsResponse>> uploadLogo(
            @RequestPart("logo") MultipartFile logo) {
        return ResponseEntity.ok(
                ApiResponse.ok(settings.uploadLogo(CurrentUser.getShopId(), logo)));
    }

    @DeleteMapping("/shop/logo")
    public ResponseEntity<ApiResponse<SettingsDtos.ShopSettingsResponse>> removeLogo() {
        return ResponseEntity.ok(ApiResponse.ok(settings.removeLogo(CurrentUser.getShopId())));
    }

    @GetMapping("/shop/logo")
    public ResponseEntity<org.springframework.core.io.Resource> logo() {
        FileStorageService.Download file = settings.readLogo(CurrentUser.getShopId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate())
                .body(file.resource());
    }

    // ==================== billing ====================

    @GetMapping("/billing")
    public ResponseEntity<ApiResponse<SettingsDtos.BillingSettingsResponse>> billing() {
        return ResponseEntity.ok(ApiResponse.ok(settings.billing(CurrentUser.getShopId())));
    }

    @PutMapping("/billing")
    public ResponseEntity<ApiResponse<SettingsDtos.BillingSettingsResponse>> updateBilling(
            @Valid @RequestBody SettingsDtos.BillingSettingsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(settings.updateBilling(CurrentUser.getShopId(), CurrentUser.getUserId(), request)));
    }

    // ==================== defaults ====================

    @GetMapping("/defaults")
    public ResponseEntity<ApiResponse<SettingsDtos.DefaultsResponse>> defaults() {
        return ResponseEntity.ok(ApiResponse.ok(settings.defaults(CurrentUser.getShopId())));
    }

    @PutMapping("/defaults")
    public ResponseEntity<ApiResponse<SettingsDtos.DefaultsResponse>> updateDefaults(
            @Valid @RequestBody SettingsDtos.DefaultsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(settings.updateDefaults(CurrentUser.getShopId(), CurrentUser.getUserId(), request)));
    }

    // ==================== notifications ====================

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<SettingsDtos.NotificationsResponse>> notifications() {
        return ResponseEntity.ok(ApiResponse.ok(settings.notifications(CurrentUser.getShopId())));
    }

    @PutMapping("/notifications")
    public ResponseEntity<ApiResponse<SettingsDtos.NotificationsResponse>> updateNotifications(
            @Valid @RequestBody SettingsDtos.NotificationsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                settings.updateNotifications(CurrentUser.getShopId(), CurrentUser.getUserId(), request)));
    }

    // ==================== staff ====================

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<SettingsDtos.StaffResponse>>> staff() {
        return ResponseEntity.ok(ApiResponse.ok(staff.list(CurrentUser.getShopId())));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<SettingsDtos.StaffResponse>> createStaff(
            @Valid @RequestBody SettingsDtos.StaffCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(staff.create(CurrentUser.getShopId(), request)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<SettingsDtos.StaffResponse>> updateStaff(
            @PathVariable Long id, @Valid @RequestBody SettingsDtos.StaffUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(staff.update(CurrentUser.getShopId(), id, request)));
    }

    @PutMapping("/users/{id}/active")
    public ResponseEntity<ApiResponse<SettingsDtos.StaffResponse>> setStaffActive(
            @PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(
                ApiResponse.ok(staff.setActive(CurrentUser.getShopId(), id, active)));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<SettingsDtos.StaffResponse>> resetStaffPassword(
            @PathVariable Long id, @Valid @RequestBody SettingsDtos.StaffResetPasswordRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(staff.resetPassword(CurrentUser.getShopId(), id, request.password())));
    }

    // ==================== export ====================

    @GetMapping("/export/manifest")
    public ResponseEntity<ApiResponse<SettingsDtos.ExportManifest>> exportManifest() {
        return ResponseEntity.ok(ApiResponse.ok(exports.manifest(CurrentUser.getShopId())));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        Long shopId = CurrentUser.getShopId();
        byte[] zip = exports.zip(shopId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(exports.fileName(shopId), StandardCharsets.UTF_8).build().toString())
                .contentLength(zip.length)
                .body(zip);
    }

    // ==================== my account ====================
    // Any signed-in user, including staff. These deliberately cannot reach shop,
    // staff or billing settings.

    @GetMapping("/my-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SettingsDtos.MyAccountResponse>> myAccount() {
        return ResponseEntity.ok(ApiResponse.ok(settings.myAccount(CurrentUser.getUserId())));
    }

    @PutMapping("/my-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SettingsDtos.MyAccountResponse>> updateMyAccount(
            @Valid @RequestBody SettingsDtos.MyAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(settings.updateMyAccount(CurrentUser.getUserId(), request)));
    }
}