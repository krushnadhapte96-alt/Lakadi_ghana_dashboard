package com.ghana.dashboard.service;

import com.ghana.dashboard.config.CacheConfig;
import com.ghana.dashboard.domain.Role;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Drops a shop's cached dashboard when the numbers behind it move.
 *
 * <p>The 60s TTL in {@link CacheConfig} is the backstop; this is the fast path. A sale, an order or a
 * bottled batch changes today's takings, the low-stock list or the pending count immediately, and a
 * shopkeeper who has just rung something up expects the tile to agree. Called from the write methods
 * of {@code SaleService}, {@code OrderService} and {@code BatchService} — the modules the dashboard
 * counts from.
 */
@Component
public class DashboardCache {

    private final CacheManager cacheManager;

    public DashboardCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Evicts both role variants of a shop's summary. Not annotated with {@code @CacheEvict} on purpose:
     * a write is far more frequent than a read here, and clearing one shop's two entries is cheaper
     * and more obvious than annotating a dozen mutators.
     */
    public void evict(Long shopId) {
        if (shopId == null) {
            return;
        }
        Cache cache = cacheManager.getCache(CacheConfig.SUMMARY_CACHE);
        if (cache == null) {
            return;
        }
        cache.evict(keyFor(shopId, Role.OWNER));
        cache.evict(keyFor(shopId, Role.STAFF));
    }

    /** Must match the SpEL key on {@code DashboardService#summary}. */
    static Object keyFor(Long shopId, Role role) {
        return shopId + ":" + role;
    }
}