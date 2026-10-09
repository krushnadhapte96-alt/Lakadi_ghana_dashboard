package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.domain.Sale;
import com.ghana.dashboard.dto.SalesDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RoleGuard;
import com.ghana.dashboard.service.BillPdfService;
import com.ghana.dashboard.service.SaleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleService saleService;
    private final BillPdfService billPdfService;

    public SaleController(SaleService saleService, BillPdfService billPdfService) {
        this.saleService = saleService;
        this.billPdfService = billPdfService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalesDtos.SaleResponse>> createSale(
            @RequestBody SalesDtos.SaleRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        SalesDtos.SaleResponse sale = saleService.createSale(shopId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(sale));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesDtos.SaleResponse>> getSale(@PathVariable Long id) {
        Long shopId = CurrentUser.getShopId();
        SalesDtos.SaleResponse sale = saleService.getSale(shopId, id);
        return ResponseEntity.ok(ApiResponse.ok(sale));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SalesDtos.SaleSummaryResponse>>> getSales(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Pageable pageable) {
        Long shopId = CurrentUser.getShopId();
        SalesDtos.SaleFilter filter = new SalesDtos.SaleFilter(
                customerId, paymentMode, paymentStatus, dateFrom, dateTo,
                pageable.getPageNumber(), pageable.getPageSize());
        Page<SalesDtos.SaleSummaryResponse> sales = saleService.getSales(shopId, filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(sales));
    }

    @PostMapping("/{id}/void")
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<SalesDtos.SaleResponse>> voidSale(
            @PathVariable Long id,
            @RequestBody SalesDtos.SaleVoidRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        SalesDtos.SaleResponse sale = saleService.voidSale(shopId, id, request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Sale voided", sale));
    }

    @PatchMapping("/{id}/payment-status")
    public ResponseEntity<ApiResponse<SalesDtos.SaleResponse>> updatePaymentStatus(
            @PathVariable Long id,
            @RequestBody SalesDtos.UpdatePaymentStatusRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        SalesDtos.SaleResponse sale = saleService.updatePaymentStatus(shopId, id, request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Payment status updated", sale));
    }

    @GetMapping("/{id}/bill.pdf")
    public ResponseEntity<byte[]> downloadBillPdf(@PathVariable Long id) {
        Long shopId = CurrentUser.getShopId();
        Sale sale = saleService.findById(id);
        if (!sale.getShopId().equals(shopId)) {
            throw new RuntimeException("Sale does not belong to this shop");
        }
        
        byte[] pdfBytes = billPdfService.generateSaleBillPdf(sale);
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=bill_" + sale.getBillNo() + ".pdf")
                .body(pdfBytes);
    }
}
