Organizador Produção
====================

Sistema web para gestão de ordens de produção, com frontend em Angular, backend em Spring Boot, WebSocket, RabbitMQ, PostgreSQL e Nginx.

Documentação principal:

- [Product Requirements (PRD)](docs/PRD.md)
- [Functional Requirements (FRD)](docs/FRD.md)
- [System Design (SDS)](docs/SDS.md)
- [DXF Complexity Engine](docs/dxf-complexity-engine.md) e [Complexidade de Facas (FileWatcherApp)](../FileWatcherApp/docs/complexidade-facas.md)

Tecnologias
-----------
- Frontend: Angular, Bootstrap
- Backend: Java Spring Boot (REST + WebSocket + JPA/Hibernate)
- Mensageria: RabbitMQ
- Banco de Dados: PostgreSQL
- Orquestração: Docker Compose
- Servidor HTTP: Nginx (serve o frontend produzido)
- Monitoramento: Prometheus + Grafana

Instalacao operacional (Ubuntu Server / maquina fisica)
-------------------------------------------------------
Use este fluxo quando for subir o ambiente em uma maquina nova e deixar operacao pronta (app + backup + restart automaticos).

1. Prepare a maquina (Ubuntu Server):
   - usuario operador com `sudo`;
   - diretorio home do usuario (`/home/<usuario>`) disponivel;
   - Docker Engine + compose plugin instalados (se ainda nao tiver):
   ```bash
   sudo apt-get update
   sudo apt-get install -y ca-certificates curl gnupg
   sudo install -m 0755 -d /etc/apt/keyrings
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
     | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
   sudo chmod a+r /etc/apt/keyrings/docker.gpg
   echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
     | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
   sudo apt-get update
   sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
   ```
2. Clone o repositorio no home do usuario:
   ```bash
   cd ~
   git clone https://github.com/yannynz/organizador-producao.git
   cd organizador-producao
   ```
3. Execute o instalador:
   ```bash
   sudo ./installer/install-organizer.sh --user "$USER"
   ```
4. Durante o fluxo, confirme:
   - IP da maquina (o script detecta e sugere automaticamente);
   - periodicidade do backup automatico;
   - periodicidade do restart automatico da aplicacao.
5. Ao final, o instalador cria/atualiza:
   - estrutura padrao:
     - `~/organizador-producao`
     - `~/backup_database`
   - arquivo central: `/etc/organizer/organizer.env`
   - comandos globais:
     - `update-organizer` (manual)
     - `organizer-backup` (manual e usado pelo timer)
     - `organizer-restore` (manual)
     - `organizer-installer` (reconfiguracao)
   - servicos/timers systemd:
     - `organizer-stack.service`
     - `organizer-backup.timer`
     - `organizer-restart.timer`
6. Estrutura final esperada no home do usuario:
   - `/home/<usuario>/organizador-producao`
   - `/home/<usuario>/backup_database`
7. Se for troca de maquina fisica, restaure o backup completo manualmente:
   ```bash
   organizer-restore --choose
   ```

Comandos operacionais apos instalacao:

```bash
systemctl status organizer-stack.service
systemctl status organizer-backup.timer
systemctl status organizer-restart.timer
```

Reconfigurar agendas sem reinstalar tudo:

```bash
sudo organizer-installer --reconfigure-schedules --user "$USER"
```

Observacoes:
- O instalador detecta hostname/IP automaticamente, e permite escolher o IP durante o fluxo.
- `update-organizer` e `organizer-restore` permanecem ferramentas manuais (sem agendamento automatico).
- Se o instalador adicionar o usuario ao grupo `docker`, faca logout/login antes de rodar comandos Docker sem `sudo`.

Como rodar o projeto
--------------------
1. Clone o repositório:
   ```bash
   git clone https://github.com/yannynz/organizador-producao.git
   cd organizador-producao
   ```
2. Suba todos os serviços (frontend, backend, banco, RabbitMQ, Nginx), com build automático do frontend:
   ```bash
   docker compose up --build
   ```
   - O Dockerfile do frontend executa `npm run build --configuration=production`.
   - O Nginx serve imediatamente o pacote buildado pela mesma composição.
3. Com tudo rodando, explore as métricas:
   - Prometheus: http://localhost:9090 
   - Grafana: http://localhost:3000 (login `admin` / senha `admin123`)
   - Endpoint direto do backend: http://localhost:8081/actuator/prometheus

Serviços disponíveis
--------------------
| Serviço          | URL                        |
| ---------------- | -------------------------- |
| Frontend (Angular)| http://localhost          |
| Backend (API)    | http://localhost:8081      |
| RabbitMQ UI      | http://localhost:15672     |
| Prometheus       | http://localhost:9090      |
| Grafana          | http://localhost:3000      |

Arquitetura
-----------
- `/organizer-front`: código fonte Angular
- `/src`: backend Spring Boot
- `/nginx`: configuração do Nginx
- `/monitoring`: arquivos de configuração do Prometheus e do Grafana
- Dockerfile
  - Fase build: usa `node:18-alpine`, instala dependências, ajusta timezone e executa o build Angular
  - Fase final: usa `nginx:alpine`, copia os assets gerados
- `docker-compose.yml` define os containers:
  - `postgres-container`: banco com healthcheck, timezone configurado, volume `pgdata17`
  - `rabbitmq-container`: gerencia filas e WebSocket
  - `rabbitmq-exporter`: expõe métricas detalhadas do broker para o Prometheus
  - `backend-container`: Spring Boot, depende de Postgres + RabbitMQ
  - `frontend-container`: container usado apenas para build — o conteúdo final é servido pelo Nginx
  - `nginx-container`: container final que expõe o frontend na porta 80
  - `postgres-exporter`: coleta conexões, transações e locks do PostgreSQL
  - `node-exporter`: métricas do host (CPU, memória, disco, rede)
  - `cadvisor`: métricas dos containers Docker (CPU, memória, reinícios, uptime)
- `prometheus-container`: coleta métricas do backend Spring Boot (Micrometer + Actuator)
- `grafana-container`: visualização das métricas com datasource provisionado para o Prometheus
- `minio` e `minio-init`: storage S3 compatível para os renders DXF. O init aguarda o serviço ficar disponível, cria o bucket `facas-renders` e aplica permissão de leitura anônima (ajuste conforme o ambiente).

Monitoramento e Métricas
------------------------
- O backend expõe métricas Micrometer em `http://localhost:8081/actuator/prometheus`, incluindo:
  - Latência HTTP p95/p99, RPS e contagem de erros 4xx/5xx (`organizador_http_server_*`)
  - Tempo de processamento de mensagens por fila (`organizador_message_processing_seconds_*`)
  - Sessões WebSocket ativas (`organizador_websocket_active_sessions`) e métricas JVM (heap, GC, threads)
- Exportadores adicionais:
  - `rabbitmq-exporter` (porta `9419`): filas, publish/consume rate, rejeições, consumidores
  - `postgres-exporter` (porta `9187`): conexões, transações, locks, deadlocks, métricas de I/O
  - `node-exporter` (porta `9100`) e `cadvisor` (porta `8082`): CPU, memória, disco, rede, uptime e reinícios dos containers
- O Prometheus usa `monitoring/prometheus.yml` para registrar todos os jobs (backend, exportadores, Prometheus). As regras de alerta ficam em `monitoring/prometheus-alerts.yml` e são montadas em `/etc/prometheus/alerts.yml`.
- Alertas configurados (avaliados no Prometheus):
  - Latência HTTP p95 > 500ms por 5min (`ApiHighLatencyP95`) — severidade **alta**
  - Erros 5xx > 1% por 2min (`ApiErrorRateHigh`) — **alta**
  - Backlog > 1000 mensagens por 10min (`RabbitMqBacklogHigh`) — **média**
  - Uso de heap > 85% por 5min (`JvmHeapHighUsage`) — **média**
  - Conexões PostgreSQL > 90% do limite por 2min (`PostgresConnectionsNearLimit`) — **alta**
  - Containers com >3 reinícios em 10min (`ContainerRestartsBurst`) — **alta**
  - Disco com >90% de utilização por 5min (`DiskUsageCritical`) — **alta**
- Grafana já é provisionado com datasource Prometheus (`monitoring/grafana/provisioning/datasources/datasource.yml`) e cinco dashboards prontos (`monitoring/grafana/provisioning/dashboards/json`):
  1. **Visão Geral** (`organizador-geral`): KPIs principais, filas e infraestrutura
  2. **Backend** (`organizador-backend`): endpoints, mensagens, JVM, WebSocket e seção **DXF Analysis** (processados na última hora, uploads por status, tamanho médio das imagens)
  3. **RabbitMQ** (`organizador-rabbitmq`): backlog, taxas, rejeições e consumidores
  4. **PostgreSQL** (`organizador-postgres`): conexões, I/O, locks, deadlocks, crescimento
  5. **Infraestrutura** (`organizador-infra`): host (CPU/mem/disco/rede) e containers (CPU, memória, uptime, reinícios)
- Acesse o Grafana em `http://localhost:3000` (login `admin` / senha `admin123`). Para alterar credenciais, ajuste `GF_SECURITY_ADMIN_USER` / `GF_SECURITY_ADMIN_PASSWORD` no `docker-compose.yml`. Os dados persistem no volume `grafana-data`.

DXF Storage (MinIO)
-------------------
- O `docker-compose.yml` já inclui healthcheck no MinIO e um job `minio-init` com retry automático. Em ambientes limpos:
  ```bash
  docker compose up -d minio minio-init
  docker compose logs -f minio-init
  ```
- Para reaplicar a criação/permissão do bucket a qualquer momento:
  ```bash
  docker compose run --rm minio-init
  ```
- Valide o bucket `facas-renders` pelo console (`http://localhost:9091`) ou via CLI:
  ```bash
  docker compose run --rm minio-init mc ls local/facas-renders
  ```
- Ajuste o FileWatcherApp (`FileWatcherApp/appsettings.Production.json` ou variáveis `DOTNET_`) para publicar direto no MinIO:
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
  Reinicie o worker após aplicar a configuração e reprocessar um DXF: os campos `imageBucket`, `imageKey`, `imageUri` e `imageUploadStatus` passam a ser preenchidos, permitindo que o frontend exiba a miniatura real.

Scripts úteis
-------------
- Rebuild manual do frontend (sem Docker completo):
  ```bash
  cd organizer-front
  npm install
  npm run build --configuration=production
  ```
- Rodar apenas o backend (modo desenvolvimento):
  ```bash
  cd src
  ./mvnw spring-boot:run
  ```
- Reexecutar o fluxo DXF (DEV):
  ```bash
  ./scripts/dxf-replay.sh
  docker compose up --build
  ```
  O script (executa `sudo` internamente) replica o ciclo manual: cria o arquivo CNC em `/home/laser`, move para `FACASOK/` e copia todos os `.dxf` de `~/Documents` para `/home/dobras/`.
- Resetar ambiente Docker:
  ```bash
  docker compose down --volumes
  docker compose up --build
  ```

Restaurar backup do banco de dados
----------------------------------
Há um dump compactado (formato `pg_dump -Fc`) em `../backup_2025-09-29_02-00-02.dump.gz`. Para usá-lo com a stack Docker atual:

1. Suba os contêineres.
   ```bash
   docker compose up -d
   ```
2. Importe o backup no `postgres-container`:
   ```bash
   gunzip -c ../backup_2025-09-29_02-00-02.dump.gz \
     | docker exec -i postgres-container \
         env PGPASSWORD=1234 \
         pg_restore --clean --if-exists --no-owner \
                    --username=postgres \
                    --dbname=teste01
   ```
   - `--clean --if-exists` remove objetos previamente criados.
   - `--no-owner` evita erros de permissão ao aplicar o dump em outro ambiente.
3. (Opcional) Valide os dados carregados:
   ```bash
   docker exec -it postgres-container \
     psql -U postgres -d teste01 \
     -c "SELECT status, COUNT(*) FROM orders GROUP BY status ORDER BY status;"
   ```

Após a restauração, o backend (porta 8081) e o frontend via Nginx (porta 80) já operam com o dataset do backup para testar a fase de vinco.
