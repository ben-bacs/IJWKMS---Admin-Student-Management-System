# ADR-0006: Generalized organization hierarchy and immutable curriculum versions

- Status: Accepted
- Date: 2026-09-20

## Context

The legacy application hardcodes departments, programs, specializations, semesters, and course lists. A persistent replacement must support institutional hierarchy changes without source edits while preserving the exact curriculum rules used for historical academic records.

## Decision

Represent institutions, colleges, and departments in one self-referencing `organization_unit` table with explicit type, stable code, lifecycle status, and optimistic version. Keep programs and specializations as typed curriculum concepts linked to departments.

Represent a curriculum as a stable program-owned identity with append-oriented versions. Requirements may be changed only while a version is `DRAFT`. Activation freezes its requirements. Activating a later revision retires the prior active version and closes its effective date without rewriting its content. Catalog courses and calendar records likewise use stable codes, explicit lifecycle states, database constraints, and optimistic locking.

Course prerequisites remain explicit directed edges. The database prevents self-reference and the application rejects any new edge that would produce a transitive cycle.

## Consequences

- Organization hierarchy can evolve without separate tables for every future unit type.
- Student and enrollment records can reference an exact curriculum version in later phases.
- Corrections to an activated curriculum require a new version rather than silent historical mutation.
- PostgreSQL constraints and service validation jointly protect codes, dates, relationships, lifecycle states, and prerequisite graphs.
- More records are retained instead of deleted, increasing the importance of lifecycle queries and indexes.
