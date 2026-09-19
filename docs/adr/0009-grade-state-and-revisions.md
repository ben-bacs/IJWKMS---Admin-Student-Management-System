# ADR-0009: Explicit grade state and immutable final-grade revisions

## Status

Accepted.

## Decision

Store grade state independently from the nullable numeric value. Initialize every enrollment as `NOT_GRADED`, permit assigned faculty to move non-final records through the authorized submission workflow, and require a dedicated transactional revision workflow for every change to a final grade.

Revision rows are append-only and retain the before/after value and status, reason, requester, approver, and occurrence time. The current grade record remains an optimistic-locking projection for efficient reads. Audit events record policy creation, submission, and revision.

Calculate GWA from policy-eligible `FINAL` records only, weighted by immutable course units and rounded half-up to two decimal places. Return no numeric GWA when no eligible grades exist.

## Consequences

Missing grades cannot be confused with zero, unauthorized faculty cannot grade unrelated offerings, and final-grade corrections are reconstructable. Effective-dated policies preserve the meaning of historical grades. Callers must handle an absent GWA explicitly and must use revision endpoints after finalization.
