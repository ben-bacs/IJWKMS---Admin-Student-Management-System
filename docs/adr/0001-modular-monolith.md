# ADR-0001: Use a modular monolith

- Status: Accepted
- Date: 2026-09-20

## Context

IJWKMS Next spans identity, students, academics, curriculum, enrollment, grading, advising, reporting, audit, and integrations. These boundaries matter, but the current team and deployment do not justify distributed-system overhead.

## Decision

Build one deployable Spring Boot backend organized into explicit business modules. Each module owns its domain, application services, API, and infrastructure adapters. Cross-module access occurs through declared application interfaces or domain events, not arbitrary repository access.

## Consequences

- Transactions can protect enrollment, grading, and audit invariants without distributed coordination.
- Development and deployment remain approachable.
- Module boundaries must be tested to prevent a disguised big ball of mud.
- A module can be extracted later only when operational evidence justifies it.

