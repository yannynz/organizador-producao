Organizador Produção
====================

Sistema web para gestão de ordens de produção, com frontend em Angular, backend em Spring Boot, WebSocket, RabbitMQ, PostgreSQL e Nginx.

Tecnologias
-----------
- Frontend: Angular, Bootstrap
- Backend: Java Spring Boot (REST + WebSocket + JPA/Hibernate)
- Mensageria: RabbitMQ
- Banco de Dados: PostgreSQL
- Orquestração: Docker Compose
- Servidor HTTP: Nginx (serve o frontend produzido)
- Monitoramento: Prometheus + Grafana

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
  - `backend-container`: Spring Boot, depende de Postgres + RabbitMQ
  - `frontend-container`: container usado apenas para build — o conteúdo final é servido pelo Nginx
  - `nginx-container`: container final que expõe o frontend na porta 80
- `prometheus-container`: coleta métricas do backend Spring Boot (Micrometer + Actuator)
- `grafana-container`: visualização das métricas com datasource provisionado para o Prometheus

Monitoramento e Métricas
------------------------
- O backend expõe métricas Micrometer em `http://localhost:8081/actuator/prometheus`. Esse endpoint é habilitado via Spring Boot Actuator e já exporta estatísticas JVM, HTTP e do agendamento.
- O Prometheus usa `monitoring/prometheus.yml` para coletar as métricas do backend a cada 15s. Ajuste esse arquivo para incluir novos jobs ou mudar o intervalo.
- O Grafana é iniciado com datasource `Prometheus` pré-configurado (provisionado em `monitoring/grafana/provisioning/datasources/datasource.yml`). Faça login em `http://localhost:3000` usando **usuário** `admin` e **senha** `admin123`.
- Após logar, importe um dashboard existente (ex.: [Spring Boot Statistics, ID 11378](https://grafana.com/grafana/dashboards/11378)) ou crie um painel do zero com consultas PromQL como `sum(rate(http_server_requests_seconds_count{uri!~"^/actuator.*"}[1m]))`.
- Para alterar as credenciais do Grafana, edite as variáveis `GF_SECURITY_ADMIN_USER` e `GF_SECURITY_ADMIN_PASSWORD` no `docker-compose.yml`. Os dados persistem no volume nomeado `grafana-data`.

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
