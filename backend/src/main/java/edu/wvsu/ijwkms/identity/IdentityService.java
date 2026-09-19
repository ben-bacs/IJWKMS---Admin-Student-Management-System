package edu.wvsu.ijwkms.identity;

import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.shared.web.ApiException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final IdentityStore store;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenCodec tokenCodec;
    private final PasswordResetNotifier passwordResetNotifier;
    private final AuditService auditService;
    private final IdentitySecurityProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public IdentityService(
            IdentityStore store,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            TokenCodec tokenCodec,
            PasswordResetNotifier passwordResetNotifier,
            AuditService auditService,
            IdentitySecurityProperties properties,
            Clock clock) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenCodec = tokenCodec;
        this.passwordResetNotifier = passwordResetNotifier;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(tokenCodec.newToken());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String username, String password, String clientIp, String userAgent) {
        Instant now = Instant.now(clock);
        String normalizedUsername = normalizeUsername(username);
        String principalHash = tokenCodec.hash(normalizedUsername);
        String clientIpHash = tokenCodec.hash(clientIp);
        long failures = store.countRecentFailures(principalHash, clientIpHash, now.minus(properties.getLoginWindow()));

        if (failures >= properties.getMaxLoginAttempts()) {
            auditService.record(
                    null,
                    "AUTH_LOGIN_RATE_LIMITED",
                    "USER",
                    null,
                    AuditOutcome.DENIED,
                    Map.of("principalHash", principalHash));
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "Too many login attempts. Try again later.");
        }

        Optional<UserAccount> candidate = store.findUserByNormalizedUsername(normalizedUsername);
        String expectedHash = candidate.map(UserAccount::passwordHash).orElse(dummyPasswordHash);
        boolean validPassword = password != null && passwordEncoder.matches(password, expectedHash);
        boolean active = candidate
                .map(UserAccount::status)
                .filter(AccountStatus.ACTIVE::equals)
                .isPresent();

        if (!validPassword || !active) {
            store.recordAuthenticationAttempt(principalHash, clientIpHash, false, now);
            auditService.record(
                    candidate.map(UserAccount::id).orElse(null),
                    "AUTH_LOGIN",
                    "USER",
                    candidate.map(user -> user.id().toString()).orElse(null),
                    AuditOutcome.FAILURE,
                    Map.of("principalHash", principalHash));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Username or password is invalid.");
        }

        UserAccount user = candidate.orElseThrow();
        String rawToken = tokenCodec.newToken();
        Instant expiresAt = now.plus(properties.getSessionTtl());
        store.createSession(
                UUID.randomUUID(),
                user.id(),
                tokenCodec.hash(rawToken),
                clientIpHash,
                truncate(userAgent, 512),
                now,
                expiresAt);
        store.recordAuthenticationAttempt(principalHash, clientIpHash, true, now);
        auditService.record(user.id(), "AUTH_LOGIN", "USER", user.id().toString(), AuditOutcome.SUCCESS, Map.of());
        return new LoginResult(rawToken, expiresAt, toView(user));
    }

    @Transactional
    public Optional<UsernamePasswordAuthenticationToken> authenticateSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        return store.findActiveSession(tokenCodec.hash(rawToken), now)
                .filter(session -> session.user().status() == AccountStatus.ACTIVE)
                .map(session -> {
                    store.touchSession(session.sessionId(), now);
                    UserAccount user = session.user();
                    Set<String> permissions = store.findPermissions(user.id());
                    Set<String> roles = store.findRoles(user.id());
                    IdentityPrincipal principal =
                            new IdentityPrincipal(user.id(), user.username(), user.displayName(), roles, permissions);
                    var authorities = permissions.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
                });
    }

    @Transactional
    public void logout(String rawToken, UUID actorUserId) {
        if (rawToken != null && !rawToken.isBlank()) {
            store.revokeSession(tokenCodec.hash(rawToken), Instant.now(clock));
        }
        auditService.record(
                actorUserId,
                "AUTH_LOGOUT",
                "USER",
                actorUserId == null ? null : actorUserId.toString(),
                AuditOutcome.SUCCESS,
                Map.of());
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = requireUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            auditService.record(
                    userId, "AUTH_PASSWORD_CHANGE", "USER", userId.toString(), AuditOutcome.FAILURE, Map.of());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "The current password is invalid.");
        }
        requireStrongPassword(newPassword);
        Instant now = Instant.now(clock);
        store.updatePassword(userId, passwordEncoder.encode(newPassword), now);
        store.revokeAllSessions(userId, now);
        auditService.record(userId, "AUTH_PASSWORD_CHANGE", "USER", userId.toString(), AuditOutcome.SUCCESS, Map.of());
    }

    @Transactional
    public void requestPasswordReset(String username, String clientIp) {
        String normalizedUsername = normalizeUsername(username);
        Optional<UserAccount> candidate = store.findUserByNormalizedUsername(normalizedUsername)
                .filter(user -> user.status() == AccountStatus.ACTIVE);
        if (candidate.isEmpty()) {
            return;
        }

        UserAccount user = candidate.orElseThrow();
        String rawToken = tokenCodec.newToken();
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.getPasswordResetTtl());
        store.createPasswordResetToken(
                UUID.randomUUID(), user.id(), tokenCodec.hash(rawToken), tokenCodec.hash(clientIp), now, expiresAt);
        passwordResetNotifier.sendResetToken(user.username(), rawToken, expiresAt);
        auditService.record(
                user.id(), "AUTH_PASSWORD_RESET_REQUEST", "USER", user.id().toString(), AuditOutcome.SUCCESS, Map.of());
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        requireStrongPassword(newPassword);
        Instant now = Instant.now(clock);
        UUID userId = store.consumePasswordResetToken(tokenCodec.hash(rawToken), now)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "The reset token is invalid or expired."));
        store.updatePassword(userId, passwordEncoder.encode(newPassword), now);
        store.revokeAllSessions(userId, now);
        auditService.record(userId, "AUTH_PASSWORD_RESET", "USER", userId.toString(), AuditOutcome.SUCCESS, Map.of());
    }

    @Transactional
    public UserView createUser(
            UUID actorUserId, String username, String displayName, String password, Set<String> roleCodes) {
        requireStrongPassword(password);
        String normalizedUsername = normalizeUsername(username);
        if (store.findUserByNormalizedUsername(normalizedUsername).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "The username is already in use.");
        }
        roleCodes.forEach(this::requireRole);

        Instant now = Instant.now(clock);
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                username.trim(),
                normalizedUsername,
                displayName.trim(),
                passwordEncoder.encode(password),
                AccountStatus.ACTIVE,
                now,
                0);
        store.createUser(user);
        roleCodes.forEach(role -> store.assignRole(user.id(), role, actorUserId));
        auditService.record(
                actorUserId,
                "IDENTITY_USER_CREATED",
                "USER",
                user.id().toString(),
                AuditOutcome.SUCCESS,
                Map.of("roles", roleCodes));
        return toView(user);
    }

    @Transactional
    public UserView setAccountStatus(UUID actorUserId, UUID userId, AccountStatus status) {
        UserAccount user = requireUser(userId);
        Instant now = Instant.now(clock);
        store.updateStatus(userId, status, now);
        if (status != AccountStatus.ACTIVE) {
            store.revokeAllSessions(userId, now);
        }
        auditService.record(
                actorUserId,
                "IDENTITY_STATUS_CHANGED",
                "USER",
                userId.toString(),
                AuditOutcome.SUCCESS,
                Map.of("status", status.name()));
        return toView(requireUser(userId));
    }

    @Transactional
    public UserView assignRole(UUID actorUserId, UUID userId, String roleCode) {
        requireUser(userId);
        requireRole(roleCode);
        store.assignRole(userId, roleCode, actorUserId);
        auditService.record(
                actorUserId,
                "IDENTITY_ROLE_ASSIGNED",
                "USER",
                userId.toString(),
                AuditOutcome.SUCCESS,
                Map.of("role", roleCode));
        return userView(userId);
    }

    @Transactional
    public UserView removeRole(UUID actorUserId, UUID userId, String roleCode) {
        requireUser(userId);
        requireRole(roleCode);
        store.removeRole(userId, roleCode);
        auditService.record(
                actorUserId,
                "IDENTITY_ROLE_REMOVED",
                "USER",
                userId.toString(),
                AuditOutcome.SUCCESS,
                Map.of("role", roleCode));
        return userView(userId);
    }

    @Transactional(readOnly = true)
    public UserView userView(UUID userId) {
        return toView(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public java.util.List<UserView> listUsers(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        return store.findAllUsers(safeSize, safePage * safeSize).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public void bootstrapSystemAdministrator(String username, String displayName, String password) {
        if (store.countUsers() > 0) {
            return;
        }
        UserView user = createUser(null, username, displayName, password, Set.of(SYSTEM_ADMIN));
        auditService.record(
                user.id(),
                "IDENTITY_BOOTSTRAP_ADMIN_CREATED",
                "USER",
                user.id().toString(),
                AuditOutcome.SUCCESS,
                Map.of());
    }

    private UserView toView(UserAccount user) {
        return new UserView(
                user.id(),
                user.username(),
                user.displayName(),
                user.status(),
                store.findRoles(user.id()),
                store.findPermissions(user.id()));
    }

    private UserAccount requireUser(UUID userId) {
        return store.findUserById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "The user was not found."));
    }

    private void requireRole(String roleCode) {
        if (!store.roleExists(roleCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "The role is invalid.");
        }
    }

    private void requireStrongPassword(String password) {
        if (!passwordPolicy.violations(password).isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", String.join(" ", passwordPolicy.violations(password)));
        }
    }

    static String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return Normalizer.normalize(username.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
