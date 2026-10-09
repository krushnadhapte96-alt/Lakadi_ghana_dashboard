package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.BatchDtos;
import com.ghana.dashboard.dto.InventoryDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RoleGuard;
import com.ghana.dashboard.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryDtos.ProductResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getAllProducts(CurrentUser.getShopId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryDtos.ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getProduct(CurrentUser.getShopId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryDtos.ProductResponse>> create(
            @Valid @RequestBody InventoryDtos.ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(productService.createProduct(CurrentUser.getShopId(), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryDtos.ProductResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody InventoryDtos.ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.updateProduct(CurrentUser.getShopId(), id, request)));
    }

    @PatchMapping("/{id}/price")
    public ResponseEntity<ApiResponse<Void>> updatePrice(
            @PathVariable Long id,
            @Valid @RequestBody InventoryDtos.UpdateProductPriceRequest request) {
        productService.updateProductPrice(CurrentUser.getShopId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Product price updated"));
    }

    @PatchMapping("/{id}/threshold")
    public ResponseEntity<ApiResponse<Void>> updateThreshold(
            @PathVariable Long id,
            @Valid @RequestBody InventoryDtos.UpdateProductThresholdRequest request) {
        productService.updateProductThreshold(CurrentUser.getShopId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Product threshold updated"));
    }

    @DeleteMapping("/{id}")
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productService.deleteProduct(CurrentUser.getShopId(), id);
        return ResponseEntity.ok(ApiResponse.success(200, "Product deleted"));
    }

    @GetMapping("/{id}/packaging")
    public ResponseEntity<ApiResponse<List<BatchDtos.PackagingMappingResponse>>> getPackagingMappings(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getPackagingMappings(CurrentUser.getShopId(), id)));
    }

    @PostMapping("/{id}/packaging")
    public ResponseEntity<ApiResponse<BatchDtos.PackagingMappingResponse>> addPackagingMapping(
            @PathVariable Long id,
            @Valid @RequestBody BatchDtos.PackagingMappingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.addPackagingMapping(CurrentUser.getShopId(), id, request)));
    }

    @DeleteMapping("/{id}/packaging/{mappingId}")
    public ResponseEntity<ApiResponse<Void>> removePackagingMapping(
            @PathVariable Long id, @PathVariable Long mappingId) {
        productService.removePackagingMapping(CurrentUser.getShopId(), mappingId);
        return ResponseEntity.ok(ApiResponse.success(200, "Packaging mapping removed"));
    }

    @PutMapping("/{id}/packaging/{mappingId}")
    public ResponseEntity<ApiResponse<BatchDtos.PackagingMappingResponse>> updatePackagingMapping(
            @PathVariable Long id, @PathVariable Long mappingId,
            @Valid @RequestBody BatchDtos.PackagingMappingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.updatePackagingMapping(CurrentUser.getShopId(), mappingId, request)));
    }
}
