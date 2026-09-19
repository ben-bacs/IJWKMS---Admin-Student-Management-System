# IJWKMS Next

IJWKMS Next is a ground-up modernization of the original Java console-based student management system. The target is a secure, persistent, API-first academic operations and student-success platform while the historical application remains preserved for comparison.

## Current milestone

Phase 1 establishes the delivery foundation:

- Java 25 and Spring Boot 4 modular-monolith backend
- PostgreSQL 17 with versioned Flyway migrations
- React 19, TypeScript, Vite, and React Router web shell
- OpenAPI, health probes, safe error envelopes, and correlation IDs
- Non-root production containers and a local Compose stack
- Backend, frontend, security, and container CI gates

Functional academic modules are intentionally staged in the [development issues](https://github.com/ben-bacs/IJWKMS---Admin-Student-Management-System/issues). The legacy implementation remains under `src/` and the immutable baseline is tagged `legacy-v1.0`.

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

Start with the [documentation index](docs/README.md), [legacy architecture](docs/legacy/architecture.md), [risk register](docs/legacy/risk-register.md), and [development standards](docs/development/standards.md).

## Security posture

The foundation denies application endpoints by default. It contains no default user account, browser-stored credentials, or production secret. Authentication and authorization are tracked in Phase 2; until then, only health, info, and API documentation endpoints are public.

Do not use real student data during development. Report security concerns privately to the maintainers rather than opening a public issue containing sensitive details.
