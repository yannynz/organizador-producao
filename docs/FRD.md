# Functional Requirements Document (FRD)

## 1. Contexto

Este FRD detalha requisitos funcionais da solução Organizador Produção, composta por:

- **Backend Spring Boot** (`src/`) — REST + WebSocket + RabbitMQ + PostgreSQL.
- **Frontend Angular** (`organizer-front/`) — UI operacionais (Produção, Delivery, Delivered, Rubber, filtros avançados).
- **FileWatcherApp** (repo adjacente) — monitor de diretórios e pipeline DXF em .NET 8.
- **Infra Docker Compose** — Postgres, RabbitMQ, MinIO, Nginx, Prometheus, Grafana e exporters.

Os requisitos foram mapeados com base nos repositórios locais, documentação existente (`docs/dxf-complexity-engine.md`, `../FileWatcherApp/docs/*.md`) e práticas atuais de operação.

## 2. Requisitos Funcionais

### 2.1 Gestão de Pedidos (API `/api/orders`)

1. **CRUD**: permitir criação, leitura (todas, por ID, por NR), atualização e exclusão de pedidos.
2. **Atualização parcial**: rota `/api/orders/update/{id}` deve aceitar payload parcial e manter campos quando `null`.
3. **Atualização de status**: `/api/orders/{id}/status` aceita parâmetros `status`, `entregador`, `observacao`, ajustando `dataEntrega`.
4. **Busca com cursor**: endpoint `/api/orders/search-cursor` deve suportar filtros (OrderSearchDTO), limit (1..200) e estratégias `ID` ou `DATE_ID`, retornando `PageEnvelope` com `nextCursor`.
5. **Auto link com OP**: `OrderService.saveOrder` precisa invocar `OpImportService.tryLinkAsync` para sincronizar OP recém-importada.

### 2.2 Regras de Negócio de Pedido

1. **Auto “Pronto para Entrega”**: `OrderStatusRules.applyAutoProntoEntrega` deve promover status para 2 quando:
   - Pedido não está emborrachado.
   - (a) `vaiVinco=false` e `dataMontagem` preenchida; ou (b) `vaiVinco=true` e `dataVinco` preenchida.
2. **Prioridade automática**: job `updateOrderPriorities` roda a cada 60s para rotacionar VERDE→AZUL (>=48h) e AZUL→AMARELO (>=24h); nunca rebaixa VERMELHA.
3. **Deduplicação de Dobras**: apenas arquivos `.m.dxf` ou `.dxf.fcd` atualizam status “Tirada”; bases `.dxf` só disparam análise DXF.
4. **Travamentos manuais**: flags `manualLock_*` em `op_import` impedem que atualizações automáticas sobrescrevam decisões humanas (ex.: emborrachada).

### 2.3 Integração FileWatcher

1. **Filas**:
   - `laser_notifications`: cria pedidos a partir de CNCs (NR/CL) e registra “aguardando corte”.
   - `facas_notifications`: atualiza status (1=NR cortada, 2=CL pronta) e logs de destaque “cortado”.
   - `dobra_notifications`: somente `.m.dxf/.dxf.fcd` (normalizados via `FileWatcherNaming`) publicam evento com `file_name`, `path`, `timestamp`.
   - `op.imported`: payload JSON `OpImportRequestDTO`; service aplica parse de PDF, detecta materiais, atualiza pedido.
2. **DXF Analysis**:
   - Fila `facas.analysis.request` recebe mensagem JSON com `analysisId`, `filePath`, `fileName`, `opId/orderNumber`, `forceReprocess`, `shadowMode`, `flags`, `meta`.
   - Worker processa, gera score/PNG, publica em `facas.analysis.result`.
   - Backend consome, persiste `DXFAnalysis` e reenvia resumo via `/topic/dxf-analysis`.
3. **Deduplicação**:
   - `FileWatcherService` precisa aguardar file-handle (8s Dobras, 20s OP) antes de publicar.
   - `_dobrasSeen` impede duplicates se ocorrerem em <2 min.

### 2.4 DXF Analysis

1. **Persistência**: `DXFAnalysis` deve armazenar:
   - Identificadores (analysisId, orderNr/id).
   - Métricas (score, label, stars, totalCutLengthMm, curveCount, intersectionCount, minRadiusMm).
   - Imagem (path, width, height) e metadados de upload (bucket, key, uri, checksum, size, contentType, status, message, uploadedAt, etag).
   - Flags `cacheHit`, `raw_payload`, `explanations`.
2. **API**:
   - `GET /api/dxf-analysis/order/{nr}` → último registro **com imagem válida** quando houver (fallback para o último sem imagem).
   - `GET /history?limit` → lista (1..25).
   - `GET /{analysisId}` → view.
   - `GET /{analysisId}/image` → redireciona para `imageUrl` (base `app.dxf.analysis.image-base-url`) ou responde 404.
   - `POST /request` → trigga nova análise, retorna `{analysisId, orderNumber}`.
3. **WebSocket**: `/topic/dxf-analysis` publica `DXFAnalysisView` sempre que `persistFromPayload` salva uma entidade.
4. **Scoring**: valores devem ser configuráveis via `DXFAnalysisOptions.Scoring` em `appsettings.json`, incluindo thresholds para serrilha mista/travada/cola, cortes secos, materiais sensíveis.

### 2.5 Frontend Angular

1. **OrdersComponent**:
   - Mostra pedidos nos status 0/1/6 (em produção/cortada/tirada).
   - Consome `/topic/orders` & `/topic/prioridades`.
   - Permite filtros por prioridade e modais de criação/edição.
2. **DeliveryComponent**:
   - Foca em status (1,2,3,4,5,6,7,8) elegíveis para logística.
   - Possui seleção múltipla com formulário para motorista, veículo, notas e recebedor.
   - Modal para “saída adversa” cria pedido com status 2 (Pronto).
3. **DeliveredComponent**:
   - Lista paginada (local) de status 4/5 com busca textual e modais.
   - Usa `orderDetailsModal` para exibir dados completos + DXF.
4. **Delivered-List tabs**:
   - Componente independente que opera todo o backlog em “modo local” (até API cursorial estar pronta).
5. **RubberComponent**:
   - Filtra facas com `status` 7/8 e `emborrachada=true`.
   - Atualiza status para 2 e carimba `dataEmborrachamento`.
6. **Order Details Modal**:
   - Leitura/escrita de praticamente todos os campos do pedido.
   - Integra com `DxfAnalysisService` (latest + history) e exibe imagem renderizada/estrelas.
   - Botão para abrir PDF da OP (via `OpService`).
7. **WebsocketService**:
   - Configura RxStomp com `environment.wsUrl` e expõe watchers/publishers (orders, prioridades, status, dxf).

### 2.6 Observabilidade

1. **Métricas HTTP**: `organizador_http_server_latency_seconds`, `organizador_http_server_requests_total`, `organizador_http_server_errors_total`.
2. **Métricas RabbitMQ**: `organizador_message_processing_seconds`, `organizador_dxf_analysis_*`, `organizador_dxf_image_size_bytes`, `organizador_dxf_analysis_upload_total`.
3. **WebSocket Gauge**: `organizador_websocket_active_sessions`.
4. **Prometheus**: deve scrapear backend, exporters (RabbitMQ, Postgres, node-exporter, cadvisor) e o próprio Prometheus.
5. **Grafana**: dashboards provisionados (geral, backend, RabbitMQ, Postgres, infra, DXF).

### 2.7 Integração com MinIO e Armazenamento

1. **Backend**: usa `APP_DXF_ANALYSIS_IMAGE_BASE_URL` para montar URLs públicas; fallback para roots locais.
2. **FileWatcher**: configura `DXFAnalysis.ImageStorage` (bucket, endpoint, credenciais, `PublicBaseUrl`, `UsePathStyle`) para publicar PNG em MinIO.
3. **Compose**: inclui serviços `minio` e `minio-init` para bootstrap do bucket `facas-renders` com permissão de leitura local.

## 3. Modelos de Dados

### 3.1 `orders`

Campos principais: `id`, `nr`, `cliente`, `prioridade`, `status`, datas (`dataH`, `dataEntrega`, `dataTirada`, `dataMontagem`, `dataVinco`, etc.), pessoas (`entregador`, `recebedor`, `montador`, `emborrachador`), flags (`emborrachada`, `pertinax`, `poliester`, `papeCalibrado`, `vaiVinco`), campos extras (`destacador`, `modalidadeEntrega`, `usuarioImportacao`), e referências a `dxf_analysis`.

### 3.2 `op_import`

Campos: `numero_op`, `cliente`, `data_op`, `emborrachada`, `share_path`, `materiais` (JSON), `destacador`, `modalidade_entrega`, `faca_id`, `manualLock_*`, `data_requerida_entrega`, `usuario_importacao`, `pertinax/poliester/papel_calibrado/vai_vinco`.

### 3.3 `dxf_analysis`

Ver PRD seção 6; campos extras: `metrics_json`, `explanations_json`, `raw_payload_json`, `cache_hit`, `created_at`, `updated_at`.

## 4. Requisitos de Interfaces Externas

### 4.1 REST APIs

| Método | Endpoint | Descrição |
| ------ | -------- | --------- |
| `GET` | `/api/orders` | Lista todos os pedidos |
| `GET` | `/api/orders/{id}` | Busca por ID |
| `GET` | `/api/orders/nr/{nr}` | Busca por NR |
| `POST` | `/api/orders/create` | Cria pedido |
| `PUT` | `/api/orders/update/{id}` | Atualiza pedido |
| `DELETE` | `/api/orders/delete/{id}` | Remove pedido |
| `PUT` | `/api/orders/{id}/status` | Atualiza status/entregador/observação |
| `POST` | `/api/orders/search-cursor` | Busca com cursor (resultado envelope) |
| `POST` | `/api/dxf-analysis/request` | Solicita análise DXF |
| `GET` | `/api/dxf-analysis/order/{nr}` | Última análise por NR |
| `GET` | `/api/dxf-analysis/order/{nr}/history?limit` | Histórico |
| `GET` | `/api/dxf-analysis/{id}` | Análise específica |
| `GET` | `/api/dxf-analysis/{id}/image` | Redireciona para imagem |
| `POST` | `/ops/import` | Importa OP (chamado pelo watcher) |
| `PATCH` | `/ops/{id}/vincular-faca/{facaId}` | Vincula OP a pedido |

### 4.2 WebSocket / STOMP

- `/ws/orders` endpoint STOMP (Nginx proxy).
- Tópicos: `/topic/orders`, `/topic/prioridades`, `/topic/dxf-analysis`, `/topic/status`.
- Destinos aplicação: `/app/orders/create`, `/app/orders/update`, `/app/status/ping-now`.

### 4.3 RabbitMQ

| Fila | Produtor | Consumidor | Payload |
| ---- | -------- | ---------- | ------- |
| `laser_notifications` | FileWatcher | Backend (`FileWatcherService`) | `{file_name, path, timestamp}` |
| `facas_notifications` | FileWatcher | Backend | Same |
| `dobra_notifications` | FileWatcher (.m/.dxf.fcd) | Backend (`DobrasFileService`) | `{file_name, original_file_name, path, timestamp}` |
| `op.imported` | FileWatcher (PDF) | Backend (`OpImportedListener`) | `OpImportRequestDTO` |
| `facas.analysis.request` | Backend (REST) + FileWatcher (watcher) | DXFAnalysisWorker | `DXFAnalysisRequest` |
| `facas.analysis.result` | DXFAnalysisWorker | Backend (`DXFAnalysisResultListener`) | `DXFAnalysisResult` |
| `filewatcher.rpc.ping` | Backend (`FileWatcherPingClient`) | FileWatcher (`RpcPingResponderService`) | `{"type":"ping"...}` |

## 5. Requisitos Não Funcionais Relevantes (Functional View)

- **Latência**: watchers/REST devem refletir estado no front via WebSocket em <2s.
- **Integridade**: deduplicação de eventos (Dobras e DXF) evita alterações redundantes.
- **Configurabilidade**: chaves `app.cors.allowed-origins`, `app.dxf.*`, `DXFAnalysisOptions` devem mudar comportamento sem rebuild.
- **Logs**: watchers devem logar eventos (process, warnings, errors) com contexto (queue, file, NR).

## 6. Traceabilidade (PRD ↔ FRD)

| PRD Objetivo | FRD Seções |
| ------------ | ---------- |
| Centralizar ciclo de pedidos | 2.1, 2.2, 2.5 |
| Automatizar ingestão/analysis | 2.3, 2.4 |
| Visibilidade em tempo real | 2.5, 4.2 |
| Qualidade operacional | 2.2, 2.3 |
| Observabilidade | 2.6, 4.3 |

## 7. Referências

- `docs/PRD.md`
- `docs/SDS.md`
- `docs/dxf-complexity-engine.md`
- `docs/pending-updates.md`
- `../FileWatcherApp/docs/complexidade-facas.md`
