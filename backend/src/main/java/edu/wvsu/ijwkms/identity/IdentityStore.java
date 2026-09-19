package edu.wvsu.ijwkms.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IdentityStore {

    private final JdbcClient jdbc;

    IdentityStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long countUsers() {
        return jdbc.sql("SELECT COUNT(*) FROM app_user").query(Long.class).single();
    }

    Optional<UserAccount> findUserByNormalizedUsername(String normalizedUsername) {
        return jdbc.sql("""
                        SELECT id, username, normalized_username, display_name, password_hash,
                               status, credentials_changed_at, version
                        FROM app_user
                        WHERE normalized_username = :username
                        """)
                .param("username", normalizedUsername)
                .query(this::mapUser)
                .optional();
    }

    Optional<UserAccount> findUserById(UUID userId) {
        return jdbc.sql("""
                        SELECT id, username, normalized_username, display_name, password_hash,
                               status, credentials_changed_at, version
                        FROM app_user
                        WHERE id = :userId
                        """).param("userId", userId).query(this::mapUser).optional();
    }

    void createUser(UserAccount user) {
        jdbc.sql("""
                        INSERT INTO app_user (
                            id, username, normalized_username, display_name, password_hash,
                            status, credentials_changed_at, version
                        ) VALUES (
                            :id, :username, :normalizedUsername, :displayName, :passwordHash,
                            :status, :credentialsChangedAt, :version
                        )
                        """)
                .param("id", user.id())
                .param("username", user.username())
                .param("normalizedUsername", user.normalizedUsername())
                .param("displayName", user.displayName())
                .param("passwordHash", user.passwordHash())
                .param("status", user.status().name())
                .param("credentialsChangedAt", user.credentialsChangedAt())
                .param("version", user.version())
                .update();
    }

    void updatePassword(UUID userId, String passwordHash, Instant changedAt) {
        jdbc.sql("""
                        UPDATE app_user
                        SET password_hash = :passwordHash,
                            credentials_changed_at = :changedAt,
                            updated_at = :changedAt,
                            version = version + 1
                        WHERE id = :userId
                        """)
                .param("passwordHash", passwordHash)
                .param("changedAt", changedAt)
                .param("userId", userId)
                .update();
    }

    void updateStatus(UUID userId, AccountStatus status, Instant changedAt) {
        jdbc.sql("""
                        UPDATE app_user
                        SET status = :status, updated_at = :changedAt, version = version + 1
                        WHERE id = :userId
                        """)
                .param("status", status.name())
                .param("changedAt", changedAt)
                .param("userId", userId)
                .update();
    }

    Set<String> findPermissions(UUID userId) {
        return jdbc.sql("""
                        SELECT DISTINCT permission.code
                        FROM app_permission permission
                        JOIN app_role_permission role_permission ON role_permission.permission_id = permission.id
                        JOIN app_user_role user_role ON user_role.role_id = role_permission.role_id
                        WHERE user_role.user_id = :userId
                        ORDER BY permission.code
                        """).param("userId", userId).query(String.class).stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    Set<String> findRoles(UUID userId) {
        return jdbc.sql("""
                        SELECT role.code
                        FROM app_role role
                        JOIN app_user_role user_role ON user_role.role_id = role.id
                        WHERE user_role.user_id = :userId
                        ORDER BY role.code
                        """).param("userId", userId).query(String.class).stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean roleExists(String roleCode) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM app_role WHERE code = :roleCode)")
                .param("roleCode", roleCode)
                .query(Boolean.class)
                .single();
    }

    void assignRole(UUID userId, String roleCode, UUID assignedBy) {
        jdbc.sql("""
                        INSERT INTO app_user_role (user_id, role_id, assigned_by)
                        SELECT :userId, id, :assignedBy
                        FROM app_role
                        WHERE code = :roleCode
                        ON CONFLICT (user_id, role_id) DO NOTHING
                        """)
                .param("userId", userId)
                .param("assignedBy", assignedBy)
                .param("roleCode", roleCode)
                .update();
    }

    void removeRole(UUID userId, String roleCode) {
        jdbc.sql("""
                        DELETE FROM app_user_role
                        WHERE user_id = :userId
                          AND role_id = (SELECT id FROM app_role WHERE code = :roleCode)
                        """).param("userId", userId).param("roleCode", roleCode).update();
    }

    void createSession(
            UUID sessionId,
            UUID userId,
            String tokenHash,
            String clientIpHash,
            String userAgent,
            Instant createdAt,
            Instant expiresAt) {
        jdbc.sql("""
                        INSERT INTO user_session (
                            id, user_id, token_hash, client_ip_hash, user_agent,
                            created_at, last_seen_at, expires_at
                        ) VALUES (
                            :id, :userId, :tokenHash, :clientIpHash, :userAgent,
                            :createdAt, :createdAt, :expiresAt
                        )
                        """)
                .param("id", sessionId)
                .param("userId", userId)
                .param("tokenHash", tokenHash)
                .param("clientIpHash", clientIpHash)
                .param("userAgent", userAgent)
                .param("createdAt", createdAt)
                .param("expiresAt", expiresAt)
                .update();
    }

    Optional<AuthenticatedSession> findActiveSession(String tokenHash, Instant now) {
        return jdbc.sql("""
                        SELECT session.id AS session_id, session.expires_at,
                               app_user.id, app_user.username, app_user.normalized_username,
                               app_user.display_name, app_user.password_hash, app_user.status,
                               app_user.credentials_changed_at, app_user.version
                        FROM user_session session
                        JOIN app_user ON app_user.id = session.user_id
                        WHERE session.token_hash = :tokenHash
                          AND session.revoked_at IS NULL
                          AND session.expires_at > :now
                          AND session.created_at >= app_user.credentials_changed_at
                        """)
                .param("tokenHash", tokenHash)
                .param("now", now)
                .query((result, rowNumber) -> new AuthenticatedSession(
                        result.getObject("session_id", UUID.class),
                        mapUser(result, rowNumber),
                        result.getTimestamp("expires_at").toInstant()))
                .optional();
    }

    void touchSession(UUID sessionId, Instant now) {
        jdbc.sql("UPDATE user_session SET last_seen_at = :now WHERE id = :sessionId")
                .param("now", now)
                .param("sessionId", sessionId)
                .update();
    }

    Optional<UUID> revokeSession(String tokenHash, Instant now) {
        return jdbc.sql("""
                        UPDATE user_session
                        SET revoked_at = :now
                        WHERE token_hash = :tokenHash AND revoked_at IS NULL
                        RETURNING user_id
                        """)
                .param("now", now)
                .param("tokenHash", tokenHash)
                .query(UUID.class)
                .optional();
    }

    void revokeAllSessions(UUID userId, Instant now) {
        jdbc.sql("""
                        UPDATE user_session
                        SET revoked_at = :now
                        WHERE user_id = :userId AND revoked_at IS NULL
                        """).param("now", now).param("userId", userId).update();
    }

    long countRecentFailures(String principalHash, String clientIpHash, Instant since) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM authentication_attempt
                        WHERE principal_hash = :principalHash
                          AND client_ip_hash = :clientIpHash
                          AND succeeded = FALSE
                          AND attempted_at >= :since
                          AND attempted_at > COALESCE((
                              SELECT MAX(success.attempted_at)
                              FROM authentication_attempt success
                              WHERE success.principal_hash = :principalHash
                                AND success.client_ip_hash = :clientIpHash
                                AND success.succeeded = TRUE
                                AND success.attempted_at >= :since
                          ), :since)
                        """)
                .param("principalHash", principalHash)
                .param("clientIpHash", clientIpHash)
                .param("since", since)
                .query(Long.class)
                .single();
    }

    void recordAuthenticationAttempt(
            String principalHash, String clientIpHash, boolean succeeded, Instant attemptedAt) {
        jdbc.sql("""
                        INSERT INTO authentication_attempt (
                            principal_hash, client_ip_hash, succeeded, attempted_at
                        ) VALUES (:principalHash, :clientIpHash, :succeeded, :attemptedAt)
                        """)
                .param("principalHash", principalHash)
                .param("clientIpHash", clientIpHash)
                .param("succeeded", succeeded)
                .param("attemptedAt", attemptedAt)
                .update();
    }

    void createPasswordResetToken(
            UUID tokenId, UUID userId, String tokenHash, String requestedIpHash, Instant createdAt, Instant expiresAt) {
        jdbc.sql("""
                        UPDATE password_reset_token
                        SET consumed_at = :createdAt
                        WHERE user_id = :userId AND consumed_at IS NULL
                        """).param("createdAt", createdAt).param("userId", userId).update();
        jdbc.sql("""
                        INSERT INTO password_reset_token (
                            id, user_id, token_hash, requested_ip_hash, created_at, expires_at
                        ) VALUES (
                            :id, :userId, :tokenHash, :requestedIpHash, :createdAt, :expiresAt
                        )
                        """)
                .param("id", tokenId)
                .param("userId", userId)
                .param("tokenHash", tokenHash)
                .param("requestedIpHash", requestedIpHash)
                .param("createdAt", createdAt)
                .param("expiresAt", expiresAt)
                .update();
    }

    Optional<UUID> consumePasswordResetToken(String tokenHash, Instant now) {
        return jdbc.sql("""
                        UPDATE password_reset_token
                        SET consumed_at = :now
                        WHERE token_hash = :tokenHash
                          AND consumed_at IS NULL
                          AND expires_at > :now
                        RETURNING user_id
                        """)
                .param("now", now)
                .param("tokenHash", tokenHash)
                .query(UUID.class)
                .optional();
    }

    List<UserAccount> findAllUsers(int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, username, normalized_username, display_name, password_hash,
                               status, credentials_changed_at, version
                        FROM app_user
                        ORDER BY normalized_username
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapUser)
                .list();
    }

    private UserAccount mapUser(ResultSet result, int rowNumber) throws SQLException {
        return new UserAccount(
                result.getObject("id", UUID.class),
                result.getString("username"),
                result.getString("normalized_username"),
                result.getString("display_name"),
                result.getString("password_hash"),
                AccountStatus.valueOf(result.getString("status")),
                result.getTimestamp("credentials_changed_at").toInstant(),
                result.getLong("version"));
    }
}
