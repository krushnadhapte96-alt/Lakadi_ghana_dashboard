package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.InventoryDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final ProductService productService;

    public InventoryController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<InventoryDtos.AggregatedInventoryResponse>> getAggregated() {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getAggregatedInventory(CurrentUser.getShopId())));
    }

    @GetMapping("/{productId}/batches")
    public ResponseEntity<ApiResponse<List<InventoryDtos.BatchStockResponse>>> getBatches(
            @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getProductBatches(CurrentUser.getShopId(), productId)));
    }

    @PostMapping("/{productId}/opening-stock")
    public ResponseEntity<ApiResponse<Void>> addOpeningStock(
            @PathVariable Long productId,
            @Valid @RequestBody InventoryDtos.OpeningStockRequest request) {
        productService.addOpeningStock(CurrentUser.getShopId(), productId, request, CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success(200, "Opening stock added"));
    }

    @PostMapping("/{productId}/adjust")
    public ResponseEntity<ApiResponse<Void>> adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody InventoryDtos.StockAdjustmentRequest request) {
        productService.adjustStock(CurrentUser.getShopId(), productId, request, CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success(200, "Stock adjusted"));
    }

    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<Page<InventoryDtos.StockMovementResponse>>> getMovements(
            @ModelAttribute InventoryDtos.StockMovementFilter filter,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getStockMovements(CurrentUser.getShopId(), filter, pageable)));
    }
}
