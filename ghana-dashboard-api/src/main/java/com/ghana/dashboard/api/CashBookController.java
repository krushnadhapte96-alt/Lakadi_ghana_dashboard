package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.PaymentDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RoleGuard;
import com.ghana.dashboard.service.CashBookDayService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cash-book")
public class CashBookController {

    private final CashBookDayService cashBookDayService;

    public CashBookController(CashBookDayService cashBookDayService) {
        this.cashBookDayService = cashBookDayService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaymentDtos.CashBookDayResponse>> getCashBookDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        PaymentDtos.CashBookDayResponse cashBookDay = cashBookDayService.getCashBookDay(
                shopId, date, userId);
        return ResponseEntity.ok(ApiResponse.ok(cashBookDay));
    }

    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<List<PaymentDtos.CashBookEntryResponse>>> getCashBookEntries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long shopId = CurrentUser.getShopId();
        List<PaymentDtos.CashBookEntryResponse> entries = cashBookDayService.getCashBookEntries(
                shopId, date);
        return ResponseEntity.ok(ApiResponse.ok(entries));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PaymentDtos.CashBookSummaryResponse>> getCashBookSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long shopId = CurrentUser.getShopId();
        PaymentDtos.CashBookSummaryResponse summary = cashBookDayService.getCashBookSummary(
                shopId, date);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @PostMapping("/{date}/close")
    public ResponseEntity<ApiResponse<PaymentDtos.CashBookDayResponse>> closeCashBook(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody PaymentDtos.CloseCashBookRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        PaymentDtos.CashBookDayResponse cashBookDay = cashBookDayService.closeDay(
                shopId, date, request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Cash book closed", cashBookDay));
    }

    @PostMapping("/{date}/reopen")
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<PaymentDtos.CashBookDayResponse>> reopenCashBook(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody PaymentDtos.ReopenCashBookRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        PaymentDtos.CashBookDayResponse cashBookDay = cashBookDayService.reopenDay(
                shopId, date, request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Cash book reopened", cashBookDay));
    }

    @GetMapping("/{date}/report.pdf")
    public ResponseEntity<byte[]> downloadCashBookReport(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long shopId = CurrentUser.getShopId();
        byte[] pdfBytes = cashBookDayService.generateCashBookReportPdf(shopId, date);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=cash_book_" + date + ".pdf")
                .body(pdfBytes);
    }
}
