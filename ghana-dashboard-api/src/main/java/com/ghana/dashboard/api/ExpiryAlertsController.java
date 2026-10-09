package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.ExpiryAlertDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.ExpiryAlertsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expiry-alerts")
public class ExpiryAlertsController {

    private final ExpiryAlertsService expiryAlertsService;

    public ExpiryAlertsController(ExpiryAlertsService expiryAlertsService) {
        this.expiryAlertsService = expiryAlertsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ExpiryAlertDtos.ExpiryAlertsResponse>> getAlerts() {
        return ResponseEntity.ok(ApiResponse.ok(expiryAlertsService.getAlerts(CurrentUser.getShopId())));
    }

    @PostMapping("/{batchId}/discount")
    public ResponseEntity<ApiResponse<ExpiryAlertDtos.ExpiryAlertItemResponse>> giveDiscount(
            @PathVariable Long batchId, @Valid @RequestBody ExpiryAlertDtos.DiscountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                expiryAlertsService.giveDiscount(CurrentUser.getShopId(), batchId, request, CurrentUser.getUserId())));
    }

    @PostMapping("/{batchId}/write-off")
    public ResponseEntity<ApiResponse<ExpiryAlertDtos.ExpiryAlertItemResponse>> writeOff(
            @PathVariable Long batchId, @Valid @RequestBody ExpiryAlertDtos.WriteOffRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                expiryAlertsService.writeOff(CurrentUser.getShopId(), batchId, request, CurrentUser.getUserId())));
    }

    @PostMapping("/{batchId}/snooze")
    public ResponseEntity<ApiResponse<ExpiryAlertDtos.ExpiryAlertItemResponse>> snooze(
            @PathVariable Long batchId, @Valid @RequestBody ExpiryAlertDtos.SnoozeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                expiryAlertsService.snooze(CurrentUser.getShopId(), batchId, request, CurrentUser.getUserId())));
    }
}
