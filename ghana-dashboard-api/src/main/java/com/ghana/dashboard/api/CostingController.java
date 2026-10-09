package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.CostingDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.CostingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class CostingController {

    private final CostingService costingService;

    public CostingController(CostingService costingService) {
        this.costingService = costingService;
    }

    @GetMapping("/cost-defaults")
    public ResponseEntity<ApiResponse<CostingDtos.CostDefaultsResponse>> getCostDefaults() {
        return ResponseEntity.ok(ApiResponse.ok(costingService.getCostDefaults(CurrentUser.getShopId())));
    }

    @PutMapping("/cost-defaults")
    public ResponseEntity<ApiResponse<CostingDtos.CostDefaultsResponse>> updateCostDefaults(
            @Valid @RequestBody CostingDtos.CostDefaultsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                costingService.updateCostDefaults(CurrentUser.getShopId(), request)));
    }

    @GetMapping("/batches/{id}/cost")
    public ResponseEntity<ApiResponse<CostingDtos.BatchCostResponse>> getBatchCost(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(costingService.getBatchCost(CurrentUser.getShopId(), id)));
    }

    @PutMapping("/batches/{id}/cost")
    public ResponseEntity<ApiResponse<CostingDtos.BatchCostResponse>> updateBatchCost(
            @PathVariable Long id, @RequestBody CostingDtos.BatchCostUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                costingService.updateBatchCost(CurrentUser.getShopId(), id, request)));
    }

    @PostMapping("/batches/{id}/cost/recalculate")
    public ResponseEntity<ApiResponse<CostingDtos.BatchCostResponse>> recalculateBatchCost(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                costingService.recalculateBatchCost(CurrentUser.getShopId(), id)));
    }

    @GetMapping("/costing/products")
    public ResponseEntity<ApiResponse<List<CostingDtos.CostingProductResponse>>> getCostingProducts() {
        return ResponseEntity.ok(ApiResponse.ok(costingService.getCostingProducts(CurrentUser.getShopId())));
    }

    @PostMapping("/products/{id}/apply-suggested-price")
    public ResponseEntity<ApiResponse<CostingDtos.ApplySuggestedPriceResponse>> applySuggestedPrice(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                costingService.applySuggestedPrice(CurrentUser.getShopId(), id)));
    }
}
