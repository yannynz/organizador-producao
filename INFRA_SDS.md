# Infra SDS (Software Design Specification)

Este documento descreve a infraestrutura do projeto "organizador-producao".
Objetivo: explicar o que cada parte faz, como faz, por que existe, e quais sao os
principais arquivos, fluxos e configuracoes. Conteudo em ASCII por padrao.

## 1. Escopo e objetivos

- Orquestracao via Docker Compose (backend, frontend, nginx, banco, mensageria e observabilidade).
- Reverse proxy com Nginx para servir o frontend e expor API/WS.
- Persistencia em PostgreSQL e storage de imagens DXF em MinIO.
- Monitoramento com Prometheus, Grafana e exporters.
- Operacao local com scripts de inicializacao e utilitarios.

## em prod eh 192.168.10.13.
- para tudo oq for necessario, tirando logicamente a comunicacao entre containers.

## 2. Arquitetura e topologia

Componentes:

- Nginx (publico) -> serve SPA e faz proxy de `/api` e `/ws` para o backend.
- Backend (Spring Boot) -> usa Postgres, RabbitMQ e MinIO (imagens DXF).
- RabbitMQ -> filas do FileWatcher, importacao de OP e analise DXF.
- Postgres -> dados principais (orders, clientes, users, history).
- Observabilidade -> Prometheus coleta metricas; Grafana visualiza.

Fluxo de rede (alto nivel):

```
Usuario -> Nginx (80)
  |-> / (SPA) -> arquivos estaticos
  |-> /api -> backend:8080
  |-> /ws  -> backend:8080

Backend -> Postgres:5432
Backend -> RabbitMQ:5672
Backend -> MinIO:9000 (imagens DXF)

Prometheus -> backend/exporters
Grafana -> Prometheus
```

## 3. Docker Compose (servicos)

Arquivo: `docker-compose.yml`.

### 3.1 Banco e mensageria

- `postgres-container`
  - image: `postgres:17`.
  - porta: `5433:5432`.
  - env: `POSTGRES_DB=teste01`, `POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=1234`.
  - volume: `pgdata17:/var/lib/postgresql/data`.
  - healthcheck: `pg_isready`.

- `rabbitmq-container`
  - image: `rabbitmq:management`.
  - portas: `5672` (AMQP), `15672` (UI).
  - env: `guest/guest`.

### 3.2 Backend

- `backend-container`
  - build: `Dockerfile` na raiz.
  - porta: `8081:8080`.
  - env:
    - DB e RabbitMQ (`SPRING_DATASOURCE_*`, `SPRING_RABBITMQ_*`).
    - CORS: `APP_CORS_ALLOWED_ORIGINS`.
    - DXF: `APP_DXF_ANALYSIS_IMAGE_BASE_URL` (aponta para MinIO).
    - Frontend URL: `APP_FRONTEND_URL`.
    - Email: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`.
  - volumes: `/tmp/nr:/tmp/nr:ro`.
  - depends_on: Postgres e RabbitMQ.

### 3.3 Frontend e proxy

- `frontend-container`
  - build: `organizer-front/Dockerfile`.
  - volume: `./organizer-front/dist:/usr/share/nginx/html`.
  - depende do backend (mas nao exposto publicamente).

- `nginx-container`
  - image: `nginx:latest`.
  - porta: `80:80`.
  - env: `SERVER_HOST` usado em template.
  - volumes:
    - `./organizer-front/dist/organizer-front/browser` (SPA).
    - `./nginx/organizador.conf` (template de nginx).
  - faz proxy de `/api` e `/ws` para `backend-container:8080`.

### 3.4 Observabilidade

- `prometheus-container` (porta `9090`)
  - config: `monitoring/prometheus.yml`.
  - alertas: `monitoring/prometheus-alerts.yml`.

- `grafana-container` (porta `3000`)
  - datasource provisionado em `monitoring/grafana/provisioning/datasources`.
  - dashboards provisionados em `monitoring/grafana/provisioning/dashboards`.

- Exporters:
  - `rabbitmq-exporter` (9419).
  - `postgres-exporter` (9187).
  - `node-exporter` (9100).
  - `cadvisor` (8082 -> 8080).

### 3.5 Storage de imagens

- `minio`
  - portas: `9000` (S3) e `9091` (console).
  - credenciais: `minio/minio123`.
  - bucket: `facas-renders` (publico).
- `minio-init`
  - cria bucket e libera download anonimo.

## 4. Rede e volumes

- Network: `organizador-producao-mynetwork` (bridge).
- Volumes persistentes:
  - `pgdata17`, `prometheus-data`, `grafana-data`, `minio-data`.

## 5. Nginx (reverse proxy)

Arquivo: `nginx/organizador.conf`.

- Serve SPA em `/` (fallback para `index.html`).
- Proxy `/api/` e `/ws/` para `backend-container:8080`.
- Cache de estaticos por 1 dia (JS/CSS/imagens).
- Headers de seguranca basicos (X-Frame-Options, nosniff, XSS).
- `allow` list existe, mas `deny all` esta comentado (nao bloqueia).

## 6. Build de imagens (Dockerfiles)

### Backend (`Dockerfile`)

- Build multi-stage com `openjdk:17-jdk-slim`.
- Executa `./mvnw clean install -DskipTests`.
- Produz `app.jar` e expoe `8080`.

### Frontend (`organizer-front/Dockerfile`)

- Build Angular em `node:18-alpine`.
- `npm run build --configuration=production`.
- Serve estatico com `nginx:alpine`.

## 7. Observabilidade

- Prometheus coleta metricas:
  - Backend: `/actuator/prometheus`.
  - RabbitMQ/Postgres/Node/cAdvisor via exporters.
- Alertas em `monitoring/prometheus-alerts.yml`.
- Grafana provisiona dashboards:
  - `organizador-overview`, `organizador-backend`, `organizador-infra`,
    `organizador-rabbitmq`, `organizador-postgres`.

## 8. Operacao

Scripts:

- `startup_organizer.sh`
  - `docker-compose down`.
  - `ng build -c production` no frontend.
  - `docker-compose up --build -d`.
  - Caminho fixo: `/home/recepcao/organizador-producao`.

- `scripts/dxf-replay.sh`
  - Simula fluxo DXF criando/movendo arquivos em `/home/laser` e `/home/dobras`.
  - Usa `sudo` e paths absolutos.

## 9. Mapa de portas e URLs

- Nginx (SPA/API/WS): `http://<SERVER_HOST>/` (default `192.168.10.13`), `http://localhost:80/`, em prod eh 192.168.10.13.
- API via proxy: `http://<SERVER_HOST>/api/...`, `http://localhost/api/...`.
- WebSocket STOMP: `ws://<SERVER_HOST>/ws/orders`, `ws://localhost/ws/orders`.
- Backend direto (porta mapeada): `http://localhost:8081` (container usa 8080).
- RabbitMQ:
  - AMQP: `amqp://localhost:5672`.
  - UI: `http://localhost:15672` (guest/guest).
- Postgres:
  - Host: `localhost:5433`.
  - Conn string: `postgresql://postgres:1234@localhost:5433/teste01`.
- Prometheus: `http://localhost:9090`.
- Grafana: `http://localhost:3000` (admin/admin123).
- MinIO:
  - S3: `http://localhost:9000`.
  - Console: `http://localhost:9091`.
- Exporters:
  - RabbitMQ: `http://localhost:9419/metrics`.
  - Postgres: `http://localhost:9187/metrics`.
  - Node: `http://localhost:9100/metrics`.
  - cAdvisor: `http://localhost:8082/metrics`.

Enderecos internos (rede Docker):

- Backend: `http://backend-container:8080`.
- Nginx: `http://nginx-container:80`.
- Frontend (nginx local): `http://frontend-container:80`.
- RabbitMQ: `amqp://rabbitmq-container:5672`, UI `http://rabbitmq-container:15672`.
- Postgres: `postgresql://postgres:1234@postgres-container:5432/teste01`.
- Prometheus: `http://prometheus:9090` (alias na rede).
- Grafana: `http://grafana-container:3000`.
- MinIO: `http://minio:9000`, console `http://minio:9091`.

## 10. Validacao em ambiente local (2026-01-15)

Ambiente usado:

- `docker compose up --build -d` com `SERVER_HOST=192.168.10.13` e `MINIO_HOST=192.168.10.13`.
- FileWatcherApp executado via dotnet (fora do Docker) com overrides:
  - `LASER_DIR=/home/ynz/Documents/organizador-producao/tmp/laser`
  - `FACAS_DIR=/home/ynz/Documents/organizador-producao/tmp/laser/FACASOK`
  - `DOBRAS_DIR=/home/ynz/Documents/organizador-producao/tmp/dobras`
  - `OPS_DIR=/home/ynz/Documents/organizador-producao/tmp/nr`
  - `DXFAnalysis__WatchFolder=/home/ynz/Documents/organizador-producao/tmp/laser/FACASOK`
  - `DXFAnalysis__OutputImageFolder=/home/ynz/Documents/organizador-producao/tmp/laser/FACASOK/Renders`
  - `DXFAnalysis__CacheFolder=/home/ynz/Documents/organizador-producao/tmp/laser/FACASOK/Cache`

Checks realizados:

- `http://localhost/` retornou `200 OK` (SPA via Nginx).
- `http://localhost:8081/actuator/health` retornou `UP`.
- Containers principais ativos: backend, frontend, nginx, postgres, rabbitmq, minio, grafana, prometheus e exporters.
- Exporters:
  - `http://rabbitmq-exporter:9419/metrics`
  - `http://postgres-exporter:9187/metrics`
  - `http://node-exporter:9100/metrics`
  - `http://cadvisor:8080/metrics`

## 10. Seguranca e configuracao

- Senhas e tokens estao hardcoded no `docker-compose.yml` (dev).
- Nao ha TLS no Nginx (porta 80 apenas).
- `node-exporter` e `cadvisor` acessam host (privileged/volumes).
- CORS configurado no backend via `APP_CORS_ALLOWED_ORIGINS`.

## 11. Status real e pontos de atencao

- Funciona: stack principal com Postgres, RabbitMQ, backend e Nginx.
- Funciona: Prometheus + Grafana com exporters.
- Funciona: MinIO com bucket publico `facas-renders`.
- Ponto de atencao: `frontend-container` monta `dist/` inteiro, enquanto o build gera `dist/organizer-front/browser`; o Nginx publico usa o path correto.
- Ponto de atencao: credenciais expostas em compose (email, banco, MinIO, Grafana).

## 12. Diagramas ASCII (fluxos principais)

### 11.1 Request web

```
Browser -> Nginx:80 -> /api -> backend:8080 -> Postgres/Rabbit
Browser -> Nginx:80 -> /ws  -> backend:8080 (STOMP)
Browser -> Nginx:80 -> /    -> SPA
```

### 11.2 DXF

```
Backend -> RabbitMQ (facas.analysis.request)
RabbitMQ -> worker -> MinIO (facas-renders)
Backend -> expoe imageBaseUrl -> Frontend
```

## 13. Catalogo de arquivos chave

- `docker-compose.yml`
  - Define servicos, portas, volumes e variaveis de ambiente.
- `Dockerfile`
  - Build do backend (JDK 17, Maven).
- `organizer-front/Dockerfile`
  - Build do frontend (Node 18) e nginx para estatico.
- `nginx/organizador.conf`
  - Reverse proxy, cache e headers.
- `monitoring/prometheus.yml`
  - Targets de scrape do Prometheus.
- `monitoring/prometheus-alerts.yml`
  - Alertas de latencia, erro, fila, heap, disco e restarts.
- `monitoring/grafana/provisioning/datasources/datasource.yml`
  - Datasource Prometheus.
- `monitoring/grafana/provisioning/dashboards/dashboard.yml`
  - Provisionamento de dashboards.
- `monitoring/grafana/provisioning/dashboards/json/*.json`
  - Dashboards: overview, backend, infra, rabbitmq, postgres.
- `startup_organizer.sh`
  - Orquestra build do frontend + compose up.
- `scripts/dxf-replay.sh`
  - Simula eventos de DXF via filesystem.
