package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.InventoryDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RoleGuard;
import com.ghana.dashboard.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/oil-types")
public class OilTypeController {

    private final ProductService productService;

    public OilTypeController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Defaults to active oil types only, which is what pickers want. Settings passes
     * {@code includeInactive=true} so deactivated rows stay visible and reversible.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryDtos.OilTypeResponse>>> getAll(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.ok(includeInactive
                ? productService.getAllOilTypesIncludingInactive()
                : productService.getAllOilTypes()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryDtos.OilTypeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getOilType(id)));
    }

    @PostMapping
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<InventoryDtos.OilTypeResponse>> create(
            @Valid @RequestBody InventoryDtos.OilTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(productService.createOilType(request)));
    }

    @PutMapping("/{id}")
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<InventoryDtos.OilTypeResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody InventoryDtos.OilTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateOilType(id, request)));
    }

    @DeleteMapping("/{id}")
    @RoleGuard(requiredRole = "OWNER")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productService.deleteOilType(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Oil type deleted"));
    }
}
