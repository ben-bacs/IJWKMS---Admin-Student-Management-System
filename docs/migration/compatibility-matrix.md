# Legacy compatibility matrix

| Legacy capability | Modern equivalent | Treatment | Target phase |
|---|---|---|---|
| Administrator login | Staff identity, secure authentication, roles, permissions, sessions | Replace | 2 |
| Student login | Linked student user account and secure session | Replace | 2 |
| Add student | Transactional student application service and API | Migrate | 4 |
| View students | Permission-scoped, paginated search API and admin UI | Enhance | 4, 9 |
| Sort by name | Allow-listed API sorting | Migrate | 4 |
| Sort by GWA | Authorized reporting/query projection | Redesign | 6, 10 |
| Add/update profile | Access-controlled student profile workflow | Enhance | 4 |
| Delete student | Status transition and retention-aware exceptional deletion | Redesign | 4 |
| Choose department/program/specialization | Persistent organization, program, curriculum, and track assignment | Redesign | 3, 4 |
| Automatic course list assignment | Curriculum requirements plus explicit offering enrollment | Redesign | 3, 5 |
| Update grades | Faculty-scoped grade submission and revision workflow | Redesign | 6 |
| Calculate GWA | Policy-aware weighted term and cumulative GWA | Enhance | 6 |
| View grades | Student-scoped grade and academic-history APIs/UI | Enhance | 6, 8 |
| Generate student report | Permission-scoped PDF/CSV academic reports | Enhance | 10 |
| Evaluate academic standing | Versioned, deterministic, explainable rules and alerts | Replace | 7 |
| Hardcoded sample data | Development-only seed fixtures with stable codes | Replace | 1, 3 |

Intentional differences are security and lifecycle corrections, not compatibility regressions. The modern system will not preserve plaintext passwords, hardcoded administrator access, constructor side effects, destructive ordinary deletion, ambiguous grade state, or unaudited final-grade replacement.

