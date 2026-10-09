package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.common.DateDefaults;
import com.ghana.dashboard.domain.Payment;
import com.ghana.dashboard.dto.ExpenseDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/expenses")
@PreAuthorize("hasRole('OWNER')")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExpenseDtos.ExpenseResponse>> create(
            @Valid @ModelAttribute ExpenseDtos.ExpenseForm form) {
        ExpenseDtos.ExpenseResponse created =
                expenseService.createExpense(CurrentUser.getShopId(), CurrentUser.getUserId(), form);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExpenseDtos.ExpenseResponse>> update(
            @PathVariable Long id, @Valid @ModelAttribute ExpenseDtos.ExpenseForm form) {
        ExpenseDtos.ExpenseResponse updated =
                expenseService.updateExpense(CurrentUser.getShopId(), CurrentUser.getUserId(), id, form);
        return ResponseEntity.ok(ApiResponse.ok("Expense updated", updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseDtos.ExpenseResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getExpense(CurrentUser.getShopId(), id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseDtos.ExpenseSummaryResponse>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Payment.PaymentMode paymentMode) {
        LocalDate from = dateFrom != null ? dateFrom : DateDefaults.monthStart();
        LocalDate to = dateTo != null ? dateTo : DateDefaults.today();
        DateDefaults.requireOrdered(from, to);
        return ResponseEntity.ok(ApiResponse.ok(
                expenseService.getExpenses(CurrentUser.getShopId(), from, to, categoryId, paymentMode)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        expenseService.deleteExpense(CurrentUser.getShopId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Expense deleted", null));
    }

    @GetMapping("/by-category")
    public ResponseEntity<ApiResponse<List<ExpenseDtos.ExpenseBreakdownResponse>>> byCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        LocalDate from = dateFrom != null ? dateFrom : DateDefaults.monthStart();
        LocalDate to = dateTo != null ? dateTo : DateDefaults.today();
        DateDefaults.requireOrdered(from, to);
        return ResponseEntity.ok(ApiResponse.ok(
                expenseService.getBreakdownByCategory(CurrentUser.getShopId(), from, to)));
    }

    @GetMapping("/{id}/bill")
    public ResponseEntity<Resource> bill(@PathVariable Long id) {
        ExpenseService.BillDownload bill = expenseService.getBill(CurrentUser.getShopId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bill.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(bill.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(bill.resource());
    }
}
