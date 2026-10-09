package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.common.JdbcSupport;
import com.ghana.dashboard.domain.Role;
import com.ghana.dashboard.dto.SettingsDtos;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Staff administration. The owner is the only role that can reach this, and every
 * method is scoped to {@code shopId} so one shop can never touch another's staff.
 *
 * <p>New accounts are created active with a password the owner sets and shares out of
 * band. There is no invitation mail flow yet, so this is deliberately the simplest
 * thing that works rather than a half-built one.
 */
@Service
public class StaffService {

    private static final String SELECT = """
            SELECT id, name, phone, email, role, active, last_login_at, created_at
            FROM users WHERE shop_id = :shop AND deleted_at IS NULL""";

    private final JdbcSupport db;
    private final PasswordEncoder passwordEncoder;

    public StaffService(JdbcSupport db, PasswordEncoder passwordEncoder) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<SettingsDtos.StaffResponse> list(Long shopId) {
        return db.jdbc().query(SELECT + " ORDER BY active DESC, role ASC, name ASC",
                Map.of("shop", shopId), (rs, i) -> new SettingsDtos.StaffResponse(
                        rs.getLong("id"), rs.getString("name"), rs.getString("phone"), rs.getString("email"),
                        rs.getString("role"), rs.getBoolean("active"),
                        JdbcSupport.dateTime(rs, "last_login_at"), JdbcSupport.dateTime(rs, "created_at")));
    }

    @Transactional
    public SettingsDtos.StaffResponse create(Long shopId, SettingsDtos.StaffCreateRequest request) {
        String phone = request.phone().trim();
        String email = blankToNull(request.email());
        // Phone and email are globally unique across shops, so the clash has to be
        // reported as "already in use" rather than leaking which shop holds it.
        if (takenByAnotherUser(phone, email, null)) {
            throw new DomainException(409, "That phone number or email is already used by another account");
        }
        LocalDateTime now = LocalDateTime.now();
        long id;
        try {
            id = db.insert("users", new java.util.LinkedHashMap<>(Map.of(
                    "shop_id", shopId,
                    "name", request.name().trim(),
                    "phone", phone,
                    "email", email == null ? "" : email,
                    "password", passwordEncoder.encode(request.password()),
                    "role", Role.STAFF.name(),
                    "active", 1,
                    "created_at", Timestamp.valueOf(now),
                    "updated_at", Timestamp.valueOf(now))));
        } catch (RuntimeException e) {
            throw new DomainException(409, "That phone number or email is already registered");
        }
        return find(shopId, id);
    }

    @Transactional
    public SettingsDtos.StaffResponse update(Long shopId, Long id, SettingsDtos.StaffUpdateRequest request) {
        require(shopId, id);
        String phone = request.phone().trim();
        String email = blankToNull(request.email());
        if (takenByAnotherUser(phone, email, id)) {
            throw new DomainException(409, "That phone number or email is already used by another account");
        }
        db.jdbc().update("""
                UPDATE users SET name = :name, phone = :phone, email = :email, updated_at = :now
                WHERE id = :id AND shop_id = :shop AND deleted_at IS NULL""", new MapSqlParameterSource()
                .addValue("name", request.name().trim())
                .addValue("phone", phone)
                .addValue("email", email == null ? "" : email)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()))
                .addValue("id", id).addValue("shop", shopId));
        return find(shopId, id);
    }

    /**
     * Deactivates rather than deletes so the person's name stays attached to the sales,
     * payments and khata rows they created, which must keep their attribution.
     */
    @Transactional
    public SettingsDtos.StaffResponse setActive(Long shopId, Long id, boolean active) {
        SettingsDtos.StaffResponse current = require(shopId, id);
        if (!active && Role.OWNER.name().equals(current.role())) {
            Long owners = db.jdbc().queryForObject("""
                    SELECT COUNT(*) FROM users
                    WHERE shop_id = :shop AND role = 'OWNER' AND active = TRUE AND deleted_at IS NULL""",
                    Map.of("shop", shopId), Long.class);
            if (owners != null && owners <= 1) {
                throw new DomainException(400, "The shop must keep at least one active owner");
            }
        }
        db.jdbc().update("""
                UPDATE users SET active = :active, updated_at = :now
                WHERE id = :id AND shop_id = :shop AND deleted_at IS NULL""", new MapSqlParameterSource()
                .addValue("active", active ? 1 : 0)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()))
                .addValue("id", id).addValue("shop", shopId));

        if (!active) {
            // A deactivated account must not be able to keep using an access token that
            // was minted while it was still active.
            db.jdbc().update("UPDATE refresh_tokens SET revoked_at = :now WHERE user_id = :id AND revoked_at IS NULL",
                    Map.of("now", Timestamp.valueOf(LocalDateTime.now()), "id", id));
        }
        return find(shopId, id);
    }

    @Transactional
    public SettingsDtos.StaffResponse resetPassword(Long shopId, Long id, String password) {
        require(shopId, id);
        db.jdbc().update("""
                UPDATE users SET password = :password, updated_at = :now
                WHERE id = :id AND shop_id = :shop AND deleted_at IS NULL""", new MapSqlParameterSource()
                .addValue("password", passwordEncoder.encode(password))
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()))
                .addValue("id", id).addValue("shop", shopId));
        // Force re-authentication everywhere the old password was still in use.
        db.jdbc().update("UPDATE refresh_tokens SET revoked_at = :now WHERE user_id = :id AND revoked_at IS NULL",
                Map.of("now", Timestamp.valueOf(LocalDateTime.now()), "id", id));
        return find(shopId, id);
    }

    private SettingsDtos.StaffResponse find(Long shopId, Long id) {
        return db.jdbc().query(SELECT + " AND id = :id", Map.of("shop", shopId, "id", id), (rs, i) ->
                new SettingsDtos.StaffResponse(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                        rs.getString("email"), rs.getString("role"), rs.getBoolean("active"),
                        JdbcSupport.dateTime(rs, "last_login_at"), JdbcSupport.dateTime(rs, "created_at")))
                .stream().findFirst()
                .orElseThrow(() -> new DomainException(404, "Staff member not found"));
    }

    private SettingsDtos.StaffResponse require(Long shopId, Long id) {
        return find(shopId, id);
    }

    /**
     * True when the value is already held by a different user in any shop. Phone and
     * email are globally unique, so the clash cannot be scoped to one shop - the
     * message deliberately says "another account" rather than naming the holder.
     *
     * @param excludeUserId the user being edited, whose own current values are not a clash
     */
    private boolean takenByAnotherUser(String phone, String email, Long excludeUserId) {
        if (phone != null) {
            // A typed null keeps `id <> :ex` valid SQL while still allowing "no exclusion".
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("v", phone)
                    .addValue("ex", excludeUserId, java.sql.Types.BIGINT);
            Long count = db.jdbc().queryForObject("""
                    SELECT COUNT(*) FROM users
                    WHERE phone = :v AND deleted_at IS NULL AND (:ex IS NULL OR id <> :ex)""", params, Long.class);
            if (count != null && count > 0) {
                return true;
            }
        }
        if (email != null) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("v", email)
                    .addValue("ex", excludeUserId, java.sql.Types.BIGINT);
            Long count = db.jdbc().queryForObject("""
                    SELECT COUNT(*) FROM users
                    WHERE email = :v AND email <> '' AND deleted_at IS NULL
                      AND (:ex IS NULL OR id <> :ex)""", params, Long.class);
            return count != null && count > 0;
        }
        return false;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}