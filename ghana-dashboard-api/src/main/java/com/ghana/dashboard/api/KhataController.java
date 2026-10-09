package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.KhataDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.KhataService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/khata")
public class KhataController {

    private final KhataService khata;

    public KhataController(KhataService khata) {
        this.khata = khata;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<KhataDtos.SummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(khata.summary(CurrentUser.getShopId())));
    }

    @GetMapping("/parties")
    public ResponseEntity<ApiResponse<List<KhataDtos.PartyResponse>>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean withBalance,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(khata.listParties(CurrentUser.getShopId(), type, overdue, withBalance, q)));
    }

    @PostMapping("/parties")
    public ResponseEntity<ApiResponse<KhataDtos.PartyResponse>> create(@Valid @RequestBody KhataDtos.PartyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(khata.createParty(CurrentUser.getShopId(), request)));
    }

    @GetMapping("/parties/{id}")
    public ResponseEntity<ApiResponse<KhataDtos.PartyResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(khata.getParty(CurrentUser.getShopId(), id)));
    }

    @PutMapping("/parties/{id}")
    public ResponseEntity<ApiResponse<KhataDtos.PartyResponse>> update(@PathVariable Long id,
                                                                       @Valid @RequestBody KhataDtos.PartyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(khata.updateParty(CurrentUser.getShopId(), id, request)));
    }

    @DeleteMapping("/parties/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        khata.deleteParty(CurrentUser.getShopId(), id);
        return ResponseEntity.ok(ApiResponse.success(200, "Party removed"));
    }

    @GetMapping("/parties/{id}/ledger")
    public ResponseEntity<ApiResponse<KhataDtos.LedgerResponse>> ledger(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(khata.ledger(CurrentUser.getShopId(), id)));
    }

    @PostMapping("/parties/{id}/credit-sale")
    public ResponseEntity<ApiResponse<KhataDtos.LedgerResponse>> creditSale(
            @PathVariable Long id, @Valid @RequestBody KhataDtos.CreditSaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Credit sale recorded",
                khata.creditSale(CurrentUser.getShopId(), CurrentUser.getUserId(), id, request)));
    }

    @PostMapping("/parties/{id}/payment")
    public ResponseEntity<ApiResponse<KhataDtos.LedgerResponse>> payment(
            @PathVariable Long id, @Valid @RequestBody KhataDtos.PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Payment recorded",
                khata.receivePayment(CurrentUser.getShopId(), CurrentUser.getUserId(), id, request)));
    }

    @PostMapping("/parties/{id}/adjustment")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<KhataDtos.LedgerResponse>> adjustment(
            @PathVariable Long id, @Valid @RequestBody KhataDtos.AdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Adjustment recorded",
                khata.adjust(CurrentUser.getShopId(), CurrentUser.getUserId(), id, request)));
    }

    @GetMapping("/parties/{id}/statement.pdf")
    public ResponseEntity<byte[]> statement(@PathVariable Long id) {
        byte[] pdf = khata.statementPdf(CurrentUser.getShopId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("khata-statement-" + id + ".pdf").build().toString())
                .body(pdf);
    }

    @GetMapping("/parties/{id}/whatsapp-reminder")
    public ResponseEntity<ApiResponse<KhataDtos.ReminderResponse>> reminder(
            @PathVariable Long id, @RequestParam(defaultValue = "en") String lang) {
        return ResponseEntity.ok(ApiResponse.ok(khata.whatsappReminder(CurrentUser.getShopId(), id, lang)));
    }
}
