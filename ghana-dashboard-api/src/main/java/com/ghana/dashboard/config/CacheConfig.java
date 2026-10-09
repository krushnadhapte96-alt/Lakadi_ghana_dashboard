package com.ghana.dashboard.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caching for the read-heavy dashboard.
 *
 * <p>One cache is defined, and it exists because the home screen is a dozen aggregates over sales,
 * stock, seed, khata and licences that every device on the shop floor re-reads every minute.
 * Entries expire after {@link #TTL_SECONDS}s and are dropped outright by {@code DashboardCache} the
 * moment a sale, order or batch changes the numbers — so a value can be at most 60s stale, and
 * usually not even that.
 *
 * <p>Spring's stock {@code ConcurrentMapCacheManager} cannot expire anything, so the TTL is applied
 * in {@link TtlCache#get}: an entry older than the window is treated as absent and recomputed.
 * Keys are {@code shopId:ROLE}, so one shop's numbers can never be served to another, nor a
 * staff request ever read the owner's cached entry.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache holding {@code DashboardService} summaries. */
    public static final String SUMMARY_CACHE = "dashboardSummary";

    /** How long a dashboard summary stays valid. */
    public static final long TTL_SECONDS = 60;

    @Bean
    public org.springframework.cache.CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(new TtlCache(SUMMARY_CACHE, TTL_SECONDS * 1000)));
        return manager;
    }

    /** A concurrent map cache where every read also checks the age of the entry. */
    static final class TtlCache extends ConcurrentMapCache {

        private final long ttlMillis;
        private final Map<Object, Long> storedAt = new ConcurrentHashMap<>();

        TtlCache(String name, long ttlMillis) {
            super(name);
            this.ttlMillis = ttlMillis;
        }

        @Override
        public ValueWrapper get(Object key) {
            ValueWrapper value = super.get(key);
            Long stored = storedAt.get(key);
            if (value == null || stored == null || System.currentTimeMillis() - stored >= ttlMillis) {
                evict(key);
                return null;
            }
            return value;
        }

        @Override
        public void put(Object key, Object value) {
            super.put(key, value);
            storedAt.put(key, System.currentTimeMillis());
        }

        @Override
        public void evict(Object key) {
            super.evict(key);
            storedAt.remove(key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            storedAt.remove(key);
            return super.evictIfPresent(key);
        }

        @Override
        public void clear() {
            super.clear();
            storedAt.clear();
        }
    }
}