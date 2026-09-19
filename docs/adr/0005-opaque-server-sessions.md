# ADR-0005: Use opaque server-side sessions for browser authentication

- Status: Accepted
- Date: 2026-09-20

## Context

The legacy application stores plaintext credentials in process memory. The web platform needs revocation, expiry, password-change invalidation, and a browser boundary that does not expose reusable credentials to JavaScript.

## Decision

Authenticate the browser with a cryptographically random opaque token in an HttpOnly, SameSite=Strict cookie. Store only its SHA-256 hash in PostgreSQL and resolve roles and permissions on every request. Protect state-changing requests with a separate CSRF token. Passwords use BCrypt with a configurable work factor. Password reset tokens are random, hashed at rest, single-use, short-lived, and passed only to a delivery adapter.

## Consequences

- Logout, password changes, account disablement, and administrative action can revoke sessions immediately.
- Browser code cannot read the session credential.
- The database is consulted for authenticated requests; later performance work may add a short, revocation-aware cache.
- HTTPS deployments must set `SESSION_COOKIE_SECURE=true`.
