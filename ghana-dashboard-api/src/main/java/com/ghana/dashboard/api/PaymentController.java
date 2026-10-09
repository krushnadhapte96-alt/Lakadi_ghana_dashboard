package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.dto.PaymentDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RoleGuard;
import com.ghana.dashboard.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentDtos.PaymentResponse>> createPayment(
            @Valid @RequestBody PaymentDtos.PaymentRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        PaymentDtos.PaymentResponse payment = paymentService.createPayment(shopId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentDtos.PaymentResponse>> getPayment(@PathVariable Long id) {
        Long shopId = CurrentUser.getShopId();
        PaymentDtos.PaymentResponse payment = paymentService.getPayment(shopId, id);
        return ResponseEntity.ok(ApiResponse.ok(payment));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentDtos.PaymentSummaryResponse>>> getPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Payment.Direction direction,
            @RequestParam(required = false) Payment.PaymentMode mode,
            @RequestParam(required = false) Payment.SourceType sourceType,
            Pageable pageable) {
        Long shopId = CurrentUser.getShopId();
        PaymentDtos.PaymentFilter filter = new PaymentDtos.PaymentFilter(
                dateFrom, dateTo, direction, mode, sourceType,
                pageable.getPageNumber(), pageable.getPageSize());
        List<PaymentDtos.PaymentSummaryResponse> payments = paymentService.getPayments(shopId, filter);
        return ResponseEntity.ok(ApiResponse.ok(payments));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PaymentDtos.PendingPaymentResponse>>> getPendingPayments() {
        Long shopId = CurrentUser.getShopId();
        List<PaymentDtos.PendingPaymentResponse> pending = paymentService.getPendingPayments(shopId);
        return ResponseEntity.ok(ApiResponse.ok(pending));
    }

    @GetMapping("/export.csv")
    public ResponseEntity<String> exportPaymentsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        Long shopId = CurrentUser.getShopId();
        LocalDate defaultFrom = LocalDate.now().minusMonths(1);
        LocalDate defaultTo = LocalDate.now();
        String csv = paymentService.exportPaymentsCsv(shopId, 
                dateFrom != null ? dateFrom : defaultFrom,
                dateTo != null ? dateTo : defaultTo);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments_" + defaultFrom + "_to_" + defaultTo + ".csv")
                .body(csv);
    }
}
