# IJWKMS Next

IJWKMS Next is a ground-up modernization of the original Java console-based student management system. The target is a secure, persistent, API-first academic operations and student-success platform while the historical application remains preserved for comparison.

## Current milestone

Phase 7 adds explainable academic standing and auditable student-success workflows to the secure academic foundation:

- Java 25 and Spring Boot 4 modular-monolith backend
- PostgreSQL 17 with versioned Flyway migrations
- React 19, TypeScript, Vite, and React Router web shell
- OpenAPI, health probes, safe error envelopes, and correlation IDs
- Non-root production containers and a local Compose stack
- Backend, frontend, security, and container CI gates
- BCrypt password hashing and environment-only first-administrator bootstrap
- Opaque, hashed, server-revocable sessions in HttpOnly SameSite cookies
- CSRF protection, login rate limiting, account lifecycle, roles, permissions, and audit events
- Configurable institutions, colleges, departments, programs, specializations, and course catalog
- Versioned curricula with immutable activated requirements and cycle-safe prerequisites
- Configurable academic years and terms with validated dates and explicit lifecycle states
- Searchable student records with unique student-number and identity linkage constraints
- Privacy-separated contact and emergency profiles with student self-service access
- Historical program and curriculum assignments with non-destructive status transitions
- Term offerings with sections, capacity, schedules, instructors, and explicit lifecycle states
- Transactional enrollment with curriculum, prerequisite, unit-load, duplicate, and availability checks
- Concurrency-safe capacity enforcement plus append-only enrollment and audit events
- Effective-dated grade policies with explicit not-graded, draft, final, incomplete, and withdrawn states
- Faculty-scoped grade submission with protected final grades and immutable revision history
- Policy-aware term and cumulative GWA weighted deterministically by course units
- Versioned deterministic standing policies with persisted inputs and rule explanations
- Adviser assignments, privacy-scoped notes, alert lifecycles, and audited standing overrides

Remaining portal and reporting capabilities are staged in the [development issues](https://github.com/ben-bacs/IJWKMS---Admin-Student-Management-System/issues). The legacy implementation remains under `src/` and the immutable baseline is tagged `legacy-v1.0`.

## Quick start with Docker

Requirements: Docker Desktop with Compose v2.

```bash
cp .env.example .env
docker compose up --build
```

Then open:

- Web application: <http://localhost:8080>
- Backend health: <http://localhost:8081/actuator/health>
- Readiness probe: <http://localhost:8081/actuator/health/readiness>
- OpenAPI UI: <http://localhost:8081/swagger-ui.html>

The values in `.env.example` are local-development defaults only. Replace them for any shared or deployed environment; `.env` files are ignored by Git.

For a new empty database, set `BOOTSTRAP_ADMIN_USERNAME` and a strong `BOOTSTRAP_ADMIN_PASSWORD` in `.env` before the first start. The bootstrap runs only while the user table is empty. After the administrator is created, remove both values from `.env` and restart the backend. No account is created when either value is absent.

Stop the stack with `docker compose down`. Add `--volumes` only when you intentionally want to remove the local PostgreSQL data volume.

## Run without Docker

Start PostgreSQL 17 and provide `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`, then run:

```bash
cd backend
./mvnw spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

The Vite development server proxies `/api` and `/actuator` to the backend on port 8080. If both services run directly, use Vite's default port 5173 for the web app.

## Verification

```bash
cd backend
./mvnw verify

cd ../frontend
npm ci
npm run check
```

Backend integration tests use Testcontainers and run when Docker is available. Frontend `check` runs type checking, linting, tests, and a production build.

## Repository map

```text
backend/     Spring Boot application, migrations, and tests
frontend/    React application and nginx runtime configuration
docs/        Architecture, risk, migration, ADR, and development records
src/         Preserved legacy Java console application
compose.yaml Local PostgreSQL, backend, and frontend stack
```

Start with the [documentation index](docs/README.md), [academic configuration model](docs/academic-configuration.md), [student records model](docs/student-records.md), [enrollment integrity model](docs/enrollment-integrity.md), [grading and GWA model](docs/grading-and-gwa.md), [legacy architecture](docs/legacy/architecture.md), [risk register](docs/legacy/risk-register.md), and [development standards](docs/development/standards.md).

## Security posture

Application endpoints require an active server-side session and are denied by default. The browser receives only an opaque HttpOnly cookie; session and reset tokens are stored as SHA-256 hashes, passwords as BCrypt hashes, and authorization is enforced from persisted permissions on every request. CSRF, account lifecycle, session revocation, rate limiting, and sensitive identity audit events are part of the boundary. Health, info, authentication entry points, and API documentation are the only public routes.

See the [identity security model](docs/security/identity.md) for bootstrap, cookie, permission, reset, and operational details.

Do not use real student data during development. Report security concerns privately to the maintainers rather than opening a public issue containing sensitive details.
