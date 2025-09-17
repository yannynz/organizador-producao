# Repository Guidelines

## Project Structure & Module Organization
- Backend (Spring Boot): `src/main/java`, resources in `src/main/resources` (Flyway SQL in `db/migration`). Tests in `src/test/java`.
- Frontend (Angular): `organizer-front/` (source in `organizer-front/src`, build output in `organizer-front/dist`).
- Infra: `docker-compose.yml`, Nginx config in `nginx/organizador.conf`.

## Build, Test, and Development Commands
- Backend
  - `./mvnw spring-boot:run` — run API locally (Java 17).
  - `./mvnw test` — run JUnit 5 tests.
  - `./mvnw clean package` — build fat JAR in `target/`.
- Frontend
  - `cd organizer-front && npm install && npm start` — dev server.
  - `npm run build` — production build to `organizer-front/dist`.
- Full stack via Docker
  - `docker compose up --build` — Postgres, RabbitMQ, backend, frontend, Nginx.

## Coding Style & Naming Conventions
- Java: 4‑space indentation, no wildcard imports, package `git.yannynz.organizadorproducao.*`.
- Angular/TS: follow Angular style guide; use `camelCase` for variables, `PascalCase` for classes/components.
- Formatting: Prettier available (`npx prettier --check .` for web files). Keep line length reasonable (≈120).

## Testing Guidelines
- Backend: JUnit 5 (`./mvnw test`). Place tests under `src/test/java` mirroring package paths.
- Frontend: Karma/Jasmine (`cd organizer-front && npm test`). Name spec files `*.spec.ts` next to components/services.
- Aim for meaningful tests around controllers/services and critical flows; keep Flyway scripts deterministic.

## Commit & Pull Request Guidelines
- Commits: concise, imperative subject. Example: `feat(order): add cursor pagination`.
- If following release bumps, keep `Versão x.y.z` messages consistent.
- PRs: include summary, linked issues, steps to test, and screenshots for UI changes. Note any DB/RabbitMQ changes.

## Security & Configuration Tips
- Do not hardcode secrets. Configure via environment vars (see `docker-compose.yml` and `application.properties`).
- Flyway manages schema; add new migrations under `src/main/resources/db/migration` using `VYYYYMMDD__description.sql`.
- RabbitMQ: queues are auto‑declared; for RPC ping, a stub responder can be enabled with `app.rpc.filewatcher.stub.enabled=true` in dev.
