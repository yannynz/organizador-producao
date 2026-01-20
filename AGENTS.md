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
Contexto rápido (Organizador + DXF)
===================================

- Backend Spring Boot (`/src/main/java/git/yannynz/organizadorproducao`), Front Angular (`/organizer-front`), Nginx serve o build do front, MinIO guarda as imagens DXF.
- Imagens DXF no front vêm de `/api/dxf-analysis/order/{nr}`:
  - `DXFAnalysisService.resolveImageUrl` prioriza `imageKey` + `app.dxf.analysis.image-base-url`, depois `imageUri`, depois `imagePath` (mas `PersistLocalImageCopy=false` no worker zera o path).
  - Ajustei a busca para aceitar `NR/CL` e variações com prefixo (normaliza e tenta `NRxxxx`, `CLxxxx`), evitando 404 quando o número vem com/sem prefixo.
- `application.properties` default: `app.dxf.analysis.image-base-url` usa `APP_DXF_ANALYSIS_IMAGE_BASE_URL` (em prod: http://192.168.10.13:9000/facas-renders).
- MinIO no docker-compose: porta 9000/9091. Se quiser acessar via outro host/porta, mude `MINIO_BROWSER_REDIRECT_URL` e o `APP_DXF_ANALYSIS_IMAGE_BASE_URL`.
- Diagnóstico de 404 no front:
  1) Conferir `dxf_analysis` (`order_nr`, `image_key`, `image_upload_status`) no Postgres.
  2) Checar se `/api/dxf-analysis/order/{nr}` responde 200 com token JWT.
  3) Validar URL direto no MinIO: `curl -I http://<host>:9000/facas-renders/<image_key>`.
  4) Nomes de DXF: use `NR 123456.dxf` (sem underline) ou garanta que o número esteja em `opId` para ligar ao pedido.
- OP import:
  - Listener `op.imported` → `OpImportService.importar`.
  - Enriquecimento de cliente/endereço em `ClienteAutoEnrichmentService` usa os campos `clienteNomeOficial` e `enderecosSugeridos` do payload.
  - Pedido é criado/atualizado se já existir (`orders.nr`).
- Auth: `/api/auth/register` e `/api/auth/login` retornam JWT. Users em `users` (padrão: admin `workyann@hotmail.com`, tester `devtester@example.com`).
- Comandos úteis:
  - Subir stack: `MINIO_HOST=<ip> SERVER_HOST=<ip> APP_DXF_ANALYSIS_IMAGE_BASE_URL=http://<ip>:9000/facas-renders docker compose up -d --build`.
  - Logs MinIO: `docker logs -f minio`.
  - DB rápido: `docker exec -it postgres-container psql -U postgres -d teste01`.

Diffs locais relevantes
-----------------------
- `DXFAnalysisService`: normalização extra para `orderNr` (tenta sem/prefixo).
