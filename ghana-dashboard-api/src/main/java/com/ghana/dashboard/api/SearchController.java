package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.SearchDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.GlobalSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the Ctrl+K palette. Shop-scoped like everything else, and open to any role: a shop where
 * only the owner can look up a bill number is a shop where the counter clerk asks the owner.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final GlobalSearchService search;

    public SearchController(GlobalSearchService search) {
        this.search = search;
    }

    /**
     * @param q free text; blank returns no groups so the palette can show recents and shortcuts
     */
    @GetMapping
    public ApiResponse<SearchDtos.SearchResponse> search(@RequestParam(name = "q", required = false) String q) {
        return ApiResponse.ok(search.search(CurrentUser.getShopId(), q));
    }
}