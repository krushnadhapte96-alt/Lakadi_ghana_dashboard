package com.ghana.dashboard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterShopRequest(
            @NotBlank @Size(max = 150) String shopName,
            @NotBlank @Size(min = 2, max = 150) String ownerName,
            @NotBlank @Pattern(regexp = "^[0-9]{7,15}$") String phone,
            @Email @Size(max = 191) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 191) String identifier,
            @NotBlank String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record AuthUserResponse(
            Long id,
            Long shopId,
            String shopName,
            String name,
            String phone,
            String email,
            String role) {
    }

    public record TokenResponse(
            String accessToken,
            int expiresInSeconds,
            AuthUserResponse user) {
    }

    public record UserSummaryResponse(
            Long id,
            String name,
            String phone,
            String email,
            String role,
            boolean active,
            String lastLoginAt) {
    }
}