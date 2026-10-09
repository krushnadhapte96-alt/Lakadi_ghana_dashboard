package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.BeejDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.SeedTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seed-types")
public class SeedTypeController {

    private final SeedTypeService seedTypeService;

    public SeedTypeController(SeedTypeService seedTypeService) {
        this.seedTypeService = seedTypeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BeejDtos.SeedTypeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(seedTypeService.getAllSeedTypes()));
    }

    @GetMapping("/{id}/rate-history")
    public ResponseEntity<ApiResponse<List<BeejDtos.RateHistoryPoint>>> rateHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(seedTypeService.getRateHistory(CurrentUser.getShopId(), id)));
    }
}
