# Student records and privacy model

Phase 4 introduces the persistent student aggregate behind `/api/v1/students`.

## Record boundaries

- `student` stores the institutional student number, legal and preferred names, optional user linkage, status, admission date, and cohort year.
- `student_profile` stores private address, contact, and emergency-contact data separately from ordinary academic summaries.
- `student_program` is an append-only assignment history. Changing a program closes the active row and creates a new one; it does not overwrite prior curriculum context.

Student numbers and linked user IDs are unique. A linked user must hold the `STUDENT` role. New assignments must select an active program and active curriculum version, with any specialization belonging to that program.

## Authorization and privacy

`student.read` permits paginated academic-summary search. `student.write` permits record, status, and program-assignment administration. Private profile reads and writes require `student.profile.read` and `student.profile.write` respectively.

A student account receives no institution-wide student permission. Object authorization instead permits the linked user to read only their own summary, profile, and assignment history and to update only their own profile. Faculty, advisers, academic administrators, and auditors can read academic summaries but cannot read contact or emergency details. Registrars and system administrators hold the private-profile permissions.

Profile values never appear in list or ordinary student responses. Profile audit events record changed field names, not the values.

## Lifecycle and history

Student status uses explicit transitions among applicant, active, leave, suspended, graduated, withdrawn, and inactive states. Deactivation is a status change; there is no ordinary hard-delete endpoint. Optimistic versions protect concurrent student, profile, and assignment updates.

Search supports name or student number, status, program, cohort, a safe sort allowlist, and bounded pagination.
