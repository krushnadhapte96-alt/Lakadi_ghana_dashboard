package com.ghana.dashboard;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.domain.User;
import com.ghana.dashboard.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AuthFlowIntegrationTest {

    private static final String COOKIE = "ghana_refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor remoteIp(ObjectMapper mapper) {
        return request -> {
            request.setRemoteAddr("10.0." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200));
            return request;
        };
    }

    private JsonNode registerShop(String phone) throws Exception {
        String body = json(java.util.Map.of(
                "shopName", "Test Shop " + phone,
                "ownerName", "Owner " + phone,
                "phone", phone,
                "email", phone + "@example.com",
                "password", "secret1234"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-shop")
                        .with(remoteIp(objectMapper))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MvcResult login(String identifier, String password) throws Exception {
        String body = json(java.util.Map.of("identifier", identifier, "password", password));
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteIp(objectMapper))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().exists(COOKIE))
                .andReturn();
    }

    private String tokenOf(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return node.path("accessToken").asText();
    }

    private String cookieValue(MvcResult result) {
        return result.getResponse().getCookie(COOKIE).getValue();
    }

    private void addStaffUser(Long shopId, String phone) {
        User staff = new User();
        staff.setShopId(shopId);
        staff.setName("Staff " + phone);
        staff.setPhone(phone);
        staff.setEmail(null);
        staff.setPassword(passwordEncoder.encode("secret1234"));
        staff.setRole(Role.STAFF);
        staff.setActive(true);
        staff.setCreatedAt(java.time.LocalDateTime.now());
        staff.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(staff);
    }

    // ── login flow: register shop → login → /auth/me ──────────────────────────

    @Test
    void registerLoginAndMe() throws Exception {
        String phone = randomPhone();
        JsonNode registered = registerShop(phone);

        assertThat(registered.path("role").asText()).isEqualTo("OWNER");
        assertThat(registered.path("shopName").asText()).isNotBlank();

        MvcResult loginResult = login(registered.path("phone").asText(), "secret1234");
        String token = tokenOf(loginResult);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.shopId").isNumber());
    }

    @Test
    void loginFailsOnWrongPassword() throws Exception {
        String phone = randomPhone();
        registerShop(phone);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteIp(objectMapper))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("identifier", phone, "password", "wrongpass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── refresh rotation + reuse detection ────────────────────────────────────

    @Test
    void refreshRotatesAndOldTokenReuseRevokesFamily() throws Exception {
        String phone = randomPhone();
        registerShop(phone);
        MvcResult loginResult = login(phone, "secret1234");
        String oldRefresh = cookieValue(loginResult);

        // First rotation succeeds and issues a new cookie.
        MvcResult firstRefresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, oldRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().exists(COOKIE))
                .andReturn();
        String newRefresh = cookieValue(firstRefresh);
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // Reusing the old (now-rotated) token is theft → 401 and the family dies.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // The legitimate successor is now dead too (family revoked).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, newRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRejectsUnknownToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, "not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }

    // ── cross-shop isolation ──────────────────────────────────────────────────

    @Test
    void shopAUsersDoNotLeakIntoShopB() throws Exception {
        JsonNode shopA = registerShop(randomPhone());
        JsonNode shopB = registerShop(randomPhone());

        MvcResult aLogin = login(shopA.path("phone").asText(), "secret1234");
        String aToken = tokenOf(aLogin);

        // A can only ever see its own users (its OWNER + the STAFF we add).
        addStaffUser(shopA.path("shopId").asLong(), randomPhone());

        mockMvc.perform(get("/api/v1/auth/users")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == " + shopA.path("id").asLong() + ")]").isNotEmpty());

        // B's users must never be visible to A.
        String usersJson = mockMvc.perform(get("/api/v1/auth/users")
                        .header("Authorization", "Bearer " + aToken))
                .andReturn().getResponse().getContentAsString();
        JsonNode users = objectMapper.readTree(usersJson).path("data");
        for (JsonNode user : users) {
            assertThat(user.path("id").asLong()).isNotEqualTo(shopB.path("id").asLong());
            assertThat(user.path("phone").asText()).isNotEqualTo(shopB.path("phone").asText());
        }
    }

    // ── role guard: STAFF blocked from owner-only API ─────────────────────────

    @Test
    void staffIsForbiddenFromOwnerOnlyEndpoint() throws Exception {
        JsonNode owner = registerShop(randomPhone());
        String staffPhone = randomPhone();
        addStaffUser(owner.path("shopId").asLong(), staffPhone);

        MvcResult staffLogin = login(staffPhone, "secret1234");
        String staffToken = tokenOf(staffLogin);

        mockMvc.perform(get("/api/v1/auth/users")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403));
    }

    // ── logout clears session ─────────────────────────────────────────────────

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String phone = randomPhone();
        registerShop(phone);
        MvcResult loginResult = login(phone, "secret1234");
        String refresh = cookieValue(loginResult);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(COOKIE, refresh)))
                .andExpect(status().isOk());

        // The refresh token is revoked — reuse after logout fails.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, refresh)))
                .andExpect(status().isUnauthorized());
    }

    // ── change password ───────────────────────────────────────────────────────

    @Test
    void changePasswordInvalidatesOldPassword() throws Exception {
        String phone = randomPhone();
        registerShop(phone);
        String token = tokenOf(login(phone, "secret1234"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of(
                                "currentPassword", "secret1234",
                                "newPassword", "newsecret99"))))
                .andExpect(status().isOk());

        // Old password no longer works.
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteIp(objectMapper))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("identifier", phone, "password", "secret1234"))))
                .andExpect(status().isUnauthorized());
    }

    private static final java.util.concurrent.atomic.AtomicLong PHONE_SEQ = new java.util.concurrent.atomic.AtomicLong(100_000_000L);

    private String randomPhone() {
        long base = 9_000_000_000L + PHONE_SEQ.incrementAndGet() + (long) (Math.random() * 1_000_000L);
        return String.valueOf(base);
    }
}