package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.BeejDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.SeedStockService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seed-stock")
public class SeedStockController {

    private final SeedStockService seedStockService;

    public SeedStockController(SeedStockService seedStockService) {
        this.seedStockService = seedStockService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BeejDtos.SeedStockResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(seedStockService.getStockCards(CurrentUser.getShopId())));
    }

    @PostMapping("/{seedTypeId}/adjust")
    public ResponseEntity<ApiResponse<Void>> adjust(
            @PathVariable Long seedTypeId, @Valid @RequestBody BeejDtos.SeedStockAdjustRequest request) {
        seedStockService.adjust(CurrentUser.getShopId(), seedTypeId, request, CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success(200, "Stock adjusted"));
    }
}
