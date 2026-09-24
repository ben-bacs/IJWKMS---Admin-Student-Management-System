# ADR-0002: Use PostgreSQL with Flyway-managed migrations

- Status: Accepted
- Date: 2026-09-20

## Context

The legacy application loses all state on exit and cannot enforce cross-record academic invariants. IJWKMS Next requires durable history, transactions, constraints, auditable changes, and JSON metadata for selected audit facts.

## Decision

Use PostgreSQL as the system of record and Flyway for ordered, reviewable schema migrations. Critical invariants use both application validation and database constraints. Integration tests run against real PostgreSQL through Testcontainers.

## Consequences

- Local development and CI require a container-capable environment.
- Migrations must be forward-safe and repeatable from an empty database.
- Schema changes are code-reviewed artifacts; production startup never performs ad hoc destructive schema repair.

