# Identity security model

## Account bootstrap

IJWKMS ships with roles and permissions but no user or password. On an empty database only, an administrator can be created from `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD`, and the optional `BOOTSTRAP_ADMIN_DISPLAY_NAME`. Both required values must be present or startup fails. Remove them from the runtime environment immediately after the first successful start.

Passwords must be 12–128 characters and include uppercase, lowercase, and numeric characters. They are stored only as BCrypt hashes. Usernames are normalized with Unicode NFKC and case folding before uniqueness and lookup.

## Browser sessions and CSRF

- Successful login returns an opaque 256-bit token in the `IJWKMS_SESSION` cookie.
- The cookie is HttpOnly, SameSite=Strict, scoped to `/`, and becomes Secure when `SESSION_COOKIE_SECURE=true`.
- Only a SHA-256 digest of the token is stored.
- Sessions expire after eight hours by default and are revoked by logout, password change/reset, or account disablement.
- State-changing requests require the CSRF header advertised by `GET /api/v1/auth/csrf`.
- The frontend keeps identity state in memory and never stores credentials or session tokens in browser storage.

## Authorization

Roles are bundles of permission codes. Controllers enforce permissions with method security, while resource reads use object-scope checks that allow self-access or an explicit broader permission. Hiding a link in the UI is not an authorization control.

Seeded roles are `SYSTEM_ADMIN`, `REGISTRAR`, `ACADEMIC_ADMIN`, `FACULTY`, `ADVISER`, `STUDENT`, and `AUDITOR`. Phase-specific migrations add permissions to these roles as new modules are implemented.

## Abuse resistance and audit

Login failures are counted by hashed normalized principal plus hashed remote address over a configurable window. Responses do not reveal whether an account exists. Login success/failure, throttling, logout, password change/reset, user creation, status changes, bootstrap, and role assignment/removal create append-oriented audit events without credentials or raw tokens.

Reset requests always return the same empty accepted response. Raw reset tokens are passed only to the configured `PasswordResetNotifier`; the default adapter intentionally does not deliver or log them.
