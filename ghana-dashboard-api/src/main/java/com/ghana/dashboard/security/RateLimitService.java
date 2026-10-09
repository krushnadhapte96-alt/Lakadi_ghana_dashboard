package com.ghana.dashboard.security;

import com.ghana.dashboard.common.DomainException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
    private static final int LOGIN_CAPACITY = 10;

    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final int REGISTER_CAPACITY = 5;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void assertLoginAllowed(HttpServletRequest request) {
        assertAllowed("login:" + clientIp(request), LOGIN_CAPACITY, LOGIN_WINDOW);
    }

    public void assertRegisterAllowed(HttpServletRequest request) {
        assertAllowed("register:" + clientIp(request), REGISTER_CAPACITY, REGISTER_WINDOW);
    }

    private void assertAllowed(String key, int capacity, Duration period) {
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, period)))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new DomainException(429, "Too many attempts — please try again later");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}