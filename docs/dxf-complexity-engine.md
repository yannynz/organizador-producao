# DXF Complexity Engine Integration

## Overview

The `DXFAnalysisWorker` described in the DXF Complexity Engine specification now has a dedicated counterpart in the Spring backend. Incoming results from the RabbitMQ queue `facas.analysis.result` are captured, persisted, and exposed by the API. The service also publishes new analysis requests to `facas.analysis.request`, allowing Organizador to trigger DXF scoring on demand while keeping compatibility with the deterministic rules delivered on 13/10/2025.

Key components added to the backend:

- **`DXFAnalysis` entity** (table `dxf_analysis`) stores the latest metrics, scoring, image metadata, and raw payload for each analysis run.
- **`DXFAnalysisResultListener`** consumes JSON payloads produced by the FileWatcherApp worker and hands them to `DXFAnalysisService`.
- **`DXFAnalysisService`** normalises the payload, associates it with an order (via NR/CL detection when necessary), persists the analysis, and broadcasts a summary through WebSocket (`/topic/dxf-analysis`).
- **`DXFAnalysisRequestPublisher`** sends well-formed analysis requests to the queue expected by the worker, reusing the deterministic scoring configuration already active in FileWatcherApp.
- **`DXFAnalysisController`** exposes REST endpoints to trigger an analysis, obtain the latest score for an order, or retrieve historical entries.

These additions keep the legacy watchers untouched while extending the data model to support DXF assessment results and PNG renders produced by the .NET worker.

## Database Schema

Flyway migration `V20251020__create_dxf_analysis.sql` introduces the `dxf_analysis` table. Main columns:

- `analysis_id` (unique), `order_nr`, `order_id` (FK to `orders`), `file_name`, `file_hash`
- Metric aggregates: `score`, `score_label`, `total_cut_length_mm`, `curve_count`, `intersection_count`, `min_radius_mm`
- Render info: `image_path`, `image_width`, `image_height`
- JSON payloads: `metrics_json`, `explanations_json`, `raw_payload_json`
- Timestamps for `analyzed_at`, `created_at`, and `updated_at`

Indexes exist on `analysis_id`, `order_nr`, `order_id`, and `analyzed_at` to keep queries efficient.

## RabbitMQ Integration

- **Request queue** (`app.dxf.analysis.request-queue`, default `facas.analysis.request`): receives `DXFAnalysisRequest` payloads. Messages contain `analysisId`, file metadata, optional `orderNumber`, flags (`forceReprocess`, `shadowMode`), and provenance metadata.
- **Result queue** (`app.dxf.analysis.result-queue`, default `facas.analysis.result`): carries `DXFAnalysisResult` payloads emitted by the worker. The listener accepts both camelCase and snake_case field names for metrics, tolerating future schema extensions by storing the raw JSON.

Processing metrics (Micrometer) continue to monitor the new queue through `MessageProcessingMetrics`.

## REST API

| Method | Path | Description |
| ------ | ---- | ----------- |
| `POST` | `/api/dxf-analysis/request` | Publishes a new analysis request. Body accepts `filePath` (required), optional `fileName`, `fileHash`, `orderNumber`, `forceReprocess`, `shadowMode`. Returns `202 Accepted` with generated `analysisId`. |
| `GET` | `/api/dxf-analysis/order/{orderNr}` | Retrieves the latest analysis summary for the specified order number. |
| `GET` | `/api/dxf-analysis/order/{orderNr}/history?limit=5` | Lists up to `limit` recent analyses for the order (default 5, max 25). |
| `GET` | `/api/dxf-analysis/{analysisId}` | Fetches analysis details by ID. |
| `GET` | `/api/dxf-analysis/{analysisId}/image` | Redirects to the public render URL or streams the local PNG when configurado (útil para o frontend exibir a miniatura mesmo sem storage S3). |

Response payload is a `DXFAnalysisView`:

```json
{
  "analysisId": "22b4f568-98c2-4f4c-aa88-789f2f31b924",
  "orderNr": "123456",
  "orderId": 42,
  "score": 4.0,
  "scoreLabel": "ALTO",
  "totalCutLengthMm": 2500.75,
  "curveCount": 10,
  "intersectionCount": 3,
  "minRadiusMm": 0.5,
  "cacheHit": false,
  "analyzedAt": "2025-10-13T13:20:54Z",
  "fileName": "NR123456_CLIENTE_VERDE.DXF",
  "fileHash": "sha256:...",
  "imagePath": "render/22b4f568-98c2-4f4c-aa88-789f2f31b924.png",
  "imageUrl": "https://files.example.com/render/22b4f568-98c2-4f4c-aa88-789f2f31b924.png",
  "imageWidth": 1920,
  "imageHeight": 1080,
  "metrics": { "...": "..." },
  "explanations": [ { "rule": "cut_length", "weight": 3 } ]
}
```

`imageUrl` is automatically built when a base URL is configured (see below). Otherwise the raw path is echoed.

## Configuration

`application.properties` now includes the following keys (override via environment or profile-specific configs):

```
app.dxf.analysis.request-queue=facas.analysis.request
app.dxf.analysis.result-queue=facas.analysis.result
app.dxf.analysis.websocket-topic=/topic/dxf-analysis
app.dxf.analysis.image-base-url=
app.dxf.analysis.image-local-roots[0]=/home/ynz/Documents/FileWatcherApp/artifacts/renders
app.dxf.analysis.order-number-pattern=(?i)(?:NR|CL)\s*(\d+)
```

- **websocket-topic** can be cleared to disable broadcasts.
- **image-base-url** prefixes rendered PNG paths (useful when the worker saves to a shared volume served by nginx).
- **image-local-roots** lista diretórios autorizados para leitura direta de PNGs pelo endpoint `/api/dxf-analysis/{analysisId}/image`. Caminhos relativos dos workers (ex.: `./artifacts/renders/...`) serão resolvidos em relação a cada root configurado.
- **order-number-pattern** controls how NR/CL identifiers are extracted from filenames when the worker omits the explicit `orderNumber`.

> **Compose**: o `docker-compose.yml` já injeta `APP_DXF_ANALYSIS_IMAGE_BASE_URL=http://192.168.10.13:9000/facas-renders`, apontando para o bucket MinIO padrão (`facas-renders`). Ajuste a URL conforme o host exposto para usuários finais.

### Storage (MinIO / S3)

Rendered PNGs são publicados no bucket `facas-renders`. O serviço `minio-init` cria e deixa o bucket público em ambientes de desenvolvimento.

1. **Subir MinIO e esperar o init**
   ```bash
   docker compose up -d minio minio-init
   ```
   O `minio-init` aguarda o healthcheck do serviço e repete o `mc alias set` até conseguir criar o bucket com permissão pública. Logs:
   ```bash
   docker compose logs -f minio-init
   ```
2. **Reaplicar manualmente (opcional)**
   ```bash
   docker compose run --rm minio-init
   ```
3. **Validar o bucket**
   - Console web: `http://localhost:9091` (login `minio` / `minio123`)
   - CLI:
     ```bash
     docker compose run --rm minio-init mc ls local/facas-renders
     ```
4. **Configurar o FileWatcherApp** (ex.: `FileWatcherApp/appsettings.Production.json`):
   ```json
   "DXFAnalysis": {
     "PersistLocalImageCopy": false,
     "ImageStorage": {
       "Enabled": true,
       "Provider": "s3",
       "Endpoint": "http://localhost:9000",
       "AccessKey": "minio",
       "SecretKey": "minio123",
       "Bucket": "facas-renders",
       "KeyPrefix": "renders",
       "UsePathStyle": true,
       "PublicBaseUrl": "http://localhost:9000/facas-renders"
     }
   }
   ```
   Ajuste `Endpoint`/`PublicBaseUrl` para o host real. Reinicie o worker (serviço Windows ou `dotnet run`) e reprocessar pelo menos um DXF para validar o upload.

Quando `ImageStorage.Enabled=true`, os campos a seguir são preenchidos:

| Campo | Origem |
|-------|--------|
| `imageBucket` | Nome do bucket (ex.: `facas-renders`) |
| `imageKey` | Caminho no bucket (`renders/<hash>/...`) |
| `imageUri` | URL pública se `PublicBaseUrl` foi configurada |
| `imageChecksum` / `imageSizeBytes` / `imageContentType` | Retorno do upload |
| `imageUploadStatus` | `uploaded`, `exists`, `failed`... |
| `imageUploadedAt` | Timestamp UTC fornecido pelo worker |
| `imageEtag` | ETag devolvido pelo storage |

Métricas expostas pelo backend após o processamento:

- `organizador_dxf_analysis_total{status="success"}`
- `organizador_dxf_analysis_failed_total`
- `organizador_dxf_analysis_upload_total{uploadStatus="uploaded|exists|failed|..."}`
- `organizador_dxf_image_size_bytes_sum/count/max`
- `organizador_dxf_analysis_duration_seconds_sum/count`

**Observação:** resultados anteriores (antes de habilitar o MinIO) continuam com `imagePath` apontando para `C:\FacasDXF\Renders\...`. O backend ignora esses caminhos Windows ao gerar `imageUrl`, e o frontend apresenta o caminho legado apenas como fallback textual.

### Imagem (fallback/backfill)

- Quando uma analise chega sem dados de imagem, o backend tenta reaproveitar a imagem mais recente por `fileHash` ou por `orderNr` (inclui variacoes NR/CL) e copia os metadados para o novo registro.
- `imageUploadStatus=skipped` e a presenca de `imageKey` passam a ser tratados como imagem valida no backend.

### Observabilidade

O dashboard `organizador-backend` ganhou a linha **DXF Analysis** destacando:

- DXFs processadas na última hora (`sum(increase(organizador_dxf_analysis_total[1h]))`)
- Throughput de upload por status (`sum(rate(organizador_dxf_analysis_upload_total[5m]))`)
- Tamanho médio dos renders (`organizador_dxf_image_size_bytes_*`)

Esses painéis utilizam os filtros padrão (`$application`, `$instance`) para facilitar o acompanhamento entre ambientes.

### Frontend (Angular)

- O modal de detalhes do pedido consome `GET /api/dxf-analysis/order/{nr}` e exibe o score em estrelas (incrementos de 0,5) — quando `scoreStars` não estiver presente, o componente recai automaticamente para o `score` numérico.
- A seção **Imagem da faca** dentro do modal mostra a foto renderizada direto do MinIO, destacando bucket/key, status do upload e demais metadados.
- A miniatura usa `imageUri`/`imageUrl` quando disponíveis; se nenhuma URL pública for informada, nada é exibido (o backend retorna 404 para o endpoint `/api/dxf-analysis/{analysisId}/image`).
- Histórico recente é atualizado tanto via polling quanto via WebSocket (`/topic/dxf-analysis`), garantindo atualização imediata após o worker publicar um resultado.

## Sample Messages

**Request payload**

```json
{
  "analysisId": "auto-generated",
  "filePath": "\\\\srv\\facas\\NR123456_CLIENTE.DXF",
  "fileName": "NR123456_CLIENTE.DXF",
  "fileHash": "sha256:2e4f...",
  "orderNumber": "123456",
  "forceReprocess": false,
  "shadowMode": false,
  "requestedBy": "organizador-producao",
  "requestedAt": "2025-10-13T12:15:00-03:00",
  "source": { "app": "organizador-producao", "version": "0.0.1-SNAPSHOT" }
}
```

**Result payload (typical fields parsed by the service)**

```json
{
  "analysisId": "22b4f568-98c2-4f4c-aa88-789f2f31b924",
  "analysisTimestamp": "2025-10-13T15:45:20Z",
  "cacheHit": false,
  "file": { "name": "NR123456_CLIENTE_VERDE.DXF", "hash": "sha256:..." },
  "order": { "number": "123456" },
  "metrics": {
    "totalCutLengthMm": 2500.75,
    "curveCount": 10,
    "intersectionCount": 3,
    "minRadiusMm": 0.5
  },
  "score": {
    "value": 4.0,
    "label": "ALTO",
    "explanations": [
      { "rule": "cut_length", "weight": 3, "message": "Corte > 2000 mm" }
    ]
  },
  "image": {
    "path": "render/22b4f568-98c2-4f4c-aa88-789f2f31b924.png",
    "width": 1920,
    "height": 1080
  }
}
```

Unknown fields are preserved in `raw_payload_json` for future analysis.

## Testing & Validation

Automated coverage includes:

- `DXFAnalysisServiceTest`: verifies payload mapping, order association (including filename inference), metric extraction, and WebSocket broadcast.
- `DXFAnalysisRequestPublisherTest`: asserts request payload normalisation (filename, order number, flags) and mandatory field validation.

Execute everything with:

```
./mvnw test
```

## Sample DXF & Manual Checks

A minimal DXF file (`docs/dxf-samples/simple_line.dxf`) accompanies this documentation. It represents a straight cut and a small circle. Use it to sanity-check the FileWatcherApp worker or to simulate a request/response cycle:

1. Place the sample on the shared DXF input folder watched by the worker.
2. Trigger a request via `POST /api/dxf-analysis/request`, pointing `filePath` to the sample.
3. Inspect the generated metrics and PNG path once the worker publishes the result.

### Automating the DEV workflow

Para reproduzir rapidamente o ciclo de mensagens em ambientes locais, use o script:

```bash
./scripts/dxf-replay.sh
```

O script executa (com `sudo`) os mesmos passos manuais documentados anteriormente:

1. Cria o arquivo CNC em `/home/laser` e o move para `FACASOK/`.
2. Copia todos os `.dxf` de `~/Documents` para `/home/dobras/`.

Com os arquivos posicionados, basta subir a stack (`docker compose up --build`) e acompanhar os logs do FileWatcherApp e do backend.

The Java backend logs (`DXFAnalysisResultListener`) and the `dxf_analysis` table will confirm whether the deterministic scoring pipeline is delivering data in the expected shape.

## Operational Notes

- Broadcasts use `/topic/dxf-analysis`. Consumers should subscribe to receive live updates when new analyses arrive.
- If the worker cannot render PNGs (missing Skia native deps), it should still send JSON without the `image` block; the service tolerates nulls and keeps the rest of the payload.
- `raw_payload_json` can be queried to compare versions of the worker as deterministic rules evolve.
- Remember to keep the FileWatcherApp `DXFAnalysisOptions` aligned (same queue names, cache behaviour, tolerances) to avoid drift between the .NET worker and the Spring consumer.

## Troubleshooting Log (2025-10-14)

- **Symptom:** inserting `NR119812.dxf` into `/home/nr` produced the file-system event, but `/api/dxf-analysis/order/119812` returned 404 and `dxf_analysis` stayed empty.
- **Findings:**
  1. The backend was publishing `analysisId`/`orderNumber`, but the worker ignored them because `DXFAnalysisRequest` only serialises `filePath`, `fileHash`, `opId`, `flags`, and `meta`. The worker generated a brand-new `analysisId` and never saw the order number.
  2. The worker’s result contract differs from the original spec: numeric `score`, array `explanations`, metrics names such as `totalCutLength`, `numCurves`, `minArcRadius`, and the enrichment travels via `opId`/`flags`.
  3. The backend consumer expected the PRD schema (`score.value`, `metrics.totalCutLengthMm`, etc.) and therefore discarded the order number (missing) and stored null columns whenever the snake/camel field name diverged.
- **Fixes applied:**
  1. `DXFAnalysisRequestPublisher` now writes `opId=<orderNr>` and mirrors both `orderNumber` and the generated request id inside `flags`. The worker republishes these values untouched.
  2. `DXFAnalysisService` recognises the worker’s shape (`timestampUtc`, numeric `score`, string-array `explanations`, metric aliases) and falls back to regex extraction only when both `opId` and `flags.orderNumber` are absent.
  3. Explanations are normalised to JSON arrays; score labels remain optional. The consumer accepts any of `totalCutLength|totalCutLengthMm|totalLengthMm`, `numCurves|curveCount`, `numIntersections|intersectionCount`, and `minArcRadius|minRadiusMm`.
  4. FileWatcherApp now publishes `DXFAnalysisRequest` automatically when novos `.dxf` aparecem no diretório de OPs; o host/porta do RabbitMQ passaram a vir de configuração.
  5. Tests were expanded to cover the revised contract, and Mockito was pinned to the classic ByteBuddy mock-maker (no instrumentation required in the sandbox).

## Practical Test Plan

1. **Prepare environment**
   - Start the stack: `docker compose up --build`.
   - Confirm queues `facas.analysis.request` and `facas.analysis.result` exist in RabbitMQ (`http://localhost:15672`).
   - Ensure FileWatcherApp is running and has access to the DXF source folder (e.g. `/home/nr`).

2. **Seed data**
   - If needed, create an order:  
     `curl -X POST http://localhost:8081/api/orders/create -H 'Content-Type: application/json' -d '{"nr":"119812","cliente":"Teste DXF","status":0}'`

3. **Place DXF**
   - Drop a DXF (e.g. `NR119812.dxf`) into the watched folder (`/home/nr`). Confirm FileWatcherApp logs an `FS event` entry.

4. **Request analysis**
   - Trigger the worker via backend API:  
     ```bash
     curl -X POST http://localhost:8081/api/dxf-analysis/request \
          -H 'Content-Type: application/json' \
          -d '{
                "filePath": "\\\\home\\\\nr\\\\NR119812.dxf",
                "fileName": "NR119812.dxf",
                "orderNumber": "119812",
                "forceReprocess": false
              }'
     ```
   - Response returns `analysisId` (the request token). RabbitMQ should show the message consumed from `facas.analysis.request`.

5. **Verify worker output**
   - Watch FileWatcherApp logs; a success entry prints the real `analysisId`, score, and duration.
   - In RabbitMQ, queue depth of `facas.analysis.result` decreases after publication.

6. **Validate persistence & API**
   - `curl http://localhost:8081/api/dxf-analysis/order/119812`
   - `curl http://localhost:8081/api/dxf-analysis/order/119812/history?limit=3`
   - Optional SQL check:  
     `docker exec -it postgres-container psql -U postgres -d teste01 -c "SELECT analysis_id, score, order_nr, analyzed_at FROM dxf_analysis ORDER BY analyzed_at DESC LIMIT 5;"`

7. **WebSocket broadcast**
   - Subscribe with the front-end or a simple STOMP client to `/topic/dxf-analysis` and confirm the event arrives when the worker publishes the result.

8. **Image validation**
   - If `image.path` is populated, confirm the PNG exists under the worker’s render folder and is reachable through the configured `app.dxf.analysis.image-base-url`.

Following the above sequence exercises the full deterministic pipeline and ensures both the worker and backend are in sync. If any step fails, re-run `./mvnw test` to verify unit coverage, and consult RabbitMQ / backend logs for contract mismatches.
