package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.InventoryDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RoleGuard;
import com.ghana.dashboard.service.PackagingItemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/packaging-items")
public class PackagingItemController {

    private final PackagingItemService packagingItemService;

    public PackagingItemController(PackagingItemService packagingItemService) {
        this.packagingItemService = packagingItemService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryDtos.PackagingItemResponse>>> getAll() {
        Long shopId = CurrentUser.getShopId();
        List<InventoryDtos.PackagingItemResponse> items = packagingItemService.getAllPackagingItems(shopId);
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<InventoryDtos.PackagingItemResponse>>> getAllActive() {
        Long shopId = CurrentUser.getShopId();
        List<InventoryDtos.PackagingItemResponse> items = packagingItemService.getAllActivePackagingItems(shopId);
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryDtos.PackagingItemResponse>> getById(@PathVariable Long id) {
        Long shopId = CurrentUser.getShopId();
        InventoryDtos.PackagingItemResponse item = packagingItemService.getPackagingItem(shopId, id);
        return ResponseEntity.ok(ApiResponse.ok(item));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryDtos.PackagingItemResponse>> create(
            @RequestBody InventoryDtos.PackagingItemRequest request) {
        Long shopId = CurrentUser.getShopId();
        InventoryDtos.PackagingItemResponse item = packagingItemService.createPackagingItem(shopId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryDtos.PackagingItemResponse>> update(
            @PathVariable Long id,
            @RequestBody InventoryDtos.PackagingItemRequest request) {
        Long shopId = CurrentUser.getShopId();
        InventoryDtos.PackagingItemResponse item = packagingItemService.updatePackagingItem(shopId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(item));
    }

    @PostMapping("/{id}/purchase")
    public ResponseEntity<ApiResponse<InventoryDtos.PackagingItemResponse>> recordPurchase(
            @PathVariable Long id,
            @RequestBody InventoryDtos.PackagingPurchaseRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        packagingItemService.recordPurchase(shopId, id, request, userId);
        
        InventoryDtos.PackagingItemResponse item = packagingItemService.getPackagingItem(shopId, id);
        return ResponseEntity.ok(ApiResponse.ok("Purchase recorded", item));
    }

    @PostMapping("/{id}/adjust")
    public ResponseEntity<ApiResponse<InventoryDtos.PackagingItemResponse>> adjustStock(
            @PathVariable Long id,
            @RequestBody InventoryDtos.PackagingStockAdjustmentRequest request) {
        Long shopId = CurrentUser.getShopId();
        Long userId = CurrentUser.getUserId();
        packagingItemService.adjustStock(shopId, id, request, userId);
        
        InventoryDtos.PackagingItemResponse item = packagingItemService.getPackagingItem(shopId, id);
        return ResponseEntity.ok(ApiResponse.ok("Stock adjusted", item));
    }

    @DeleteMapping("/{id}")
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long shopId = CurrentUser.getShopId();
        packagingItemService.deletePackagingItem(shopId, id);
        return ResponseEntity.ok(ApiResponse.success(200, "Packaging item deleted"));
    }

    @GetMapping("/{id}/movements")
    public ResponseEntity<ApiResponse<Page<InventoryDtos.PackagingMovementResponse>>> getMovements(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        Long shopId = CurrentUser.getShopId();
        InventoryDtos.PackagingMovementFilter filter = new InventoryDtos.PackagingMovementFilter(
                id, type, null, null, pageable.getPageNumber(), pageable.getPageSize());
        Page<InventoryDtos.PackagingMovementResponse> movements = 
                packagingItemService.getPackagingMovements(shopId, filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(movements));
    }
}
