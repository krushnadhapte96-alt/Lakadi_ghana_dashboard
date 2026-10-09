package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.domain.SeedPurchase;
import com.ghana.dashboard.dto.BeejDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RoleGuard;
import com.ghana.dashboard.service.SeedPurchaseService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seed-purchases")
public class SeedPurchaseController {

    private final SeedPurchaseService seedPurchaseService;

    public SeedPurchaseController(SeedPurchaseService seedPurchaseService) {
        this.seedPurchaseService = seedPurchaseService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BeejDtos.SeedPurchaseResponse>> create(
            @Valid @ModelAttribute BeejDtos.SeedPurchaseForm form) {
        BeejDtos.SeedPurchaseResponse created =
                seedPurchaseService.createPurchase(CurrentUser.getShopId(), CurrentUser.getUserId(), form);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BeejDtos.SeedPurchaseResponse>> update(
            @PathVariable Long id, @Valid @ModelAttribute BeejDtos.SeedPurchaseUpdateForm form) {
        BeejDtos.SeedPurchaseResponse updated = seedPurchaseService.updatePurchase(
                CurrentUser.getShopId(), CurrentUser.getUserId(), id, form);
        return ResponseEntity.ok(ApiResponse.ok("Purchase updated", updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BeejDtos.SeedPurchaseResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(seedPurchaseService.getPurchase(CurrentUser.getShopId(), id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BeejDtos.SeedPurchaseSummaryResponse>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long seedTypeId,
            @RequestParam(required = false) SeedPurchase.PaymentStatus paymentStatus) {
        LocalDate from = dateFrom != null ? dateFrom : DateDefaults.monthStart();
        LocalDate to = dateTo != null ? dateTo : DateDefaults.today();
        DateDefaults.requireOrdered(from, to);
        return ResponseEntity.ok(ApiResponse.ok(seedPurchaseService.getPurchases(
                CurrentUser.getShopId(), from, to, supplierId, seedTypeId, paymentStatus)));
    }

    @DeleteMapping("/{id}")
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        seedPurchaseService.deletePurchase(CurrentUser.getShopId(), id);
        return ResponseEntity.ok(ApiResponse.success(200, "Purchase deleted"));
    }

    @GetMapping("/{id}/bill")
    public ResponseEntity<Resource> bill(@PathVariable Long id) {
        SeedPurchaseService.BillDownload bill = seedPurchaseService.getBill(CurrentUser.getShopId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bill.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(bill.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(bill.resource());
    }
}
