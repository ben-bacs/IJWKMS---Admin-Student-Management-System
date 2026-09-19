# Development standards

## Workflow

- Never implement modernization work directly on `main`.
- Use short-lived branches from `replatform/ijwkms-next` or the current agreed integration branch.
- Do not rewrite published history or retag `legacy-v1.0`.
- Use conventional commits with a meaningful module scope.
- Link implementation work to its GitHub issue and keep pull requests reviewable.

## Architecture

- Respect modular-monolith boundaries and dependency direction.
- Keep domain behavior independent from HTTP, console, and persistence details.
- Use application services for transactions and cross-aggregate workflows.
- Represent absence and lifecycle with explicit types/statuses, never magic values.
- Preserve academic history; ordinary workflows deactivate or transition rather than hard-delete.

## Security and privacy

- Deny by default and enforce permissions plus object scope server-side.
- Never commit secrets, plaintext passwords, real student data, or `.env` files.
- Use explicit request/response DTOs to prevent mass assignment and accidental disclosure.
- Validate at trust boundaries and return safe error envelopes.
- Audit authentication, authorization administration, student-sensitive changes, enrollment, grading, standing overrides, and exports.
- Minimize personal data in storage, logs, API payloads, and browser persistence.

## Database

- Change schema only through reviewed Flyway migrations.
- Use foreign keys, uniqueness constraints, indexes, and optimistic locking where they protect invariants.
- Test migrations from an empty PostgreSQL database.
- Prefer additive, forward-safe migrations and documented operational rollback.

## Quality gates

- Add tests at the lowest useful layer and integration coverage for persistence/security boundaries.
- Do not mock away PostgreSQL invariants in integration tests.
- Run formatting, static analysis, unit tests, integration tests, frontend checks, and builds before merge.
- Never claim a check passed unless its command actually ran successfully.

