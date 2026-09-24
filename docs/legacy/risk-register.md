# Legacy risk register

The findings below were verified against the source at `legacy-v1.0` rather than inferred from the modernization plan.

| ID | Severity | Verified evidence | Impact | Required treatment |
|---|---|---|---|---|
| LEG-001 | Critical | `Main.adminLogin()` compares input to `admin` / `password123` constants | Anyone with source access has administrator credentials | Remove the legacy path from the primary runtime; use hashed credentials and managed configuration |
| LEG-002 | Critical | `StudentLogin.studentAccounts` stores raw passwords and compares them with `String.equals` | Credential disclosure and no secure lifecycle | Replace with a password encoder and secure sessions; never migrate plaintext values |
| LEG-003 | High | `Student` constructor calls `StudentLogin.addStudentAccount` | Entity construction mutates global authentication state | Make account creation an explicit transactional application workflow |
| LEG-004 | High | `Main.addStudent()` constructs `Student` before `AdminControl.addStudent()` checks duplicates | A rejected duplicate can overwrite an existing login password | Enforce uniqueness before credential persistence and at the database layer |
| LEG-005 | High | `AdminControl.deleteStudent()` removes only from `studentList` | Deleted students retain valid credentials in `StudentLogin` | Couple lifecycle changes to session/account revocation in one transaction |
| LEG-006 | High | `Student.evaluateAcademicStanding()` treats every nonzero valid GWA as good because valid GWA is constrained to `0.0..5.0` | The at-risk branch is unreachable | Implement versioned deterministic standing rules with boundary tests |
| LEG-007 | Medium | `gwa` initializes to `0.0`, while grade entry accepts `0.0` and standing treats GWA zero as “no grades” | Missing and real numeric states are ambiguous | Use explicit grade/standing status and nullable values where appropriate |
| LEG-008 | High | Students, credentials, grades, and structure live only in static/in-memory collections | All operational state is lost on restart | Persist through PostgreSQL with Flyway-managed constraints |
| LEG-009 | High | No roles, permissions, object-level checks, or audit events exist | Unauthorized academic or personal-data access cannot be prevented or reconstructed | Add deny-by-default server authorization and append-oriented audit events |
| LEG-010 | Medium | `Student`, `AdminControl`, and `Main` print directly to `System.out` | Domain behavior is coupled to console presentation and is difficult to test | Return domain/application results through explicit ports and API DTOs |
| LEG-011 | High | `getEnrolledCourses()` and `getCourseGrades()` return mutable collections | Callers bypass invariants and history | Encapsulate mutations in transactional application/domain services |
| LEG-012 | Medium | `initializeSampleData()` hardcodes the academic catalog and structure | Academic configuration requires a code change and is not versioned | Move demo data to development-only seed fixtures using stable codes |
| LEG-013 | High | No build definition or automated tests are present | Behavior and security regressions cannot be detected reliably | Add reproducible builds and layered automated tests |
| LEG-014 | Medium | Physical deletion removes the in-memory student record | Academic history has no retention or lifecycle integrity | Prefer explicit student status transitions and controlled exceptional deletion |

## Risk acceptance policy

Critical and high findings cannot be accepted for the modern primary runtime without an explicit, documented mitigation. The tagged CLI remains intentionally historical and must not be exposed as a production service.

