package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.AuthDtos;
import com.ghana.dashboard.dto.AuthDtos.AuthUserResponse;
import com.ghana.dashboard.dto.AuthDtos.TokenResponse;
import com.ghana.dashboard.dto.AuthDtos.UserSummaryResponse;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.security.RateLimitService;
import com.ghana.dashboard.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register-shop")
    public ResponseEntity<ApiResponse<AuthUserResponse>> registerShop(
            @Valid @RequestBody AuthDtos.RegisterShopRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimitService.assertRegisterAllowed(httpRequest);
        AuthUserResponse user = authService.registerShop(
                request.shopName(), request.ownerName(), request.phone(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Shop registered", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimitService.assertLoginAllowed(httpRequest);
        TokenResponse tokens = authService.login(
                request.identifier(), request.password(), remoteIp(httpRequest), httpResponse);
        return ResponseEntity.ok(ApiResponse.ok("Logged in", tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        TokenResponse tokens = authService.refresh(
                refreshCookieValue(httpRequest), remoteIp(httpRequest), httpResponse);
        return ResponseEntity.ok(ApiResponse.ok("Session refreshed", tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(refreshCookieValue(httpRequest), httpResponse);
        return ResponseEntity.ok(ApiResponse.success(200, "Logged out"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logoutAll(refreshCookieValue(httpRequest), httpResponse);
        return ResponseEntity.ok(ApiResponse.success(200, "Logged out everywhere"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(CurrentUser.getUserId())));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.getUserId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(200, "Password changed"));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> users() {
        return ResponseEntity.ok(ApiResponse.ok(authService.listShopUsers(CurrentUser.getShopId())));
    }

    private String refreshCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthService.REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String remoteIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}