package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.BeejDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RoleGuard;
import com.ghana.dashboard.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BeejDtos.SupplierResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getAllSuppliers(CurrentUser.getShopId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BeejDtos.SupplierResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getSupplier(CurrentUser.getShopId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BeejDtos.SupplierResponse>> create(
            @Valid @RequestBody BeejDtos.SupplierRequest request) {
        BeejDtos.SupplierResponse created = supplierService.createSupplier(CurrentUser.getShopId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BeejDtos.SupplierResponse>> update(
            @PathVariable Long id, @Valid @RequestBody BeejDtos.SupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplierService.updateSupplier(CurrentUser.getShopId(), id, request)));
    }

    @DeleteMapping("/{id}")
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        supplierService.deleteSupplier(CurrentUser.getShopId(), id);
        return ResponseEntity.ok(ApiResponse.success(200, "Supplier deleted"));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<BeejDtos.SupplierPaymentResponse>> recordPayment(
            @PathVariable Long id, @Valid @RequestBody BeejDtos.SupplierPaymentRequest request) {
        BeejDtos.SupplierPaymentResponse payment = supplierService.recordPayment(
                CurrentUser.getShopId(), id, request, CurrentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Payment recorded", payment));
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<ApiResponse<BeejDtos.SupplierLedgerResponse>> getLedger(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getLedger(CurrentUser.getShopId(), id)));
    }
}
