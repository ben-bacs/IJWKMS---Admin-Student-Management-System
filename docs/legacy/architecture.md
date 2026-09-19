# Legacy architecture and verified baseline

## Baseline

- Repository: `ben-bacs/IJWKMS---Admin-Student-Management-System`
- Baseline commit: `254831e37389a465bc32c88ded413143ff162916`
- Preservation tag: `legacy-v1.0`
- Original entry point: `src/Main.java`
- Original UML: `finalClassDiagram(UML).drawio`

The tag is the authoritative historical snapshot. Modernization work occurs on `replatform/ijwkms-next` and must not rewrite the tag or the repository history.

## Runtime shape

The legacy system is a single-process Java console application with no declared build tool. `Main` owns the input loop, creates hardcoded academic data, authenticates users, and directly invokes domain objects. All state is process-local and is lost when the application stops.

```text
Console / Scanner
      |
    Main
      |---- AdminControl ---- LinkedList<Student>
      |                            |---- Profile
      |                            |---- List<Course>
      |                            `---- Map<courseCode, grade>
      |
      |---- StudentLogin ----- Map<studentId, plaintext password>
      `---- List<Department> - Program - Specialization - Course
```

## Classes and responsibilities

| Class | Legacy responsibility | Modern disposition |
|---|---|---|
| `Main` | Console orchestration, input, authentication, sample data, grading | Split across API, application services, configuration, and seed fixtures |
| `Person` | Shared ID and name base class | Replace with explicit identity/profile attributes where needed |
| `Student` | Student data, credentials, enrollment, grades, GWA, standing, console output | Replace with student aggregate plus enrollment, grading, and advising modules |
| `Admin` | Staff identity and department string | Replace with authenticated user plus roles and permissions |
| `AdminControl` | In-memory student CRUD, reports, sorting, profile mutation | Replace with module-specific application services and repositories |
| `StudentLogin` | Static credential map and login comparison | Remove; replace with secure identity/session subsystem |
| `Profile` | Address and contact values | Replace with access-controlled student profile |
| `Department` | Department name and programs | Replace with persistent organizational hierarchy |
| `Program` | Program name and specializations | Replace with persistent program and curriculum model |
| `Specialization` | Name and direct course list | Replace with track plus versioned curriculum requirements |
| `Course` | Catalog-like code, name, units | Retain as a persistent catalog concept |
| `Semester` | Course grouping | Replace with academic year and term entities |

## Supported legacy behavior

- Administrator login.
- Student account creation and login.
- Student creation, listing, name/GWA sorting, and deletion.
- Profile creation/update.
- Hardcoded department, program, specialization, and course selection.
- Direct course assignment during student creation.
- Grade entry, weighted GWA calculation, student grade display, reporting, and standing display.

## Architectural limits

- No persistence, transaction boundary, API, frontend, build definition, tests, CI/CD, deployment model, authorization model, audit log, or observability.
- Console output is embedded in domain and application behavior.
- Academic structure is recreated from hardcoded values at startup.
- Students directly expose mutable collections for enrollment and grades.
- Catalog courses, term offerings, enrollments, and grade records are not distinct concepts.

