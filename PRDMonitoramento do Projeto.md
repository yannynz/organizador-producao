📄 PRD – Monitoramento do Projeto “Organizador Produção”
1. Contexto e Objetivo

O sistema Organizador Produção gerencia fluxos de produção industrial com backend em Spring Boot, frontend em Angular, mensageria com RabbitMQ, banco PostgreSQL e infraestrutura em Docker.
O objetivo deste PRD é definir as métricas que devem ser expostas, coletadas e monitoradas com Prometheus + Grafana, permitindo:

Observar a saúde geral do sistema em tempo real.

Detectar e diagnosticar falhas e gargalos de performance rapidamente.

Fornecer dados históricos para análise de capacidade e otimização.

Permitir alertas automatizados para incidentes críticos.

2. Escopo

Este PRD abrange:

Exposição de métricas em todos os componentes do sistema.

Coleta via Prometheus e visualização em Grafana.

Criação de dashboards e configuração inicial de alertas.

Não abrange:

Ajustes de arquitetura do sistema.

Automação de deploy de Grafana/Prometheus (infra deve estar previamente disponível).

3. Métricas a Implementar
🖥️ Backend (Spring Boot)

Latência de requisições HTTP (média e percentis 95/99 por endpoint).

Taxa de requisições por segundo (RPS).

Taxa de erros (4xx, 5xx por endpoint).

Tempo médio de processamento de mensagens.

Uso de memória JVM / heap / GC (pausas, frequência).

Número de threads ativas.

Conexões WebSocket ativas.

📌 Como: habilitar micrometer + prometheus actuator endpoint (/actuator/prometheus).

📬 RabbitMQ

Tamanho das filas.

Taxa de publicação e consumo de mensagens.

Tempo médio em fila (latência).

Taxa de rejeição / nack.

Número de consumidores conectados.

📌 Como: usar rabbitmq_exporter ou métricas nativas do broker.

🗄️ PostgreSQL

Queries por segundo.

Latência média / percentis de queries.

Conexões ativas / máximo permitido.

Transações (commits / rollbacks).

Locks e deadlocks detectados.

Uso de I/O e crescimento de disco.

📌 Como: utilizar postgres_exporter.

📦 Infraestrutura (Docker / Host)

CPU / memória / disco / rede por container.

Reinícios de containers.

Uptime dos serviços.

Latência de rede e disponibilidade.

📌 Como: configurar node_exporter e cadvisor.

🌐 Frontend (opcional)

Tempo de carregamento médio.

Taxa de falhas em requisições API.

Erros JS capturados no cliente.

📌 Como: integrar com ferramentas como Sentry ou enviar métricas customizadas via API.

4. Dashboards no Grafana

Criar dashboards separados e interligados:

Visão Geral – KPIs principais (latência, erros, RPS, filas, conexões DB).

Backend – detalhes por endpoint, GC, threads, heap.

Mensageria – filas, latências, consumo.

Banco – queries, locks, conexões.

Infraestrutura – containers, CPU, memória, uptime.

5. Alertas Sugeridos
Alerta	Condição	Severidade
Latência API > 500ms (P95)	por >5 min	Alta
Erros 5xx > 1%	por >2 min	Alta
Mensagens em fila > 1000	por >10 min	Média
Uso heap > 85%	por >5 min	Média
Conexões DB > 90% do máximo	por >2 min	Alta
Container reiniciando repetidamente	>3x em 10 min	Alta
Disco > 90%	contínuo	Alta


