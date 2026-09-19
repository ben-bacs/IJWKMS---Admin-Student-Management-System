# ADR-0004: Use a versioned REST API as the primary application boundary

- Status: Accepted
- Date: 2026-09-20

## Context

The legacy console directly couples input, output, orchestration, and domain behavior. The new React client, reports, tests, and future integrations require a stable, documented boundary.

## Decision

Expose application capabilities under `/api/v1` using resource-oriented HTTP endpoints and an OpenAPI contract. Requests use explicit DTOs and validation. Lists use pagination and allow-listed sorting. Errors use stable machine-readable codes plus correlation IDs and never expose stack traces.

## Consequences

- Backend behavior can be tested independently from the UI.
- Contract compatibility is explicit and versioned.
- External-standard mappings remain in the integrations module rather than leaking into core entities.

