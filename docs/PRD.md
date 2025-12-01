# Product Requirements Document (PRD)

## 1. Visão Geral

Organizador Produção é uma plataforma web destinada a monitorar, priorizar e concluir ordens de produção de facas, sincronizando eventos de chão de fábrica (laser, facas, dobras) com equipes administrativas. A solução integra:

- Backend Spring Boot (REST, WebSocket e RabbitMQ) hospedando regras de negócio e persistência (PostgreSQL).
- Frontend Angular que exibe filas operacionais, detalhes de OP/NR, DXF analysis e dashboards de entrega.
- FileWatcherApp (.NET 8) que observa diretórios físicos, publica eventos e coordena a análise determinística de DXF.
- Infraestrutura Docker Compose com Postgres, RabbitMQ, MinIO, Prometheus, Grafana, Nginx e exporters.

## 2. Objetivos

1. **Centralizar** o ciclo de vida de pedidos (criação, corte, destaque, borracha, entrega) com rastreabilidade completa.
2. **Automatizar** ingestão de OPs e DXFs reduzindo tarefas manuais (FileWatcher + DXFAnalysis).
3. **Fornecer visibilidade em tempo real** por meio de WebSockets e dashboards (Grafana + métricas customizadas).
4. **Garantir qualidade operacional** via regras determinísticas (status, prioridade, scoring de DXF, deduplicação).
5. **Escalar** para múltiplos ambientes (dev/local/docker) com configurações seguras e reprodutíveis.

## 3. Personas & Stakeholders

| Persona | Necessidade | Responsabilidades |
| ------- | ----------- | ----------------- |
| **Operador de corte** | Ver fila de produção, confirmar cortes, acompanhar destaques | Usa a tela “Produção” e atualiza status automaticamente via arquivos CNC/laser |
| **Equipe de Dobras** | Validar somente arquivos finais (.m.dxf/.dxf.fcd) e disparar análise | Atua via watcher; evita poluir fila `dobra_notifications` com bases |
| **Equipe de Entrega** | Planejar rotas, registrar entregas, anotar saídas adversas | Usa telas “Delivery” e “Delivered”, imprime PDFs, monitora status 3/4/5 |
| **Responsáveis por Borracha** | Identificar facas prontas (status 7/8 + flag emborrachada) | Tela “Rubber”, registra emborrachamento e data |
| **Gestores** | Acompanhar métricas, filas, SLA de DXF | Grafana, Prometheus, relatórios (score, throughput) |
| **Equipe de TI** | Operar pipelines, ajustar configurações, depurar watchers | Docker Compose, scripts, logs, health checks, integrações com FileWatcherApp |

## 4. Escopo

### 4.1 Funcionalidades Incluídas
- CRUD de pedidos com atualização parcial via REST e STOMP.
- Importação automática de OP (PDF) e sincronização de campos (emborrachada, materiais, destacador).
- Monitoramento dos diretórios Laser, Facas OK, Dobras e NR/CL com eventos RabbitMQ (`*_notifications`).
- Processamento determinístico de DXF (pré-processamento, métricas, render PNG, score 0-5).
- Fluxos específicos (Delivery, Delivered, Rubber) com filtros avançados e telinhas de suporte.
- Observabilidade (Micrometer -> Prometheus -> Grafana) e alertas pré-configurados.
- Infra Docker Compose com MinIO, Nginx, exporters e scripts de replay.

### 4.2 Fora de Escopo
- Versionamento de DXFs/OPs além da última análise persistida.
- Machine Learning para scoring (é intencionalmente determinístico).
- Orquestração multi-tenant ou alta disponibilidade (Compose foca em ambiente único).
- Aplicativos móveis nativos ou offline-first.
- Automação completa dos filtros cursoriais (há backlog em `docs/pending-updates.md`).

## 5. Requisitos de Alto Nível

1. **Integração FileWatcher**: consumir eventos RabbitMQ e refletir estado do pedido em <2s (SLA local).
2. **Score DXF**: armazenar `score`, `score_label`, `score_stars`, métricas e metadados de imagem para cada análise; permitir ajustes via config sem recompilar.
3. **Deduplicação Dobras**: apenas `.m.dxf/.dxf.fcd` entram na fila `dobra_notifications`; demais solicitam somente análise.
4. **WebSockets**: `/topic/orders`, `/topic/prioridades`, `/topic/dxf-analysis`, `/topic/status` devem refletir mudanças sem refresh.
5. **Segurança & Config**: segregar variáveis (RabbitMQ, DB, MinIO) com fallback local; Nginx deve restringir IPs e adicionar cabeçalhos de segurança.
6. **Observabilidade**: expor métricas customizadas (`organizador_http_server_*`, `organizador_dxf_*`, etc.) e dashboards documentados.
7. **Documentação**: manter guias técnicos de DXF (`docs/dxf-complexity-engine.md`, `docs/complexidade-facas.md`) e este pacote PRD/FRD/SDS.

## 6. Métricas de Sucesso

- **Tempo médio** entre evento `laser_notifications` e criação do pedido < 5 s.
- **Disponibilidade**: fila `facas.analysis.result` sem mensagens não processadas por >5 min.
- **Precisão**: 100% dos `.m.dxf/.dxf.fcd` resultam em status “Tirada (6)” na repetição do evento.
- **UX**: atualizações WebSocket percebidas instantaneamente (latência < 2 s) em telas principais.
- **Observabilidade**: dashboards Grafana carregáveis após `docker compose up --build` sem ajustes extras.

## 7. Requisitos Não Funcionais

- **Performance**: backend suporta pelo menos 200 requisições REST/min com latência p95 < 500 ms.
- **Confiabilidade**: retry exponencial nas publicações RabbitMQ (watcher) e deduplicação de eventos Dobras (2 min).
- **Manutenibilidade**: configuração declarativa (`application.properties`, `appsettings.json`) com comentários; testes unitários chave (OrderStatusRules, DXFAnalysisService, FileWatcher naming).
- **Segurança**: sem credenciais hardcoded além de valores dummy; Nginx com ACL; MinIO com usuário/senha configuráveis.
- **Observabilidade**: logs JSON/estruturados (FileWatcher) e métricas Micrometer habilitadas.

## 8. Dependências e Riscos

| Dependência | Risco | Mitigação |
| ----------- | ----- | --------- |
| FileWatcherApp repo externo | Divergência de contratos DXF | Documentar filas/formatos no FRD e manter notas em README + docs técnicos |
| RabbitMQ/MinIO | indisponibilidade quebra fluxo | Compose define healthchecks; backend lida com exceções e métricas de failure |
| Bibliotecas Angular/Spring | Atualizações não planejadas | Travar versões (Angular 17.3, Spring Boot via `pom.xml`) |

## 9. Roadmap / Releases

1. **MVP**: stack Compose funcional com watchers + telas principais.
2. **Fase DXF**: integração completa com DXFAnalysisWorker, MinIO e dashboards.
3. **Fase Entregas Avançadas**: filtros cursoriais, telas Delivered com paginação via API.
4. **Observabilidade**: provisionamento automático de Grafana/Prometheus e alertas.
5. **Calibração Contínua**: ajustes de scoring e backlog descritos em `docs/pending-updates.md`.

## 10. Anexos e Referências

- `docs/FRD.md` (Functional Requirements)
- `docs/SDS.md` (System Design)
- `docs/dxf-complexity-engine.md`, `docs/complexidade-facas.md`
- `../FileWatcherApp/docs/*.md`
- `README.md` atualizado
