# Grading, revision history, and GWA

Phase 6 makes grade state explicit and confines every mutation to an authorized, transactional workflow.

## Grade state and policy

Every enrollment receives a grade record in `NOT_GRADED` state with a null numeric value. `DRAFT` and `FINAL` require a numeric value and an effective grade policy. `INCOMPLETE` and `WITHDRAWN` have no numeric value. A numeric zero is therefore never used as a sentinel for missing work; it is accepted only if the selected policy explicitly includes zero in its range.

Grade policies define a code, range, passing threshold, effective dates, and whether their final grades contribute to GWA. Policies are added as effective-dated records rather than silently changing the interpretation of historical grades.

## Authorization and lifecycle

- Faculty with `grade.submit` can submit grades only for offerings to which they are assigned.
- A separately privileged actor with `grade.revise` can use the same submission boundary when administrative intervention is required.
- Students can read only their own grade records and GWA.
- Assigned faculty can read their offering roster; registrar, academic administration, advising, audit, and system-administration roles receive their explicitly granted read scope.
- Ordinary submission rejects any attempt to overwrite a `FINAL` record.

Final-grade corrections use a dedicated revision transaction. It locks the grade record, persists the before/after value and status with reason, requester, approver, and timestamp, updates the current projection using optimistic locking, and writes an audit event. If any step fails, the transaction rolls back.

## GWA

Term and cumulative GWA use only `FINAL` records whose effective policy has `include_in_gwa = true`:

```text
GWA = sum(numeric grade × course units) / sum(course units)
```

The result is rounded half-up to two decimal places. When no grade is eligible, the API returns `weightedGwa: null`, zero total units, and a zero eligible-grade count. This distinguishes the absence of a GWA from the numeric grade zero.

PostgreSQL integration coverage verifies faculty scope, absence of unauthorized side effects, policy range validation, final-grade protection, immutable revision history, student self-scope, policy exclusion, and weighted GWA. Unit tests pin the arithmetic and empty-result behavior.
