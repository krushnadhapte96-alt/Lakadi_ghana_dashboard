package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.DashboardDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The home dashboard. One request fills the whole landing screen — greeting figures, quick stats,
 * alerts and both charts — so a shop on a slow connection is not waiting on a dozen calls.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardDtos.Summary>> summary() {
        DashboardDtos.Summary summary = dashboardService.summary(
                CurrentUser.getShopId(), CurrentUser.getRole());
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}