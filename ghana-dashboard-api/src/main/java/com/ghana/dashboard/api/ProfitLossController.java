package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.dto.ExpenseDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.ProfitLossService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/pnl")
@PreAuthorize("hasRole('OWNER')")
public class ProfitLossController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final ProfitLossService profitLossService;

    public ProfitLossController(ProfitLossService profitLossService) {
        this.profitLossService = profitLossService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ExpenseDtos.PnLSummaryResponse>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "month") String groupBy) {
        LocalDate start = from != null ? from : DateDefaults.monthStart();
        LocalDate end = to != null ? to : DateDefaults.today();
        return ResponseEntity.ok(ApiResponse.ok(
                profitLossService.getProfitLoss(CurrentUser.getShopId(), start, end, groupBy)));
    }

    @GetMapping("/export.csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "month") String groupBy) {
        LocalDate start = from != null ? from : DateDefaults.monthStart();
        LocalDate end = to != null ? to : DateDefaults.today();
        String csv = profitLossService.exportPnLCsv(CurrentUser.getShopId(), start, end, groupBy);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("pnl_" + start + "_to_" + end + ".csv"))
                .body(csv);
    }

    @GetMapping("/report.pdf")
    public ResponseEntity<byte[]> reportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "month") String groupBy) {
        LocalDate start = from != null ? from : DateDefaults.monthStart();
        LocalDate end = to != null ? to : DateDefaults.today();
        byte[] pdf = profitLossService.generatePnLReportPdf(CurrentUser.getShopId(), start, end, groupBy);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("pnl_" + start + "_to_" + end + ".pdf"))
                .body(pdf);
    }

    private static String attachment(String filename) {
        return ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString();
    }
}
