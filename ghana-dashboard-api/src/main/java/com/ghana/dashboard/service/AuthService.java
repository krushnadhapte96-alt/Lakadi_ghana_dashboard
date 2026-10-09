package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.RefreshToken;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.domain.Shop;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.dto.AuthDtos.AuthUserResponse;
import com.ghana.dashboard.dto.AuthDtos.TokenResponse;
import com.ghana.dashboard.dto.AuthDtos.UserSummaryResponse;
import com.ghana.dashboard.repository.RefreshTokenRepository;
import com.ghana.dashboard.repository.ShopRepository;
import com.ghana.dashboard.repository.UserRepository;
import com.ghana.dashboard.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    public static final String REFRESH_COOKIE = "ghana_refresh";

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Environment environment;

    public AuthService(UserRepository userRepository,
                       ShopRepository shopRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       Environment environment) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.environment = environment;
    }

    @Transactional
    public AuthUserResponse registerShop(String shopName, String ownerName, String phone,
                                         String email, String password) {
        String normalizedPhone = phone.trim();
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new DomainException(409, "A user with this phone number already exists");
        }
        if (StringUtils.hasText(email) && userRepository.existsByEmail(email.trim().toLowerCase())) {
            throw new DomainException(409, "A user with this email already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        Shop shop = new Shop();
        shop.setName(shopName.trim());
        shop.setMobile(normalizedPhone);
        shop.setCurrencyCode("INR");
        shop.setStatus("ACTIVE");
        shop.setCreatedBy("register-shop");
        shop.setCreatedAt(now);
        shop.setUpdatedAt(now);
        shopRepository.save(shop);

        User user = new User();
        user.setShopId(shop.getId());
        user.setName(ownerName.trim());
        user.setPhone(normalizedPhone);
        user.setEmail(StringUtils.hasText(email) ? email.trim().toLowerCase() : null);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.OWNER);
        user.setActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        return toAuthUser(user, shop);
    }

    @Transactional
    public TokenResponse login(String identifier, String password, String remoteIp,
                               HttpServletResponse response) {
        User user = findByIdentifier(identifier)
                .orElseThrow(() -> new DomainException(401, "Invalid phone/email or password"));
        if (!user.isActive()) {
            throw new DomainException(401, "Account is deactivated");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new DomainException(401, "Invalid phone/email or password");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return issueTokens(user, remoteIp, response);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken, String remoteIp, HttpServletResponse response) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new DomainException(401, "Missing refresh token");
        }
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new DomainException(401, "Invalid refresh token"));

        LocalDateTime now = LocalDateTime.now();
        if (stored.isRevoked()) {
            // Reuse detected — revoke the whole family so a leaked token cannot rotate further.
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            throw new DomainException(401, "Refresh token reuse detected");
        }
        if (stored.isExpired(now)) {
            throw new DomainException(401, "Refresh token expired");
        }

        User user = stored.getUser();
        if (user == null || !user.isActive()) {
            throw new DomainException(401, "Account unavailable");
        }

        // Rotate: revoke the presented token, issue its successor in the same family.
        RefreshToken next = persistRefreshToken(user, stored.getFamilyId(), remoteIp);
        stored.setRevokedAt(now);
        stored.setReplacedBy(next.getId());
        refreshTokenRepository.save(stored);

        return buildResponse(user, next.getRawValue(), response);
    }

    @Transactional
    public void logout(String rawRefreshToken, HttpServletResponse response) {
        if (StringUtils.hasText(rawRefreshToken)) {
            refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawRefreshToken))
                    .ifPresent(token -> {
                        token.setRevokedAt(LocalDateTime.now());
                        refreshTokenRepository.save(token);
                    });
        }
        clearRefreshCookie(response);
    }

    @Transactional
    public void logoutAll(String rawRefreshToken, HttpServletResponse response) {
        if (StringUtils.hasText(rawRefreshToken)) {
            refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawRefreshToken))
                    .ifPresent(token -> refreshTokenRepository.revokeAllForUser(token.getUser().getId(),
                            LocalDateTime.now()));
        }
        clearRefreshCookie(response);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new DomainException(404, "User not found"));
        return toAuthUser(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new DomainException(404, "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new DomainException(400, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listShopUsers(Long shopId) {
        return userRepository.findByShopIdAndActiveTrueOrderByIdAsc(shopId).stream()
                .map(user -> new UserSummaryResponse(
                        user.getId(),
                        user.getName(),
                        user.getPhone(),
                        user.getEmail(),
                        user.getRole().name(),
                        user.isActive(),
                        user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString()))
                .toList();
    }

    private TokenResponse issueTokens(User user, String remoteIp, HttpServletResponse response) {
        RefreshToken issue = persistRefreshToken(user, jwtService.newFamilyId(), remoteIp);
        return buildResponse(user, issue.getRawValue(), response);
    }

    private TokenResponse buildResponse(User user, String rawRefreshValue, HttpServletResponse response) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getShopId(), user.getRole());
        setRefreshCookie(response, rawRefreshValue);
        return new TokenResponse(accessToken, jwtService.accessTtlSeconds(), toAuthUser(user));
    }

    private RefreshToken persistRefreshToken(User user, String familyId, String remoteIp) {
        String rawValue = jwtService.generateRefreshTokenValue();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(jwtService.hashRefreshToken(rawValue));
        entity.setFamilyId(familyId);
        entity.setExpiresAt(LocalDateTime.now().plus(jwtService.refreshTtl()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedIp(remoteIp);
        entity.setRawValue(rawValue);
        return refreshTokenRepository.save(entity);
    }

    private void setRefreshCookie(HttpServletResponse response, String rawValue) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, rawValue);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setSecure(environment.acceptsProfiles(Profiles.of("prod")));
        cookie.setAttribute("SameSite", "Lax");
        cookie.setMaxAge((int) jwtService.refreshTtl().getSeconds());
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setSecure(environment.acceptsProfiles(Profiles.of("prod")));
        cookie.setAttribute("SameSite", "Lax");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private Optional<User> findByIdentifier(String identifier) {
        String value = identifier.trim();
        if (value.contains("@")) {
            return userRepository.findByEmail(value.toLowerCase());
        }
        return userRepository.findByPhone(value);
    }

    private AuthUserResponse toAuthUser(User user) {
        Shop shop = shopRepository.findById(user.getShopId()).orElse(null);
        return toAuthUser(user, shop);
    }

    private AuthUserResponse toAuthUser(User user, Shop shop) {
        return new AuthUserResponse(
                user.getId(),
                user.getShopId(),
                shop == null ? null : shop.getName(),
                user.getName(),
                user.getPhone(),
                user.getEmail(),
                user.getRole().name());
    }
}