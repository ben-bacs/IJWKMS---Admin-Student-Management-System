# ADR-0003: Enforce identity and authorization at the server boundary

- Status: Accepted
- Date: 2026-09-20

## Context

The legacy implementation uses hardcoded and plaintext credentials and has no authorization model. IJWKMS handles sensitive personal and academic information and high-impact actions such as enrollment and grading.

## Decision

Use Spring Security with modern password hashing, revocable server-recognized sessions, independent permissions, deny-by-default endpoint policies, and object-level authorization. Initial roles group permissions but are not the sole authorization primitive. Security-sensitive actions emit append-oriented audit events.

The concrete browser credential transport and CSRF configuration will be finalized with the identity implementation and documented in a superseding or supporting ADR if needed.

## Consequences

- UI visibility never substitutes for server enforcement.
- Permission and object-scope regression tests are mandatory.
- Authentication secrets and production credentials must come from the runtime environment or a secret manager.

