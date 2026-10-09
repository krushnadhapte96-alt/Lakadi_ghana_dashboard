package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.domain.LossLedger;
import com.ghana.dashboard.dto.ExpenseDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.LossLedgerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/loss-ledger")
@PreAuthorize("hasRole('OWNER')")
public class LossLedgerController {

    private final LossLedgerService lossLedgerService;

    public LossLedgerController(LossLedgerService lossLedgerService) {
        this.lossLedgerService = lossLedgerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseDtos.LossLedgerResponse>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) LossLedger.LossType lossType,
            @RequestParam(required = false) Long productId) {
        LocalDate from = dateFrom != null ? dateFrom : DateDefaults.monthStart();
        LocalDate to = dateTo != null ? dateTo : DateDefaults.today();
        DateDefaults.requireOrdered(from, to);
        return ResponseEntity.ok(ApiResponse.ok(
                lossLedgerService.getLossEntries(CurrentUser.getShopId(), from, to, lossType, productId)));
    }
}
