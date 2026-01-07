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
