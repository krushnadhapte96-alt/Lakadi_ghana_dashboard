package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.domain.Order;
import com.ghana.dashboard.dto.SalesDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.BillPdfService;
import com.ghana.dashboard.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final BillPdfService billPdfService;

    public OrderController(OrderService orderService, BillPdfService billPdfService) {
        this.orderService = orderService;
        this.billPdfService = billPdfService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalesDtos.OrderResponse>> createOrder(
            @RequestBody SalesDtos.OrderRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        SalesDtos.OrderResponse order = orderService.createOrder(shopId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesDtos.OrderResponse>> getOrder(@PathVariable Long id) {
        Long shopId = CurrentUser.getShopId();
        SalesDtos.OrderResponse order = orderService.getOrder(shopId, id);
        return ResponseEntity.ok(ApiResponse.ok(order));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SalesDtos.OrderSummaryResponse>>> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Pageable pageable) {
        Long shopId = CurrentUser.getShopId();
        SalesDtos.OrderFilter filter = new SalesDtos.OrderFilter(
                status, customerId, dateFrom, dateTo,
                pageable.getPageNumber(), pageable.getPageSize());
        Page<SalesDtos.OrderSummaryResponse> orders = orderService.getOrders(shopId, filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }

    @GetMapping("/status-counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusCounts() {
        Long shopId = CurrentUser.getShopId();
        Map<String, Long> counts = Map.of(
                "NEW", orderService.countByStatus(shopId, "NEW"),
                "CONFIRMED", orderService.countByStatus(shopId, "CONFIRMED"),
                "PACKED", orderService.countByStatus(shopId, "PACKED"),
                "OUT_FOR_DELIVERY", orderService.countByStatus(shopId, "OUT_FOR_DELIVERY"),
                "DELIVERED", orderService.countByStatus(shopId, "DELIVERED"),
                "CANCELLED", orderService.countByStatus(shopId, "CANCELLED")
        );
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesDtos.OrderResponse>> updateOrder(
            @PathVariable Long id,
            @RequestBody SalesDtos.OrderUpdateRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        SalesDtos.OrderResponse order = orderService.updateOrder(shopId, id, request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Order updated", order));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SalesDtos.OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody SalesDtos.OrderStatusUpdateRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        SalesDtos.OrderResponse order = orderService.updateOrderStatus(shopId, id, request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Order status updated", order));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SalesDtos.OrderResponse>> cancelOrder(
            @PathVariable Long id,
            @RequestBody SalesDtos.OrderCancelRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        SalesDtos.OrderResponse order = orderService.cancelOrder(shopId, id, request, userId);
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled", order));
    }

    @GetMapping("/{id}/bill.pdf")
    public ResponseEntity<byte[]> downloadBillPdf(@PathVariable Long id) {
        Long shopId = CurrentUser.getShopId();
        Order order = orderService.findById(id);
        if (!order.getShopId().equals(shopId)) {
            throw new RuntimeException("Order does not belong to this shop");
        }
        
        byte[] pdfBytes = billPdfService.generateOrderBillPdf(order);
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=order_" + order.getBillNo() + ".pdf")
                .body(pdfBytes);
    }
}
