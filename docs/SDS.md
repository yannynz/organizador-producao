# System Design Specification (SDS)

## 1. Arquitetura em Camadas

```
┌────────────────────────────────────────────────────────────────────────┐
│ Frontend Angular (organizer-front)                                     │
│ - Orders / Delivery / Delivered / Rubber / Advanced Filters / Modal    │
│ - RxStomp WebSockets (orders, prioridades, dxf-analysis, status)       │
│ - HTTP -> /api/** (Nginx proxy)                                        │
└──────────────┬─────────────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────┐      ┌──────────────────────────────────┐
│ Nginx (organizador.conf)    │─────▶│ Backend Spring Boot (src/)       │
│ - Serve Angular build       │      │ - REST, STOMP, RabbitMQ clients  │
│ - Proxy /api e /ws          │      │ - PostgreSQL via JPA             │
└──────────────┬──────────────┘      │ - Micrometer/Actuator            │
               │                     │ - DXFAnalysis + Order logic      │
               │                     └──────────────┬───────────────────┘
               │                                     │
               │ RabbitMQ                            │ PostgreSQL
               │                                     │
┌──────────────▼──────────────┐            ┌─────────▼─────────┐
│ FileWatcherApp (.NET 8)     │◀──────────▶│ Postgres 17       │
│ - File watchers (Laser, etc)│            │ - orders, op_import│
│ - DXFAnalysisWorker         │            │ - dxf_analysis     │
│ - RpcPing responder         │            └────────────────────┘
└──────────────┬──────────────┘
               │
          MinIO / S3
```

## 2. Componentes Principais

### 2.1 Frontend Angular
- **Tecnologia**: Angular 17 + Bootstrap + RxStomp.
- **Entradas**: REST `/api/**`, STOMP `/topic/**`.
- **Saídas**: STOMP `/app/orders/update`, HTTP updates.
- **Responsabilidade**: UI, formulários reativos, filtros locais, exibição de DXF (score/PNG).

### 2.2 Backend Spring Boot
- **Tecnologia**: Java 17, Spring Boot (Web, WebSocket, AMQP, Data JPA), Micrometer.
- **Principais Beans**:
  - Controllers (`OrderController`, `DXFAnalysisController`, `OpImportController`).
  - Services (`OrderService`, `OpImportService`, `DobrasFileService`, `DXFAnalysisService`, `DXFAnalysisRequestPublisher`, `FileWatcherService`, `DestacadorMonitorService`, `StatusWsPublisher`).
  - RabbitMQ config (`RabbitMQConfig`, `RabbitRpcConfig`, `AmqpConfig`).
  - Monitoring (`HttpRequestMetricsFilter`, `MessageProcessingMetrics`, `WebSocketMetrics`).
  - Scheduled jobs (`FileWatcherPingScheduler`, priority updater).
- **Persistência**: PostgreSQL (`orders`, `op_import`, `dxf_analysis` via Flyway). Flyway migrations em `src/main/resources/db/migration`.
- **WebSocket**: STOMP endpoint `/ws/orders` com tópicos `/topic/orders`, `/topic/prioridades`, `/topic/dxf-analysis`, `/topic/status`.

### 2.3 FileWatcherApp (.NET 8)
- **Serviços Hosted**:
  - `FileWatcherService`: FileSystemWatcher para Laser, Facas OK, Dobras, OP.
  - `DXFAnalysisWorker`: pipeline determinística (DXFPreprocessor, DXFAnalyzer, DXFImageRenderer, ComplexityScorer).
  - `RpcPingResponderService`: responde `filewatcher.rpc.ping`.
- **Config**: `appsettings.json` (RabbitMq, FileWatcher, DXFAnalysis).
- **Saídas**: RabbitMQ (notificações + análise), logs.
- **Fluxo Dobras**: `HandleDobrasFileAsync` só publica em `dobra_notifications` se `HasDobrasSavedSuffix` = true; caso contrário apenas chama `PublishAnalysisRequest`.
- **Scoring**: ajustável via `DXFAnalysisOptions.Scoring` (pesos, thresholds) e doc `../FileWatcherApp/docs/complexidade-facas.md`.

### 2.4 Infraestrutura Docker Compose
- Serviços: `postgres-container`, `rabbitmq-container`, `backend-container`, `frontend-container`, `nginx-container`, `minio`, `minio-init`, `prometheus-container`, `grafana-container`, `rabbitmq-exporter`, `postgres-exporter`, `node-exporter`, `cadvisor`.
- **Volumes**: `pgdata17`, `prometheus-data`, `grafana-data`.
- **Rede**: `organizador-producao-mynetwork`.
- **Healthchecks**: banco, rabbit, compose start-ordering.
- **Nginx**: restrições IP, cabeçalhos de segurança, cache de estáticos.

## 3. Fluxos Importantes

### 3.1 Criar Pedido via LASER
1. FileWatcher (LASER_DIR) detecta arquivo `NR123456CLIENTE_VERMELHO.CNC`.
2. Publica em `laser_notifications`.
3. Backend `FileWatcherService.handleLaserQueue`:
   - Registra evento “aguardando corte” com `DestacadorMonitorService`.
   - `processFile` parseia NR/cliente/prioridade via regex.
   - Se NR não existe, cria `Order` (status 0, dataH=now) e publica via `/topic/orders`.

### 3.2 OP Import
1. PDF depositado em OPS_DIR; watcher publica `op.imported`.
2. `OpImportedListener` → `OpImportService.importar`:
   - `PdfParser` extrai dados (cliente, materiais, destacador, modalidade, datas).
   - Atualiza `op_import`, sincroniza com pedido (emborrachada, vaiVinco, materiais).
   - Publica WebSocket `/topic/ops`.

### 3.3 DXF Analysis
1. Angular (ou backend) chama `POST /api/dxf-analysis/request` (payload `DXFAnalysisRequestDTO`).
2. `DXFAnalysisRequestPublisher` gera `analysisId`, detecta NR, publica em `facas.analysis.request`.
3. `DXFAnalysisWorker`:
   - Verifica cache (hash).
   - Gera métricas (comprimento, serrilha, curva, 3pt, materiais).
   - Renderiza PNG (Skia), calcula SHA256 e envia ao storage (MinIO).
   - Aplica `ComplexityScorer` (0–5).
   - Publica em `facas.analysis.result`.
4. Backend `DXFAnalysisResultListener`:
   - Chama `DXFAnalysisService.persistFromPayload` → persiste, associa Order, grava raw JSON.
   - Publica `/topic/dxf-analysis`.
5. Angular `OrderDetailsModal` refaz GET e atualiza UI.

### 3.4 Dobras (final)
1. FileWatcher monitora DOBRAS_DIR.
2. Ao detectar `NR 123456.m.DXF`:
   - Aguarda 8s, dedup window 2 min.
   - Publica em `dobra_notifications`.
   - Solicita análise DXF (caso habilitado).
3. Backend `DobrasFileService` extrai NR e atualiza pedido status 6 (Tirada) + `dataTirada`, broadcast `/topic/orders`.

### 3.5 Ping FileWatcher
1. `FileWatcherPingScheduler` chama `FileWatcherPingClient.pingNow` (fila `filewatcher.rpc.ping`) a cada 10 s.
2. `RpcPingResponderService` responde `{"ok":true,"instanceId":...,"ts":...,"version":...}`.
3. Backend publica `StatusEvent` via `/topic/status` e expõe health-check.

## 4. Implantação & Configuração

1. **Docker Compose**:
   ```bash
   docker compose up --build
   ```
   - Backend disponível em `http://localhost:8081`.
   - Frontend via Nginx em `http://localhost`.
   - RabbitMQ: `http://localhost:15672` (guest/guest).
   - MinIO Console: `http://localhost:9091`.
   - Prometheus: `http://localhost:9090`.
   - Grafana: `http://localhost:3000` (admin/admin123).

2. **Variáveis chave**:
   - `SPRING_DATASOURCE_*`, `SPRING_RABBITMQ_*`.
   - `APP_CORS_ALLOWED_ORIGINS`.
   - `APP_DXF_ANALYSIS_IMAGE_BASE_URL`.
   - `appsettings.json` (RabbitMq host, watch folders, scoring).

3. **Scripts úteis**:
   - `./scripts/dxf-replay.sh`: reproduz ciclo Laser → Dobras para testes.
   - `startup_organizer.sh`: bootstrap local (se existir).

## 5. Observabilidade

### 5.1 Métricas Backend
- HTTP latência/contagem/erros `organizador_http_server_*`.
- Rabbit processing `organizador_message_processing_seconds`.
- DXF metrics `organizador_dxf_*`.
- WebSocket sessions `organizador_websocket_active_sessions`.

### 5.2 Exporters & Dashboards
- RabbitMQ exporter (9419), Postgres exporter (9187), node-exporter (9100), cadvisor (8082).
- Grafana dashboards: visão geral, backend, RabbitMQ, Postgres, infra, DXF (in `monitoring/grafana/provisioning`).

### 5.3 Logs
- Backend: logs Spring Boot + loggers custom (DOBRAS, IMPORT, DXF).
- FileWatcher: `ILogger<T>` (console) com prefixos `[WATCHER-*]`, `[DXF]`, `[PROCESS-*]`.
- Nginx: access/error logs (config default).

## 6. Segurança

- **Nginx**: `allow/deny` por faixa IP, `X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`.
- **Credenciais**: Postgres/Rabbit/MinIO configuráveis via compose; nunca expor secrets em repositório.
- **CORS/WebSocket**: `app.cors.allowed-origins` define hosts confiáveis (default inclui localhost/nginx container).
- **Health endpoints**: expostos em `/actuator/health`, `/actuator/prometheus` (config via properties).

## 7. Considerações de Escalabilidade

- **RabbitMQ**: filas persistentes; watchers usam retries exponenciais; backend idempotente (`findByAnalysisId`, dedup NR).
- **DXFAnalysisWorker**: paralelismo configurável (`parallelism`, default `Environment.ProcessorCount/2`), caching por hash, storage S3 para imagens.
- **Frontend**: watchers locais para delivered list até API cursorial ser otimizada; modularização em componentes stand-alone.
- **Observabilidade**: dashboards facilitam identificar gargalos (latência HTTP, backlog DXF, consumo Rabbit).

## 8. Backlog Técnico (resumo)

Referenciar `docs/pending-updates.md`:
- Calibração de scores (NR 120253 ≈1.7; NR 120247 ≈4).
- Implementar filtros reais em `OrderRepositoryImpl.searchDeliveredByCursor`.
- Atualizar documentação de DXF no front após ajustes de scoring/imagens.

## 9. Referências

- `docs/PRD.md`
- `docs/FRD.md`
- `docs/dxf-complexity-engine.md`
- `docs/complexidade-facas.md`
- `../FileWatcherApp/docs/*.md`
