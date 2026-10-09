package com.ghana.dashboard.security;

import com.ghana.dashboard.domain.Role;

public record AuthPrincipal(Long userId, Long shopId, Role role) {
}