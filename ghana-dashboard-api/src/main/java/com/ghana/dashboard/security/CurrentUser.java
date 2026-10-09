package com.ghana.dashboard.security;

import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.common.DomainException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new DomainException(401, "Not authenticated");
        }
        return principal;
    }

    public static Long getUserId() {
        return get().userId();
    }

    public static Long getShopId() {
        return get().shopId();
    }

    public static Role getRole() {
        return get().role();
    }
}