# ADR-0008: Serialized enrollment invariants and append-only events

Status: Accepted

## Context

Duplicate, unit-load, and capacity checks are unsafe when implemented as unrelated reads followed by writes. Concurrent requests can each observe availability and jointly violate the rule. Enrollment changes also need a durable explanation beyond the current status column.

## Decision

Execute enrollment mutations in database transactions. Acquire row locks in the consistent order of student then course offering. Recalculate active unit load and capacity after the relevant lock is held. Retain a database uniqueness constraint on student and offering as a final duplicate guard.

Represent each lifecycle transition with an append-only enrollment event containing before and after status, reason, actor, correlation ID, and occurrence time. Also emit the platform audit event in the same transaction.

## Consequences

Concurrent attempts cannot overbook an offering or independently push one student beyond the unit limit. Lock ordering reduces deadlock risk. Enrollment history is explainable and queryable, while mutations do more database work and must keep transactions short.
