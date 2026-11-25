package com.example.backend.user;

import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.auth.RoleMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private static final String PROFILE_SELECT = """
            SELECT id, firebase_uid, name, email, phone, address_line, avatar_url, role, status
            FROM users WHERE firebase_uid = ? AND deleted_at IS NULL
            """;

    private final JdbcTemplate jdbc;

    public UserProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserProfileDto get(String firebaseUid) {
        return findByUid(firebaseUid).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Chưa có hồ sơ MySQL. Hãy gọi POST /users/me sau đăng nhập."));
    }

    @Transactional
    public UserProfileDto synchronize(FirebasePrincipal principal, UserProfileRequest request) {
        UserProfileRequest input = request == null ? new UserProfileRequest(null, null, null,
                null, null, null, null, null) : request;
        String uid = principal.firebaseUid();
        var existing = findByUid(uid);

        if (existing.isEmpty() && principal.emailVerified() && hasText(principal.email())) {
            List<Long> legacy = jdbc.query(
                    "SELECT id FROM users WHERE email = ? AND deleted_at IS NULL LIMIT 1",
                    (rs, row) -> rs.getLong("id"), principal.email().trim());
            if (!legacy.isEmpty()) {
                jdbc.update("UPDATE users SET firebase_uid = ? WHERE id = ? AND deleted_at IS NULL",
                        uid, legacy.getFirst());
                existing = findByUid(uid);
            }
        }

        String name = firstNonNull(input.fullName(), input.name());
        String email = principal.email() != null ? principal.email().trim()
                : trimToEmpty(input.email());
        String phone = firstNonNull(input.phoneNumber(), input.phone());

        if (existing.isEmpty()) {
            String databaseRole = RoleMapper.selfRegistrationRole(input.role());
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO users(firebase_uid, name, email, phone, role, avatar_url)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, uid);
                statement.setString(2, trimToEmpty(name));
                statement.setString(3, emptyToNull(email));
                statement.setString(4, emptyToNull(trimToEmpty(phone)));
                statement.setString(5, databaseRole);
                statement.setString(6, input.avatarUrl());
                return statement;
            }, keyHolder);
        } else {
            jdbc.update("""
                    UPDATE users SET
                      name = COALESCE(NULLIF(?, ''), name),
                      email = COALESCE(NULLIF(?, ''), email),
                      phone = COALESCE(NULLIF(?, ''), phone),
                      address_line = COALESCE(?, address_line),
                      avatar_url = COALESCE(?, avatar_url),
                      last_login_at = CURRENT_TIMESTAMP
                    WHERE firebase_uid = ?
                    """, trimToEmpty(name), email, trimToEmpty(phone), input.address(),
                    input.avatarUrl(), uid);
        }
        return findByUid(uid).orElseThrow(() ->
                new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể đồng bộ hồ sơ"));
    }

    private java.util.Optional<UserProfileDto> findByUid(String uid) {
        List<UserProfileDto> rows = jdbc.query(PROFILE_SELECT, (rs, row) -> {
            String databaseRole = rs.getString("role");
            String status = rs.getString("status");
            String name = nullToEmpty(rs.getString("name"));
            String phone = nullToEmpty(rs.getString("phone"));
            return new UserProfileDto(
                    Long.toString(rs.getLong("id")), rs.getString("firebase_uid"), name, name,
                    nullToEmpty(rs.getString("email")), phone, phone,
                    nullToEmpty(rs.getString("address_line")), nullToEmpty(rs.getString("avatar_url")),
                    RoleMapper.toAndroidRole(databaseRole), status.toUpperCase(Locale.ROOT),
                    "active".equals(status));
        }, uid);
        return rows.stream().findFirst();
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String firstNonNull(String first, String second) { return first != null ? first : second; }
    private static String trimToEmpty(String value) { return value == null ? "" : value.trim(); }
    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
