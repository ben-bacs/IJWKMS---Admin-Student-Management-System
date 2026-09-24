# Academic configuration model

Phase 3 replaces the legacy startup fixtures with persistent, permission-gated configuration APIs.

## Model

```text
Institution -> College -> Department -> Program -> Curriculum -> CurriculumVersion
                                      |              |             `-> Requirements
                                      `-> Track      `-> Track scope

AcademicYear -> AcademicTerm
Course -> Prerequisite Course
```

Organization, program, specialization, course, curriculum, curriculum-version, academic-year, and term identifiers are UUIDs. Human-facing codes are normalized to uppercase, constrained in PostgreSQL, and remain stable after creation.

## Lifecycle and history

- Organization units, programs, specializations, and courses begin as `DRAFT` and transition to `ACTIVE` or `INACTIVE`.
- Course, program, and specialization content can be edited only while `DRAFT`; activation prevents destructive content changes.
- Curriculum versions move from `DRAFT` to `ACTIVE` to `RETIRED`.
- Requirements can be added or removed only from draft versions.
- Activating a revision automatically retires the previous active version and closes its effective date on the day before the revision begins.
- Academic years move from `PLANNED` to `ACTIVE` to `CLOSED`.
- Terms advance through `PLANNED`, `ENROLLMENT_OPEN`, `IN_PROGRESS`, `GRADING`, and `CLOSED`.
- No ordinary configuration API physically deletes historical records.

Mutable administrative records use a numeric `version`. Update commands must send the version they read; stale writes return `409 STALE_VERSION`.

## Validation and constraints

PostgreSQL enforces unique stable codes, foreign keys, lifecycle values, unit ranges, date ordering, self-prerequisite rejection, a single active curriculum version, a single active academic year, and unique curriculum-requirement scope. Application services additionally enforce organization parent types, department ownership, specialization/program consistency, term containment and overlap, lifecycle transitions, and transitive prerequisite-cycle rejection.

List APIs return:

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0
}
```

Page sizes are validated and capped by the API contract.

## Authorization

Phase 3 adds four permissions:

- `academics.read`
- `academics.manage`
- `curriculum.read`
- `curriculum.manage`

`SYSTEM_ADMIN` and `ACADEMIC_ADMIN` receive all four. `REGISTRAR`, `FACULTY`, `ADVISER`, `STUDENT`, and `AUDITOR` receive read permissions only. Every mutation is enforced server-side and creates an audit event.

## API groups

- `/api/v1/academics/organization-units`
- `/api/v1/academics/courses`
- `/api/v1/academics/academic-years`
- `/api/v1/academics/terms`
- `/api/v1/curriculum/programs`
- `/api/v1/curriculum/specializations`
- `/api/v1/curriculum/curricula`
- `/api/v1/curriculum/versions`
- `/api/v1/curriculum/courses/{courseId}/prerequisites`

The generated OpenAPI document is the authoritative request/response reference.
