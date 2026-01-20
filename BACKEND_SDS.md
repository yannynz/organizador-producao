# Backend SDS (Software Design Specification)

Este documento descreve o backend do projeto "organizador-producao" de ponta a ponta.
Objetivo: explicar o que cada parte faz, como faz, por que existe, e quais sao os
principais campos e variaveis. Todo o conteudo foi escrito em ASCII por padrao.

## 1. Escopo e objetivos

- Backend Spring Boot (Java 17) com PostgreSQL, RabbitMQ e WebSocket STOMP.
- Suporta pedidos (orders), importacao de OP, monitoramento de pastas e analise DXF.
- Expoe APIs REST, canais WS e integracoes AMQP.
- Usa Flyway para migracoes, JWT para seguranca, Micrometer para metricas.

## 2. Arquitetura de alto nivel

Camadas principais:

- Config: configuracoes de Spring, AMQP, CORS, auditoria e propriedades.
- Controller: endpoints REST e STOMP.
- Service: regras de negocio, integracoes e orquestracao.
- Repository: acesso a dados via JPA/Criteria.
- Model/Domain: entidades de negocio e DTOs.
- Infra/Security: JWT, autenticacao e auditoria.
- Monitoring/Health/Jobs: metricas, healthchecks e tarefas agendadas.

## 3. Fluxos principais

### 3.1 Pedidos (Orders)

- Criacao via API ou via mensagens Rabbit (FileWatcher).
- Atualizacoes manualmente via API, WS ou automacoes.
- Status e prioridade podem ser ajustados automaticamente.
- Historico de mudancas gravado em `order_history`.

### 3.2 Importacao de OP

- Mensagens chegam em `op.imported` (RabbitMQ).
- `OpImportService` persiste `op_import`, aplica flags e sincroniza com `orders`.
- Reconciliacao periodica garante link entre OP e pedido quando criado depois.

### 3.3 FileWatcher (pastas)

- Fila `laser_notifications`: cria/atualiza pedidos a partir de nomes de arquivos.
- Fila `facas_notifications`: atualiza status para cortado/pronto.
- Fila `dobra_notifications`: atualiza status para tirada.
- Eventos de "destacador" alimentam observacao do pedido.

### 3.4 Analise DXF

- API publica request em fila `facas.analysis.request`.
- Listener consome resultados em `facas.analysis.result`.
- Persistencia e broadcast via WebSocket.

### 3.5 Ping RPC

- Backend envia ping via RabbitMQ para FileWatcher.
- Healthcheck e WS usam este ping para status online/offline.

## 4. Modelo de dados (entidades principais)

Entidades e relacionamentos:

- `Order` (orders)
  - Referencias: `Cliente`, `Transportadora`, `ClienteEndereco`.
  - Auditoria: `createdBy`, `updatedBy`.
- `OpImport` (op_import)
  - Link opcional para `Order` via `facaId` (id do pedido).
  - Flags e locks manuais para sincronizacao.
- `Cliente` (clientes)
  - JSONB `apelidos`, pode ter `Transportadora` padrao.
  - Muitos `ClienteEndereco`.
- `Transportadora` (transportadoras)
  - JSONB `apelidos`.
- `ClienteEndereco` (cliente_enderecos)
  - Enderecos por cliente e flags (default, manual_lock).
- `DXFAnalysis` (dxf_analysis)
  - Resultado de analise e metadados de imagem.
- `User` (users) e `PasswordResetToken` (password_reset_tokens)
  - Base de autenticacao.
- `OrderHistory` (order_history)
  - Auditoria de campo.

## 5. Superficie de API

### REST (principais)

- `POST /api/auth/register` - cria usuario e retorna JWT.
- `POST /api/auth/login` - autentica e retorna JWT.
- `POST /api/auth/forgot-password` - inicia fluxo de reset.
- `GET /api/auth/validate-token` - valida token de reset.
- `POST /api/auth/reset-password` - redefine senha.
- `GET /api/users/assignable` - lista usuarios atribuiveis.
- `GET /api/users` - lista usuarios (ADMIN).
- `GET /api/users/me` - dados do usuario logado.
- `GET /api/users/{id}` - usuario por id (ADMIN).
- `POST /api/users` - cria usuario (ADMIN).
- `PUT /api/users/{id}` - atualiza usuario (ADMIN).
- `DELETE /api/users/{id}` - remove usuario (ADMIN).

- `GET /api/orders` - lista pedidos.
- `GET /api/orders/{id}` - pedido por id.
- `GET /api/orders/nr/{nr}` - pedido por NR.
- `POST /api/orders/create` - cria pedido.
- `PUT /api/orders/update/{id}` - atualiza pedido.
- `DELETE /api/orders/delete/{id}` - remove pedido.
- `PUT /api/orders/{id}/status` - atualiza status + dados.
- `PATCH /api/orders/{id}/priority` - altera prioridade e envia comando Rabbit.
- `GET /api/orders/{id}/history` - historico de alteracoes.
- `POST /api/orders/search-cursor` - busca com cursor.

- `GET /api/clientes` - busca paginada.
- `GET /api/clientes/{id}` - cliente por id.
- `POST /api/clientes` - cria cliente.
- `PATCH /api/clientes/{id}` - atualiza cliente.

- `GET /api/transportadoras` - busca paginada.
- `GET /api/transportadoras/{id}` - transportadora por id.
- `POST /api/transportadoras` - cria transportadora.
- `PATCH /api/transportadoras/{id}` - atualiza transportadora.

- `POST /api/ops/import` - importa OP.
- `PATCH /api/ops/{id}/vincular-faca/{facaId}` - vincula OP ao pedido.
- `GET /api/ops/{nr}` - consulta OP por numero.

- `GET /api/dxf-analysis/order/{orderNr}` - ultima analise por pedido.
- `GET /api/dxf-analysis/order/{orderNr}/history` - historico de analises.
- `GET /api/dxf-analysis/{analysisId}` - analise por id.
- `GET /api/dxf-analysis/{analysisId}/image` - imagem (redirect).
- `POST /api/dxf-analysis/request` - solicita analise DXF.

### WebSocket (STOMP)

- Endpoint STOMP: `/ws/orders`.
- App destinations:
  - `/app/orders` -> lista pedidos (topic `/topic/orders`).
  - `/app/orders/{id}` -> pedido por id.
  - `/app/orders/create` -> cria pedido via WS.
  - `/app/orders/update` -> atualiza pedido via WS.
  - `/app/orders/delete/{id}` -> remove pedido via WS.
  - `/app/prioridades` -> publica lista de prioridades.
  - `/app/prioridades/update` -> atualiza prioridade via WS.
  - `/app/status/ping-now` -> publica status do FileWatcher.
- Topics:
  - `/topic/orders`, `/topic/prioridades`, `/topic/status`, `/topic/dxf-analysis`.

### Status real (implementado no codigo)

- Auth: endpoints REST OK (sem dependencias externas).
- Orders REST: OK; `/priority` publica comando Rabbit em `file_commands`, `/status` pode retornar 403 se usuario estiver com entrega ativa.
- Orders WS: OK; destinos `/app/orders/*` e `/app/prioridades/*` operam via STOMP.
- Users: `/assignable` e `/me` requerem autenticacao; demais `/api/users/*` exigem ADMIN.
- Clientes/Transportadoras: OK (CRUD via JPA).
- OPs: OK (usa `op_import` e relacionamento com orders).
- DXF REST: GETs OK se houver dados em `dxf_analysis`; `/request` depende Rabbit (`facas.analysis.request`); `/image` depende `app.dxf.analysis.imageBaseUrl`.
- Status WS: `/app/status/ping-now` depende RPC `filewatcher.rpc.ping`.

### Status validado em ambiente local (2026-01-15)

Ambiente e premissas:

- Docker Compose ativo (backend + dependencias) e FileWatcherApp via dotnet.
- Pastas monitoradas no teste: `tmp/laser`, `tmp/laser/FACASOK`, `tmp/dobras`, `tmp/nr`.
- API acessada diretamente em `http://localhost:8081`.

Validacoes realizadas:

- Auth: `POST /api/auth/register` e `POST /api/auth/login` retornaram JWT; `GET /api/users/me` OK.
- Orders via Rabbit: CNC em `tmp/laser` criou pedido `NR=120184` (status 0); CNC em `tmp/laser/FACASOK` atualizou para status 1; DXF em `tmp/dobras` (".m.DXF") atualizou para status 6.
- Orders REST: `PATCH /api/orders/{id}/priority` atualizou prioridade e criou historico; `PUT /api/orders/{id}/status` atualizou status; `POST /api/orders/create` e `DELETE /api/orders/delete/{id}` OK.
- OP import: PDF em `tmp/nr` publicou `op.imported`; `GET /api/ops/120432` retornou registro persistido.
- Clientes/Transportadoras: `POST /api/transportadoras` e `POST /api/clientes` OK; `GET` com `search` retornou resultados.

Observacoes e gaps detectados:

- DXF: FileWatcherApp publicou requests de analise para arquivos `.CNC`, gerando falha "DXF file version not supported: Unknown"; novos `.dxf` em `tmp/laser` e `tmp/laser/FACASOK` nao geraram novos registros em `dxf_analysis` neste teste.
- WebSocket STOMP nao exercitado via cliente.
- Fluxo de email (forgot/reset password) nao testado.

## 6. Integracoes AMQP (RabbitMQ)

- `laser_notifications`: cria/atualiza pedidos via FileWatcher.
- `facas_notifications`: atualiza status para cortado/pronto.
- `dobra_notifications`: atualiza status para tirada.
- `op.imported` (exchange `op.exchange`): importa OPs.
- `file_commands`: comandos para servico C# (ex: renomear prioridade).
- RPC `filewatcher.rpc.ping`: ping/pong para health e status.
- `facas.analysis.request`: solicita analise DXF.
- `facas.analysis.result`: resultados de analise DXF.

## 7. Agendamentos

- FileWatcher ping: a cada 10s.
- Atualizacao de prioridades: a cada 60s (comentario diz 10min, mas o codigo esta em 60s).
- Reconciliacao de OPs sem faca: a cada 50s.

## 8. Observabilidade

- `HttpRequestMetricsFilter`: latencia e volume HTTP por endpoint.
- `MessageProcessingMetrics`: tempo de processamento por fila.
- `WebSocketMetrics`: conexoes WS ativas.
- Exportacao Prometheus via Actuator.

## 9. Seguranca

- JWT via header `Authorization: Bearer <token>`.
- `SecurityConfiguration` define rotas abertas e protegidas.
- Roles: ADMIN, DESENHISTA, OPERADOR, ENTREGADOR.
- Auditoria: `createdBy`, `updatedBy` em `Order`.

## 10. Configuracoes (application.properties)

Grupos principais:

- Banco: `spring.datasource.*`, `spring.jpa.*`.
- RabbitMQ: `spring.rabbitmq.*`, `spring.rabbitmq.template.*`.
- Flyway: `spring.flyway.*`.
- DXF: `app.dxf.analysis.*`.
- RPC FileWatcher: `app.rpc.filewatcher.*`.
- CORS: `app.cors.allowed-origins`.
- JWT: `security.jwt.*`.
- Email: `spring.mail.*`.
- Frontend: `app.frontend.url`.
- Metricas: `management.*`.

## 11. Migracoes Flyway (resumo)

- `V20250900__create_orders.sql`: cria `orders`.
- `V20250901__add_orders_emborrachada.sql`: adiciona `emborrachada`.
- `V20250902__add_orders_emborrachada.sql`: no-op comentado.
- `V20250903__add_orders_data_cortada_tirada.sql`: no-op comentado.
- `V20250905__add_orders_data_cortada_tirada.sql`: adiciona `data_cortada`, `data_tirada`.
- `V20250906__orders_extra_fields.sql`: extras em orders/op_import.
- `V20250914__add_orders_extra_fields.sql`: no-op comentado.
- `V20250915__order_extras.sql`: materiais especiais em orders.
- `V20250916__op_import_extras.sql`: cria `op_import` + extras.
- `V20250917__order_defaults.sql`: defaults e normalizacao.
- `V20250918__op_import_manual_locks.sql`: locks manuais.
- `V20250919__add_orders_vincador.sql`: `vincador` e `data_vinco`.
- `V20250926__add_vinco_flags.sql`: `vai_vinco` + lock.
- `V20251020__create_dxf_analysis.sql`: cria `dxf_analysis`.
- `V20251028__augment_dxf_analysis_storage.sql`: campos de storage.
- `V20251127__create_client_transportadora_tables.sql`: clientes/transportadoras/enderecos + links.
- `V20251127_1__enable_unaccent.sql`: extensao `unaccent`.
- `V20251130__add_cliente_horario_funcionamento.sql`: horario funcionamento.
- `V20251130_2__add_cep_to_cliente_enderecos.sql`: `cep`.
- `V20251130_3__add_cnpj_cpf_to_clientes.sql`: `cnpj_cpf`.
- `V20251130_4__add_inscricao_estadual_to_clientes.sql`: `inscricao_estadual`.
- `V20251130_5__add_telefone_to_clientes.sql`: `telefone`.
- `V20251130_6__add_email_contato_to_clientes.sql`: `email_contato`.
- `V20251201__create_auth_tables.sql`: `users` + auditoria orders.
- `V20251203__create_password_reset_tokens.sql`: tokens de reset.
- `V20251215__create_order_history.sql`: historico de pedidos.
- `V20251218__convert_apelidos_to_jsonb.sql`: converte apelidos.
- `V20251222__fix_apelidos_jsonb.sql`: corrige jsonb se necessario.

## 12. Catalogo detalhado de arquivos (arquivo por arquivo)

### Entry point

`src/main/java/git/yannynz/organizadorproducao/OrganizadorProducao.java`
- O que faz: inicia o Spring Boot e imprime mensagem inicial.
- Como faz: `SpringApplication.run` + `CommandLineRunner`.
- Por que existe: bootstrap da aplicacao.
- Campos/variaveis: nenhum campo persistente.

### Configuracao

`src/main/java/git/yannynz/organizadorproducao/config/DXFAnalysisProperties.java`
- O que faz: propriedades de integracao DXF.
- Como faz: `@ConfigurationProperties(prefix="app.dxf.analysis")`.
- Por que existe: centralizar nomes de fila, topico e regex.
- Campos: `requestQueue`, `resultQueue`, `websocketTopic`, `imageBaseUrl`, `imageLocalRoots`, `orderNumberPattern`.

`src/main/java/git/yannynz/organizadorproducao/config/WebSocketConfig.java`
- O que faz: configura STOMP broker e endpoint.
- Como faz: `configureMessageBroker`, `registerStompEndpoints`.
- Por que existe: ativar WS para updates em tempo real.
- Campos: `allowedOrigins`.

`src/main/java/git/yannynz/organizadorproducao/config/MyWebSocketHandler.java`
- O que faz: handler de echo para WS simples.
- Como faz: `handleTextMessage`.
- Por que existe: teste/debug de WS.
- Campos: nenhum.

`src/main/java/git/yannynz/organizadorproducao/config/WebConfig.java`
- O que faz: CORS para `/api/**`.
- Como faz: `addCorsMappings`.
- Por que existe: permitir frontend acessar API.
- Campos: `allowedOrigins`.

`src/main/java/git/yannynz/organizadorproducao/config/RabbitMQConfig.java`
- O que faz: declara filas base.
- Como faz: beans `Queue`.
- Por que existe: garantir filas no startup.
- Campos: constantes de nomes de fila.

`src/main/java/git/yannynz/organizadorproducao/config/AmqpConfig.java`
- O que faz: exchange/queue/binding para OP.
- Como faz: beans `TopicExchange`, `Queue`, `Binding`.
- Por que existe: rota `op.imported`.
- Campos: none na config; listener `OpImportedListener` com `service`, `processingMetrics`, `mapper`, `log`.

`src/main/java/git/yannynz/organizadorproducao/config/RabbitRpcConfig.java`
- O que faz: configura RabbitTemplate para RPC.
- Como faz: `RabbitTemplate` com reply timeout e fila.
- Por que existe: ping/pong do FileWatcher.
- Campos: `rpcQueueName`, `replyTimeoutMs`.

`src/main/java/git/yannynz/organizadorproducao/config/RabbitListenerStringConfig.java`
- O que faz: container factory para `String` em listeners.
- Como faz: `SimpleMessageConverter`.
- Por que existe: facilitar parse manual de JSON.
- Campos: none.

`src/main/java/git/yannynz/organizadorproducao/config/AsyncConfig.java`
- O que faz: executor assinc e enable scheduling.
- Como faz: `ThreadPoolTaskExecutor`.
- Por que existe: tarefas async de OP e agendamentos.
- Campos: executor `opExecutor`.

`src/main/java/git/yannynz/organizadorproducao/config/FileWatcherRpcStub.java`
- O que faz: stub de resposta RPC (dev).
- Como faz: `@RabbitListener` e retorna `FileWatcherPong`.
- Por que existe: permitir testes sem FileWatcher real.
- Campos: `instanceId`, `metricsQueue`, `messageProcessingMetrics`.

`src/main/java/git/yannynz/organizadorproducao/config/JacksonConfig.java`
- O que faz: adiciona suporte a proxies Hibernate.
- Como faz: bean `Hibernate6Module`.
- Por que existe: evitar erros de serializacao.
- Campos: none.

`src/main/java/git/yannynz/organizadorproducao/config/JpaAuditingConfig.java`
- O que faz: habilita auditoria JPA.
- Como faz: `@EnableJpaAuditing`.
- Por que existe: preencher `createdBy/updatedBy`.
- Campos: bean `auditorProvider`.

`src/main/java/git/yannynz/organizadorproducao/config/pagination/CursorStrategy.java`
- O que faz: define estrategias de cursor.
- Como faz: enum.
- Por que existe: trade-off entre performance e ordenacao.
- Campos: `ID`, `DATE_ID`.

`src/main/java/git/yannynz/organizadorproducao/config/pagination/CursorPaging.java`
- O que faz: encode/decode de cursor.
- Como faz: JSON + Base64.
- Por que existe: paginacao stateless.
- Campos: `Key`, `PageEnvelope`, `MAPPER`.

### Monitoring, Health, Jobs

`src/main/java/git/yannynz/organizadorproducao/monitoring/HttpRequestMetricsFilter.java`
- O que faz: metricas HTTP por endpoint.
- Como faz: timers e counters por tags.
- Por que existe: monitorar latencia e erros.
- Campos: caches de `Timer` e `Counter`, nomes de metricas.

`src/main/java/git/yannynz/organizadorproducao/monitoring/WebSocketMetrics.java`
- O que faz: gauge de sessoes WS.
- Como faz: eventos de connect/disconnect.
- Por que existe: visibilidade de uso WS.
- Campos: `activeSessions`, `sessions`.

`src/main/java/git/yannynz/organizadorproducao/monitoring/MessageProcessingMetrics.java`
- O que faz: tempo de processamento por fila.
- Como faz: `Timer.Sample` com cache.
- Por que existe: observabilidade de consumo AMQP.
- Campos: `timers`, interfaces `ThrowingRunnable` e `ThrowingSupplier`.

`src/main/java/git/yannynz/organizadorproducao/health/FileWatcherHealthIndicator.java`
- O que faz: healthcheck baseado em ping.
- Como faz: `Health.up/down`.
- Por que existe: integracao Actuator.
- Campos: `client`.

`src/main/java/git/yannynz/organizadorproducao/jobs/FileWatcherPingScheduler.java`
- O que faz: ping periodico + broadcast via WS.
- Como faz: `@Scheduled`.
- Por que existe: status continuo do FileWatcher.
- Campos: `client`, `publisher`.

### Dominio e entidades

`src/main/java/git/yannynz/organizadorproducao/domain/user/UserRole.java`
- O que faz: enum de perfis.
- Como faz: constantes.
- Por que existe: controle de acesso.
- Campos: `ADMIN`, `DESENHISTA`, `OPERADOR`, `ENTREGADOR`.

`src/main/java/git/yannynz/organizadorproducao/domain/user/User.java`
- O que faz: entidade de usuario.
- Como faz: JPA + `UserDetails`.
- Por que existe: autenticacao JWT.
- Campos: `id`, `name`, `email`, `password`, `role`, `active`.

`src/main/java/git/yannynz/organizadorproducao/domain/user/PasswordResetToken.java`
- O que faz: token de reset.
- Como faz: JPA com `User`.
- Por que existe: recuperacao de senha.
- Campos: `id`, `token`, `user`, `expiryDate`, `createdAt`.

`src/main/java/git/yannynz/organizadorproducao/domain/user/UserRepository.java`
- O que faz: CRUD de usuarios.
- Como faz: `JpaRepository`.
- Por que existe: lookup para auth.
- Campos: metodos `findByEmail`, `findByActiveTrueAndRoleIn`.

`src/main/java/git/yannynz/organizadorproducao/domain/user/PasswordResetTokenRepository.java`
- O que faz: CRUD de tokens.
- Como faz: `JpaRepository`.
- Por que existe: validar e remover tokens.
- Campos: metodos `findByToken`, `deleteByToken`.

`src/main/java/git/yannynz/organizadorproducao/model/Order.java`
- O que faz: entidade principal do negocio.
- Como faz: JPA + auditoria.
- Por que existe: representar pedido e seu fluxo.
- Campos: ver secao 4.

`src/main/java/git/yannynz/organizadorproducao/model/OpImport.java`
- O que faz: entidade para OPs importadas.
- Como faz: JPA com JSONB e locks.
- Por que existe: sincronizar sistema externo.
- Campos: ver secao 4.

`src/main/java/git/yannynz/organizadorproducao/model/Cliente.java`
- O que faz: cadastro de clientes.
- Como faz: JPA + JSONB apelidos.
- Por que existe: base de enderecos e entrega.
- Campos: ver secao 4.

`src/main/java/git/yannynz/organizadorproducao/model/ClienteEndereco.java`
- O que faz: enderecos de clientes.
- Como faz: JPA com vinculo a cliente.
- Por que existe: logistica de entrega.
- Campos: ver secao 4.

`src/main/java/git/yannynz/organizadorproducao/model/Transportadora.java`
- O que faz: cadastro de transportadoras.
- Como faz: JPA + JSONB apelidos.
- Por que existe: padrao de entrega.
- Campos: ver secao 4.

`src/main/java/git/yannynz/organizadorproducao/model/DXFAnalysis.java`
- O que faz: entidade de analise DXF.
- Como faz: JPA + JSONB.
- Por que existe: historico e dados de imagem.
- Campos: ver secao 4.

`src/main/java/git/yannynz/organizadorproducao/model/OrderHistory.java`
- O que faz: historico de alteracoes.
- Como faz: JPA com `order` e `user`.
- Por que existe: auditoria.
- Campos: `id`, `order`, `user`, `timestamp`, `fieldName`, `oldValue`, `newValue`.

`src/main/java/git/yannynz/organizadorproducao/model/FileInfo.java`
- O que faz: modelo simples de arquivo.
- Como faz: POJO.
- Por que existe: utilitario.
- Campos: `fileName`, `path`, `timestamp`.

### DTOs

`src/main/java/git/yannynz/organizadorproducao/model/dto/FileWatcherPong.java`
- O que faz: resposta de ping.
- Como faz: POJO com `@JsonInclude`.
- Por que existe: padronizar RPC.
- Campos: `ok`, `instanceId`, `ts`, `version`.

`src/main/java/git/yannynz/organizadorproducao/model/dto/StatusEvent.java`
- O que faz: evento de status para WS.
- Como faz: `record`.
- Por que existe: payload consistente.
- Campos: `kind`, `online`, `latencyMs`, `lastChecked`, `lastSeenTs`, `instanceId`, `version`, `source`.

`src/main/java/git/yannynz/organizadorproducao/model/dto/AssignableUserDTO.java`
- O que faz: usuario atribuivel.
- Como faz: DTO simples.
- Por que existe: reduzir payload na UI.
- Campos: `id`, `name`, `role`.

`src/main/java/git/yannynz/organizadorproducao/model/dto/DXFAnalysisView.java`
- O que faz: view da analise DXF.
- Como faz: `record`.
- Por que existe: retorno de API.
- Campos: ver secao 4 (subset de `DXFAnalysis`).

`src/main/java/git/yannynz/organizadorproducao/model/dto/DXFAnalysisRequestDTO.java`
- O que faz: request de analise DXF.
- Como faz: `record` com `@NotBlank`.
- Por que existe: API de solicitacao.
- Campos: `filePath`, `fileName`, `fileHash`, `orderNumber`, `forceReprocess`, `shadowMode`.

`src/main/java/git/yannynz/organizadorproducao/model/dto/DXFAnalysisRequestResponse.java`
- O que faz: resposta de request DXF.
- Como faz: `record`.
- Por que existe: retornar `analysisId`.
- Campos: `analysisId`, `orderNumber`.

`src/main/java/git/yannynz/organizadorproducao/model/dto/FileCommandDTO.java`
- O que faz: comando de arquivo para Rabbit.
- Como faz: Lombok DTO.
- Por que existe: integrar com servico C#.
- Campos: `action`, `nr`, `newPriority`, `directory`.

`src/main/java/git/yannynz/organizadorproducao/model/dto/OpImportRequestDTO.java`
- O que faz: payload de importacao de OP.
- Como faz: DTO com campos de produto e sugestoes.
- Por que existe: integrar OP externa e enriquecer cliente.
- Campos: ver secao 4.

`src/main/java/git/yannynz/organizadorproducao/model/dto/OrderSearchDTO.java`
- O que faz: filtros de busca.
- Como faz: campos + `Range`.
- Por que existe: consultas flexiveis.
- Campos: `q`, `nr`, `cliente`, `prioridade`, `status`, `statusIn`, `entregador`, `observacao`, `veiculo`, `recebedor`, `montador`, `emborrachador`, `dataH`, `dataEntrega`, `dataHRetorno`, `dataMontagem`, `dataEmborrachamento`, `Range.from`, `Range.to`.

`src/main/java/git/yannynz/organizadorproducao/model/dto/EnderecoSugeridoDTO.java`
- O que faz: endereco sugerido por OP.
- Como faz: DTO simples.
- Por que existe: enriquecer cadastro.
- Campos: `uf`, `cidade`, `bairro`, `logradouro`, `cep`, `horarioFuncionamento`, `padraoEntrega`.

### Controllers

`src/main/java/git/yannynz/organizadorproducao/controller/AuthenticationController.java`
- O que faz: auth/register/reset.
- Como faz: delega a `AuthenticationService`.
- Por que existe: endpoints de login/recuperacao.
- Campos: `service`.

`src/main/java/git/yannynz/organizadorproducao/controller/UserController.java`
- O que faz: CRUD usuarios + atribuiveis.
- Como faz: `UserService`.
- Por que existe: administracao e atribuicao.
- Campos: `service`.

`src/main/java/git/yannynz/organizadorproducao/controller/OrderController.java`
- O que faz: CRUD pedidos, patch prioridade, search cursor.
- Como faz: `OrderService`, `OrderHistoryService`, `OpImportService`, `FileCommandPublisher`.
- Por que existe: API principal de pedidos.
- Campos: `orderService`, `fileCommandPublisher`, `orderHistoryService`, `opImportService`.

`src/main/java/git/yannynz/organizadorproducao/controller/OrderWebSocketController.java`
- O que faz: endpoints STOMP para pedidos.
- Como faz: `@MessageMapping` e `@SendTo`.
- Por que existe: atualizacao em tempo real.
- Campos: `orderService`, `messagingTemplate`.

`src/main/java/git/yannynz/organizadorproducao/controller/PriorityWebSocketController.java`
- O que faz: WS para prioridades.
- Como faz: publica lista e updates.
- Por que existe: painel de prioridades.
- Campos: `orderService`, `messagingTemplate`.

`src/main/java/git/yannynz/organizadorproducao/controller/StatusWsController.java`
- O que faz: ping imediato via WS.
- Como faz: `FileWatcherPingClient` + `StatusWsPublisher`.
- Por que existe: status sob demanda.
- Campos: `pingClient`, `publisher`.

`src/main/java/git/yannynz/organizadorproducao/controller/ClienteController.java`
- O que faz: CRUD parcial de clientes.
- Como faz: `ClienteService`.
- Por que existe: gerenciamento de clientes.
- Campos: `service`.

`src/main/java/git/yannynz/organizadorproducao/controller/TransportadoraController.java`
- O que faz: CRUD parcial de transportadoras.
- Como faz: `TransportadoraService`.
- Por que existe: gerenciamento logistico.
- Campos: `service`.

`src/main/java/git/yannynz/organizadorproducao/controller/OpImportController.java`
- O que faz: importacao de OP e vinculo.
- Como faz: `OpImportService` e `OpImportRepository`.
- Por que existe: integrar sistema externo.
- Campos: `service`, `repo`.

`src/main/java/git/yannynz/organizadorproducao/controller/DXFAnalysisController.java`
- O que faz: API de analise DXF.
- Como faz: `DXFAnalysisService` e publisher.
- Por que existe: consulta e request de analise.
- Campos: `analysisService`, `requestPublisher`.

`src/main/java/git/yannynz/organizadorproducao/controller/auth/AuthenticationRequest.java`
- O que faz: payload de login.
- Como faz: DTO Lombok.
- Por que existe: request padrao.
- Campos: `email`, `password`.

`src/main/java/git/yannynz/organizadorproducao/controller/auth/AuthenticationResponse.java`
- O que faz: resposta com JWT.
- Como faz: DTO Lombok.
- Por que existe: retorno padrao.
- Campos: `token`.

`src/main/java/git/yannynz/organizadorproducao/controller/auth/RegisterRequest.java`
- O que faz: payload de registro.
- Como faz: DTO Lombok.
- Por que existe: criar usuario.
- Campos: `name`, `email`, `password`.

`src/main/java/git/yannynz/organizadorproducao/controller/auth/ForgotPasswordRequest.java`
- O que faz: payload de reset.
- Como faz: DTO Lombok.
- Por que existe: iniciar fluxo de reset.
- Campos: `email`.

`src/main/java/git/yannynz/organizadorproducao/controller/auth/ResetPasswordRequest.java`
- O que faz: payload de nova senha.
- Como faz: DTO Lombok.
- Por que existe: finalizar reset.
- Campos: `token`, `newPassword`.

### Services

`src/main/java/git/yannynz/organizadorproducao/service/AuthenticationService.java`
- O que faz: registro, login, reset.
- Como faz: `AuthenticationManager`, `JwtService`, `PasswordResetTokenRepository`.
- Por que existe: consolidar regras de auth.
- Campos: `repository`, `tokenRepository`, `jwtService`, `authenticationManager`, `emailService`, `passwordEncoder`.

`src/main/java/git/yannynz/organizadorproducao/service/UserService.java`
- O que faz: CRUD usuarios e atribuiveis.
- Como faz: `UserRepository`, `PasswordEncoder`.
- Por que existe: regras de atribuicao por role.
- Campos: `repository`, `passwordEncoder`.

`src/main/java/git/yannynz/organizadorproducao/service/ClienteService.java`
- O que faz: CRUD clientes.
- Como faz: normaliza nome e vincula transportadora.
- Por que existe: consistencia de cadastro.
- Campos: `repo`, `transportadoraRepo`.

`src/main/java/git/yannynz/organizadorproducao/service/TransportadoraService.java`
- O que faz: CRUD transportadoras.
- Como faz: normaliza nome.
- Por que existe: consistencia.
- Campos: `repo`.

`src/main/java/git/yannynz/organizadorproducao/service/ClienteAutoEnrichmentService.java`
- O que faz: cria/atualiza cliente/endereco via OP.
- Como faz: normaliza, compara, salva e linka.
- Por que existe: reduzir trabalho manual.
- Campos: `clienteRepo`, `enderecoRepo`, `log`.

`src/main/java/git/yannynz/organizadorproducao/service/OrderHistoryService.java`
- O que faz: grava historico de mudancas.
- Como faz: le usuario do contexto de seguranca.
- Por que existe: auditoria.
- Campos: `repo`, `userRepo`.

`src/main/java/git/yannynz/organizadorproducao/service/OrderStatusRules.java`
- O que faz: regras automaticas de status.
- Como faz: valida `montagem` e `vinco`.
- Por que existe: reduzir updates manuais.
- Campos: constantes de status.

`src/main/java/git/yannynz/organizadorproducao/service/OrderService.java`
- O que faz: CRUD de pedidos e job de prioridade.
- Como faz: `OrderRepository` + `SimpMessagingTemplate`.
- Por que existe: centralizar regras de pedidos.
- Campos: `orderRepository`, `messagingTemplate`, `opImportService`.

`src/main/java/git/yannynz/organizadorproducao/service/SearchResult.java`
- O que faz: envelope de busca.
- Como faz: `record`.
- Por que existe: retorno padrao do cursor.
- Campos: `items`, `hasMore`, `lastKey`.

`src/main/java/git/yannynz/organizadorproducao/service/OpImportService.java`
- O que faz: importa OP e sincroniza pedidos.
- Como faz: parse de datas, flags, locks, link e reconcile.
- Por que existe: integracao com OP externa.
- Campos: `repo`, `orderRepo`, `mapper`, `ws`, `log`, `clienteAuto`.

`src/main/java/git/yannynz/organizadorproducao/service/FileWatcherPingClient.java`
- O que faz: ping via Rabbit RPC.
- Como faz: `convertSendAndReceive`.
- Por que existe: health e status em tempo real.
- Campos: `rabbitTemplate`, `rpcQueue`, `timeoutMs`.

`src/main/java/git/yannynz/organizadorproducao/service/FileWatcherService.java`
- O que faz: processa filas laser/facas.
- Como faz: regex no `file_name`, cria/atualiza pedidos.
- Por que existe: integrar eventos de arquivos.
- Campos: `messagingTemplate`, `orderRepository`, `destacadorMonitorService`, `messageProcessingMetrics`.

`src/main/java/git/yannynz/organizadorproducao/service/DXFAnalysisRequestPublisher.java`
- O que faz: publica request DXF.
- Como faz: monta JSON e envia para fila.
- Por que existe: integrar motor DXF.
- Campos: `rabbitTemplate`, `objectMapper`, `properties`, `orderPattern`.

`src/main/java/git/yannynz/organizadorproducao/service/DXFAnalysisService.java`
- O que faz: persiste e serve analises DXF.
- Como faz: normaliza payload, salva e publica WS.
- Por que existe: historico e UI.
- Campos: repositorios, `meterRegistry`, contadores e timers.

`src/main/java/git/yannynz/organizadorproducao/service/DXFAnalysisResultListener.java`
- O que faz: consome resultados DXF.
- Como faz: parse JSON e chama `persistFromPayload`.
- Por que existe: ingestao automatica.
- Campos: `analysisService`, `messageProcessingMetrics`, `objectMapper`, `properties`.

`src/main/java/git/yannynz/organizadorproducao/service/DestacadorMonitorService.java`
- O que faz: registra destaque M/F na observacao.
- Como faz: regex e manipulacao de linhas.
- Por que existe: rastrear corte de destacadores.
- Campos: `orderRepository`, `messagingTemplate`, patterns de sexo.

`src/main/java/git/yannynz/organizadorproducao/service/DobrasFileService.java`
- O que faz: atualiza status para tirada via fila dobras.
- Como faz: valida sufixo e extrai NR.
- Por que existe: integrar etapa de dobras.
- Campos: `objectMapper`, `orderRepository`, `messagingTemplate`, `messageProcessingMetrics`.

`src/main/java/git/yannynz/organizadorproducao/service/EmailService.java`
- O que faz: envia email de reset.
- Como faz: `JavaMailSender` + `SimpleMailMessage`.
- Por que existe: recuperar senha.
- Campos: `mailSender`, `fromEmail`, `frontendUrl`.

`src/main/java/git/yannynz/organizadorproducao/service/FileCommandPublisher.java`
- O que faz: envia comandos para `file_commands`.
- Como faz: serializa `FileCommandDTO`.
- Por que existe: integrar com servico C#.
- Campos: `rabbitTemplate`, `objectMapper`, `log`.

`src/main/java/git/yannynz/organizadorproducao/service/StatusWsPublisher.java`
- O que faz: publica `StatusEvent` no WS.
- Como faz: `SimpMessagingTemplate`.
- Por que existe: canal central de status.
- Campos: `template`.

`src/main/java/git/yannynz/organizadorproducao/service/WebSocketMessage.java`
- O que faz: envelope de mensagens WS.
- Como faz: classe simples `action` + `data`.
- Por que existe: padronizar payloads WS.
- Campos: `action`, `data`.

### Repositories

`src/main/java/git/yannynz/organizadorproducao/repository/OrderRepository.java`
- O que faz: acesso a pedidos.
- Como faz: `JpaRepository` + metodos derivados.
- Por que existe: consultas basicas.
- Campos: metodos `findByNr`, `findByStatusIn`, `findTopByNrOrderByIdDesc`, `findByNrOrderByIdDesc`, `findByEntregadorAndStatus`.

`src/main/java/git/yannynz/organizadorproducao/repository/OrderRepositoryCustom.java`
- O que faz: define busca cursor.
- Como faz: assinatura de metodo custom.
- Por que existe: paginacao eficiente.
- Campos: `searchDeliveredByCursor`.

`src/main/java/git/yannynz/organizadorproducao/repository/OrderRepositoryImpl.java`
- O que faz: implementa busca cursor.
- Como faz: Criteria API com keyset.
- Por que existe: evitar offset.
- Campos: `em` (EntityManager).

`src/main/java/git/yannynz/organizadorproducao/repository/ClienteRepository.java`
- O que faz: acesso a clientes.
- Como faz: query nativa com `unaccent`.
- Por que existe: busca tolerante.
- Campos: `findByNomeNormalizado`, `search`.

`src/main/java/git/yannynz/organizadorproducao/repository/ClienteEnderecoRepository.java`
- O que faz: acesso a enderecos.
- Como faz: metodos derivados.
- Por que existe: lookup por cliente.
- Campos: `findByClienteId`, `findByClienteIdAndIsDefaultTrue`.

`src/main/java/git/yannynz/organizadorproducao/repository/TransportadoraRepository.java`
- O que faz: acesso a transportadoras.
- Como faz: query nativa com `unaccent`.
- Por que existe: busca tolerante.
- Campos: `findByNomeNormalizado`, `search`.

`src/main/java/git/yannynz/organizadorproducao/repository/OpImportRepository.java`
- O que faz: acesso a OP importada.
- Como faz: metodos derivados.
- Por que existe: localizar OP por numero ou faca.
- Campos: `findByNumeroOp`, `findTopByFacaIdOrderByCreatedAtDesc`.

`src/main/java/git/yannynz/organizadorproducao/repository/DXFAnalysisRepository.java`
- O que faz: acesso a analises DXF.
- Como faz: metodos derivados e paginacao.
- Por que existe: historico e latest.
- Campos: `findByAnalysisId`, `findTopByOrderNrOrderByAnalyzedAtDesc`, `findTop5ByOrderNrOrderByAnalyzedAtDesc`, `findByOrderNrOrderByAnalyzedAtDesc`.

`src/main/java/git/yannynz/organizadorproducao/repository/OrderHistoryRepository.java`
- O que faz: acesso a historico de pedidos.
- Como faz: metodo derivado.
- Por que existe: auditoria.
- Campos: `findByOrderIdOrderByTimestampDesc`.

### Security (infra)

`src/main/java/git/yannynz/organizadorproducao/infra/security/JwtService.java`
- O que faz: gera e valida JWT.
- Como faz: JJWT + secret key Base64.
- Por que existe: auth stateless.
- Campos: `secretKey`, `jwtExpiration`.

`src/main/java/git/yannynz/organizadorproducao/infra/security/JwtAuthenticationFilter.java`
- O que faz: valida JWT por request.
- Como faz: extrai header, valida token, seta SecurityContext.
- Por que existe: proteger endpoints.
- Campos: `jwtService`, `userDetailsService`.

`src/main/java/git/yannynz/organizadorproducao/infra/security/SecurityConfiguration.java`
- O que faz: define regras de acesso.
- Como faz: `SecurityFilterChain`.
- Por que existe: restricao por role e JWT.
- Campos: `jwtAuthFilter`, `authenticationProvider`.

`src/main/java/git/yannynz/organizadorproducao/infra/security/ApplicationSecurityConfig.java`
- O que faz: beans de autenticacao.
- Como faz: `DaoAuthenticationProvider`, `PasswordEncoder`.
- Por que existe: login com users do banco.
- Campos: `userRepository`.

`src/main/java/git/yannynz/organizadorproducao/infra/security/SecurityAuditorAware.java`
- O que faz: fornece auditor atual.
- Como faz: le `SecurityContext`.
- Por que existe: preencher `createdBy/updatedBy`.
- Campos: none (apenas metodo).

### Resources

`src/main/resources/application.properties`
- O que faz: config geral.
- Como faz: propriedades Spring e `app.*`.
- Por que existe: parametrizar ambiente.
- Campos: ver secao 10.

### Flyway

Todos os arquivos em `src/main/resources/db/migration` seguem as descricoes da secao 11.

## 13. Diagramas ASCII (fluxos principais)

### 13.1 FileWatcher -> Orders

```
FileWatcherApp
  | (JSON com file_name)
  v
RabbitMQ (laser_notifications, facas_notifications, dobra_notifications)
  | @RabbitListener
  v
FileWatcherService / DobrasFileService
  | parse nome / aplica regra
  v
OrderRepository -> PostgreSQL
  |
  v
WebSocket (/topic/orders)
```

### 13.2 Importacao de OP

```
Sistema externo
  | publish op.imported
  v
RabbitMQ (op.exchange -> op.imported)
  | OpImportedListener
  v
OpImportService
  | salva op_import
  | aplica flags e locks
  | sincroniza Order
  v
OrderRepository / OpImportRepository
  |
  v
WebSocket (/topic/orders, /topic/ops)
```

### 13.3 Analise DXF

```
Cliente REST
  | POST /api/dxf-analysis/request
  v
DXFAnalysisRequestPublisher
  | publish JSON
  v
RabbitMQ (facas.analysis.request)
  |
  v
Motor DXF externo
  | publish resultado JSON
  v
RabbitMQ (facas.analysis.result)
  |
  v
DXFAnalysisResultListener -> DXFAnalysisService -> DB
  |
  v
WebSocket (/topic/dxf-analysis)
```

### 13.4 Ping RPC FileWatcher

```
Scheduler / WS ping-now
  | pingNow()
  v
RabbitMQ RPC (filewatcher.rpc.ping)
  | FileWatcher responder
  v
FileWatcherPingClient
  | PingResult
  v
StatusWsPublisher -> /topic/status
```

## 14. Dicionario de dados (campos e significado)

### 14.1 Order (orders)

- `id`: chave primaria do pedido(identificacao para a aplicacao).
- `nr`: numero do pedido (NR/CL)(identificacao dentro da fabrica).
- `cliente`: nome livre do cliente (legado).
- `prioridade`: nivel de prioridade (VERDE/AZUL/AMARELO/VERMELHO).
- `dataH`: data/hora de criacao do pedido.
- `status`: inteiro do status do fluxo.
- `dataEntrega`: data/hora em que saiu/foi entregue.
- `entregador`: nome do entregador.
- `observacao`: texto livre; pode incluir marcacoes de destaque e tags.
- `veiculo`: identificacao do veiculo.
- `dataHRetorno`: data/hora de retorno.
- `recebedor`: pessoa que recebeu.
- `montador`: responsavel pela montagem.
- `dataMontagem`: data/hora da montagem.
- `emborrachador`: responsavel por emborrachamento.
- `dataEmborrachamento`: data/hora de emborrachamento.
- `emborrachada`: true se a faca/pedido esta emborrachado.
- `dataCortada`: data/hora em que foi cortada.
- `dataTirada`: data/hora em que foi tirada.
- `destacador`: indicador de destaque (M, F, M/F).
- `modalidadeEntrega`: "A ENTREGAR" ou "RETIRADA".
- `dataRequeridaEntrega`: prazo de entrega.
- `usuarioImportacao`: usuario do sistema externo.
- `pertinax`: material especial.
- `poliester`: material especial.
- `papelCalibrado`: material especial.
- `vaiVinco`: true se ha etapa de vinco.
- `vincador`: responsavel pelo vinco.
- `dataVinco`: data/hora do vinco.
- `clienteRef`: FK para `Cliente`.
- `transportadora`: FK para `Transportadora`.
- `endereco`: FK para `ClienteEndereco`.
- `horarioFuncAplicado`: horario de funcionamento aplicado no pedido.
- `foraHorario`: true se entrega fora do horario.
- `createdBy`: id do usuario criador (auditoria).
- `updatedBy`: id do usuario atualizador (auditoria).

### 14.2 OpImport (op_import)

- `id`: chave primaria da OP.
- `numeroOp`: numero da OP.
- `codigoProduto`: codigo do produto.
- `descricaoProduto`: descricao do produto.
- `cliente`: nome do cliente (legado no payload).
- `dataOp`: data/hora da OP.
- `emborrachada`: flag de emborrachamento.
- `sharePath`: caminho de rede compartilhado.
- `materiais`: JSONB de materiais.
- `destacador`: M, F, MF.
- `modalidadeEntrega`: "A ENTREGAR" ou "RETIRADA".
- `facaId`: id do pedido vinculado.
- `createdAt`: data/hora de criacao.
- `dataRequeridaEntrega`: prazo sugerido.
- `usuarioImportacao`: usuario do sistema externo.
- `pertinax`: material especial.
- `poliester`: material especial.
- `papelCalibrado`: material especial.
- `vaiVinco`: flag de vinco.
- `manualLockEmborrachada`: lock manual para emborrachada.
- `manualLockPertinax`: lock manual para pertinax.
- `manualLockPoliester`: lock manual para poliester.
- `manualLockPapelCalibrado`: lock manual para papel calibrado.
- `manualLockVaiVinco`: lock manual para vinco.
- `clienteRef`: FK para `Cliente`.
- `endereco`: FK para `ClienteEndereco`.

### 14.3 Cliente (clientes)

- `id`: chave primaria.
- `nomeOficial`: nome de cadastro.
- `nomeNormalizado`: nome sem acento/caixa para busca.
- `apelidos`: lista JSONB de apelidos.
- `padraoEntrega`: modalidade padrao.
- `horarioFuncionamento`: horario comercial.
- `cnpjCpf`: documento fiscal.
- `inscricaoEstadual`: IE.
- `telefone`: telefone de contato.
- `emailContato`: email de contato.
- `observacoes`: texto livre.
- `ativo`: ativo/inativo.
- `transportadora`: FK para `Transportadora`.
- `ultimoServicoEm`: data/hora do ultimo servico.
- `origin`: origem do cadastro (ex: OP).
- `manualLockMask`: mascara de locks (reservado).
- `enderecos`: lista de `ClienteEndereco`.
- `transportadoraId`: campo transient para binds de API.

### 14.4 ClienteEndereco (cliente_enderecos)

- `id`: chave primaria.
- `cliente`: FK para `Cliente`.
- `label`: rotulo do endereco.
- `uf`: unidade federativa.
- `cidade`: cidade.
- `bairro`: bairro.
- `logradouro`: logradouro.
- `cep`: codigo postal.
- `horarioFuncionamento`: horario do endereco.
- `padraoEntrega`: modalidade preferida.
- `isDefault`: true se endereco principal.
- `origin`: origem do dado.
- `confidence`: confianca do dado.
- `manualLock`: trava manual.

### 14.5 Transportadora (transportadoras)

- `id`: chave primaria.
- `nomeOficial`: nome de cadastro.
- `nomeNormalizado`: nome normalizado.
- `apelidos`: lista JSONB.
- `localizacao`: referencia de localizacao.
- `horarioFuncionamento`: horario comercial.
- `ultimoServicoEm`: data/hora ultimo servico.
- `padraoEntrega`: modalidade preferida.
- `observacoes`: texto livre.
- `ativo`: ativo/inativo.

### 14.6 DXFAnalysis (dxf_analysis)

- `id`: chave primaria.
- `analysisId`: id externo da analise.
- `orderNr`: NR/CL normalizado.
- `order`: FK para `Order`.
- `fileName`: nome do arquivo analisado.
- `fileHash`: hash do arquivo.
- `imagePath`: caminho local gerado (se existir).
- `imageWidth`: largura da imagem.
- `imageHeight`: altura da imagem.
- `imageBucket`: bucket de storage.
- `imageKey`: chave do objeto no storage.
- `imageUri`: URI direta do storage.
- `imageChecksum`: checksum da imagem.
- `imageSizeBytes`: tamanho da imagem.
- `imageContentType`: mime type.
- `imageUploadStatus`: status de upload (ok/failed).
- `imageUploadMessage`: detalhes do upload.
- `imageUploadedAt`: data/hora do upload.
- `imageEtag`: etag do storage.
- `score`: score numerico.
- `scoreLabel`: rotulo do score.
- `scoreStars`: score em estrelas (0 a 5).
- `totalCutLengthMm`: total de corte em mm.
- `curveCount`: quantidade de curvas.
- `intersectionCount`: quantidade de intersecoes.
- `minRadiusMm`: menor raio.
- `cacheHit`: true se veio de cache.
- `analyzedAt`: data/hora da analise.
- `metrics`: JSONB com metricas adicionais.
- `explanations`: JSONB com explicacoes.
- `rawPayload`: JSONB com payload bruto.
- `createdAt`: data/hora de criacao.
- `updatedAt`: data/hora de update.

### 14.7 User (users)

- `id`: chave primaria.
- `name`: nome do usuario.
- `email`: email (login).
- `password`: hash bcrypt.
- `role`: perfil do usuario.
- `active`: ativo/inativo.

### 14.8 PasswordResetToken (password_reset_tokens)

- `id`: chave primaria.
- `token`: token de reset.
- `user`: FK para `User`.
- `expiryDate`: expiracao do token.
- `createdAt`: criado em.

### 14.9 OrderHistory (order_history)

- `id`: chave primaria.
- `order`: FK para `Order`.
- `user`: FK para `User`.
- `timestamp`: data/hora da mudanca.
- `fieldName`: campo alterado.
- `oldValue`: valor anterior.
- `newValue`: novo valor.

## 15. Status e prioridade (valores observados no codigo)

### 15.1 Status numericos

- `0`: Em producao (OrderStatusRules).
- `1`: Cortada (FileWatcherService).
- `2`: Pronto para entrega (OrderStatusRules e facas CL).
- `3`: Saiu para entrega (OrderService usa como "entrega ativa").
- `4`: Retirada (OrderService).
- `5`: Entregue (OrderService).
- `6`: Tirada (DobrasFileService).
- `7`: Montada corte (OrderStatusRules).
- `8`: Montada completa _vinco e corte(OrderStatusRules).

Obs: podem existir outros status nao documentados no codigo atual.

### 15.2 Prioridades

- Valores usados em nomes de arquivo: `VERMELHO`, `AMARELO`, `AZUL`, `VERDE`.
- Job de escalonamento usa `VERMELHA` (com A) para "nao alterar".
- Recomendacao: padronizar string de prioridade para evitar divergencias.

### 15.3 Regras automaticas

- Se `emborrachada=true`, nao aplica auto "pronto entrega".
- Se `vaiVinco=false` e `dataMontagem` presente -> auto status 2.
- Se `vaiVinco=true` e `dataVinco` presente -> auto status 2.

## 16. Paginacao por cursor

- `CursorPaging.Key(dataEntrega, id)` representa o "ultimo item".
- `CursorStrategy.ID`:
  - Ordena por `id desc`.
  - Cursor usa apenas `id`.
- `CursorStrategy.DATE_ID`:
  - Ordena por `dataEntrega desc, id desc`.
  - Cursor usa `dataEntrega` + `id`.
- Resposta sempre retorna:
  - `items`: lista.
  - `nextCursor`: Base64 com JSON do cursor.
  - `hasMore`: true se ha mais paginas.

Exemplo de resposta:

```
{
  "items": [ ... ],
  "nextCursor": "eyJkYXRhRW50cmVnYSI6IjIwMjUtMDEtMDFUMDA6MDA6MDBaIiwiaWQiOjEyM30",
  "hasMore": true
}
```

## 17. Mensagens e exemplos de payload

### 17.1 FileWatcher (laser_notifications / facas_notifications)

```
{
  "file_name": "NR123456 CLIENTE_X_VERDE.CNC",
  "path": "//share/laser/NR123456 CLIENTE_X_VERDE.CNC",
  "timestamp": 1700000000
}
```

### 17.2 Dobras (dobra_notifications)

```
{
  "file_name": "NR 123456.m.DXF",
  "path": "//share/dobras/NR 123456.m.DXF",
  "timestamp": 1700000000
}
```

### 17.3 Importacao de OP (op.imported)

```
{
  "numeroOp": "123456",
  "codigoProduto": "ABC-001",
  "descricaoProduto": "Produto X",
  "cliente": "CLIENTE X",
  "dataOp": "2025-01-10",
  "materiais": ["ACO", "BORRACHA"],
  "emborrachada": true,
  "vaiVinco": false,
  "sharePath": "\\\\server\\share\\OP123456",
  "destacador": "M",
  "modalidadeEntrega": "A ENTREGAR",
  "dataRequeridaEntrega": "2025-01-12",
  "usuarioImportacao": "op_user"
}
```

### 17.4 File command (file_commands)

```
{
  "action": "RENAME_PRIORITY",
  "nr": "123456",
  "newPriority": "AMARELO",
  "directory": "LASER"
}
```

### 17.5 RPC ping/pong

Request:

```
{
  "type": "ping",
  "ts": "2025-01-01T10:00:00Z",
  "source": "backend"
}
```

Response:

```
{
  "ok": true,
  "instanceId": "filewatcher-01",
  "ts": "2025-01-01T10:00:00Z",
  "version": "1.2.3"
}
```

### 17.6 DXF analysis request (fila)

```
{
  "analysisId": "b8f7c9ce-1a2b-4c3d-9e9a-2f7d41d3b1b0",
  "filePath": "/opt/shared/NR123456.dxf",
  "fileName": "NR123456.dxf",
  "fileHash": "sha256:...",
  "opId": "123456",
  "requestedAt": "2025-01-01T10:00:00Z",
  "requestedBy": "organizador-producao",
  "forceReprocess": false,
  "shadowMode": false,
  "source": { "app": "organizador-producao", "version": "dev" },
  "flags": { "requestId": "b8f7c9ce-1a2b-4c3d-9e9a-2f7d41d3b1b0", "orderNumber": "123456" }
}
```

### 17.7 DXF analysis result (fila)

```
{
  "analysisId": "b8f7c9ce-1a2b-4c3d-9e9a-2f7d41d3b1b0",
  "file": { "name": "NR123456.dxf", "hash": "sha256:..." },
  "score": { "value": 3.2, "label": "OK" },
  "metrics": { "totalCutLengthMm": 1200.5, "curveCount": 14, "intersectionCount": 2 },
  "image": {
    "storageBucket": "facas-renders",
    "storageKey": "nr_123456.png",
    "storageUri": "http://minio:9000/facas-renders/nr_123456.png",
    "width": 1024,
    "height": 768,
    "sizeBytes": 345678
  },
  "cacheHit": false,
  "timestampUtc": "2025-01-01T10:00:05Z"
}
```

## 18. Validacoes e regras de negocio

- `AuthenticationService.register` rejeita email duplicado.
- `forgotPassword` nao revela se email existe.
- `resetPassword` exige token valido e nao expirado.
- `DXFAnalysisRequestDTO.filePath` e obrigatorio.
- `OrderController.patchPriority` exige `priority` no payload.
- `ClienteService.create` exige `nomeOficial`.
- `TransportadoraService.create` exige `nomeOficial`.
- `OpImportService.importar` ignora payload nulo ou `numeroOp` vazio.
- `modalidadeEntrega` default: "A ENTREGAR" quando ausente.
- `OrderStatusRules.applyAutoProntoEntrega` move status para 2 quando montado/vinco completo.
- `OpImportService` aplica/retira locks manuais para sincronizacao bidirecional.

## 19. Observabilidade (metricas detalhadas)

HTTP:

- `organizador_http_server_latency_seconds` (tags: `uri`, `method`, `status`, `outcome`).
- `organizador_http_server_requests_total` (tags: `uri`, `method`, `status`, `outcome`).
- `organizador_http_server_errors_total` (tags: `uri`, `method`, `status_family`).

RabbitMQ:

- `organizador_message_processing_seconds` (tag: `queue`).

WebSocket:

- `organizador_websocket_active_sessions`.

DXF:

- `organizador_dxf_analysis_total` (tag: `status=success`).
- `organizador_dxf_analysis_failed_total`.
- `organizador_dxf_image_size_bytes`.
- `organizador_dxf_analysis_duration_seconds`.
- `organizador_dxf_analysis_upload_total` (tag: `uploadStatus`).

## 20. Configuracoes criticas e variaveis de ambiente

Banco:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

RabbitMQ:

- `spring.rabbitmq.host`
- `spring.rabbitmq.username`
- `spring.rabbitmq.password`
- `spring.rabbitmq.port`

RPC FileWatcher:

- `app.rpc.filewatcher.queue`
- `app.rpc.filewatcher.timeout-ms`
- `app.rpc.filewatcher.stub.enabled`

DXF:

- `app.dxf.analysis.request-queue`
- `app.dxf.analysis.result-queue`
- `app.dxf.analysis.websocket-topic`
- `app.dxf.analysis.image-base-url`
- `app.dxf.analysis.order-number-pattern`

JWT:

- `security.jwt.secret-key`
- `security.jwt.expiration-time`

Email:

- `spring.mail.host`
- `spring.mail.port`
- `spring.mail.username`
- `spring.mail.password`
- `app.frontend.url`

CORS:

- `app.cors.allowed-origins`

## 21. Operacao e dependencias

- Dependencias obrigatorias:
  - PostgreSQL (schema gerenciado por Flyway).
  - RabbitMQ (filas e exchanges).
- Dependencias opcionais:
  - FileWatcherApp (pastas/arquivos).
  - Motor de analise DXF (publica resultados).
- Endpoints de observabilidade:
  - `/actuator/health`
  - `/actuator/prometheus`
- Logs relevantes:
  - `OpImportService` e listeners AMQP.
  - `DXFAnalysisService` e listener de resultados.
  - `DobrasFileService` e `FileWatcherService`.

## 22. Observacoes de implementacao e pontos de atencao

- `OrderRepositoryImpl` contem comentario "TODOS os filtros", mas os filtros nao estao implementados no arquivo atual.
- Job de prioridades usa `fixedRate=60000` com comentario "10 minutos".
- Strings de prioridade diferem entre "VERMELHO" e "VERMELHA".
- `spring.jpa.hibernate.ddl-auto=update` esta ativo junto com Flyway; risco de drift em schema.
- Existem migracoes comentadas (no-op) mantidas por historico.
