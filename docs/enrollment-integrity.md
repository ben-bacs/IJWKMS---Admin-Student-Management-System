# Course offering and enrollment integrity model

Phase 5 separates the stable course catalog from term-specific offerings and provides transactional enrollment under `/api/v1/course-offerings` and `/api/v1/enrollments`.

## Offerings

Each offering identifies one active catalog course, academic term, and section. Capacity, weekly schedule rows, faculty assignments, and the draft/open/closed/in-progress/completed/cancelled lifecycle belong to the offering rather than the catalog course.

Schedule and instructor configuration is draft-only. Opening an offering requires a term whose enrollment status and optional timestamp window accept enrollment. An offering with active enrollments cannot be cancelled.

## Enrollment rules

Enrollment creation verifies all of the following in one transaction:

- the student is active;
- the term and offering accept enrollment;
- the course belongs to the student's active curriculum and specialization scope;
- every prerequisite course has a completed enrollment;
- the resulting active load does not exceed 24 units;
- the student has no existing enrollment for the offering; and
- the offering has remaining capacity.

The completed-enrollment prerequisite signal is intentionally policy-neutral in this phase. Phase 6 grading will connect completion to a passing final grade and its grade policy.

## Concurrency and history

Creation locks the student row first and the offering row second. The student lock serializes duplicate and unit-load decisions for one student. The offering lock serializes capacity and lifecycle decisions across students. Capacity is counted in a fresh statement after the offering lock is acquired, and database uniqueness provides a final duplicate guard.

Every create, drop, and withdrawal writes an append-only `enrollment_event` with the transition, actor, reason, timestamp, and correlation ID. The same transition writes a business audit event. No enrollment hard-delete endpoint exists.

Students may act only on enrollments tied to their linked student record. Registrars and system administrators may create and change enrollments institutionally; faculty, advisers, academic administrators, and auditors receive read-only enrollment visibility.
