# Plano de Reescrita Robusta - Organizador Producao + FileWatcher

Data: 2026-05-22
Status: plano tecnico inicial baseado em auditoria do codigo atual
Escopo: backend Spring Boot, frontend Angular, infraestrutura Docker/Nginx/observabilidade e integracao com `/home/ynz/Documents/FileWatcherApp`

## 1. Objetivo

Reescrever e reorganizar a aplicacao sem perder funcionalidades, dados ou integracoes existentes, mantendo o sistema simples, leve e previsivel.

A reescrita deve preservar:

- Fluxo de pedidos e status de producao.
- Importacao de OPs pelo FileWatcher.
- Notificacoes de arquivos Laser, Facas e Dobras via RabbitMQ.
- Analise DXF, renderizacao de imagens e exibicao no frontend.
- Sincronizacao de prioridade entre sistema e arquivos fisicos.
- WebSocket em tempo real para pedidos, prioridades, status e DXF.
- Autenticacao JWT, usuarios, permissoes e telas atuais.
- Persistencia em PostgreSQL, imagens no MinIO e deploy via Docker/Nginx.

## 2. Diagnostico resumido

O sistema funciona, mas esta com alto risco operacional porque varias responsabilidades estao concentradas em arquivos grandes, contratos estao implicitos e ha regras duplicadas entre backend, frontend e FileWatcher.

Principais pontos encontrados:

- `OpImportService.java` tem cerca de 1096 linhas e mistura importacao, normalizacao, sincronizacao com pedido, locks manuais, retry, historico e WebSocket.
- `DXFAnalysisService.java` tem cerca de 851 linhas e mistura contrato Rabbit, mapeamento, persistencia, imagem, fallback de URL, score e broadcast.
- `FileWatcherService.cs` tem cerca de 1448 linhas e concentra watchers, debounce, sanitizacao, publicacao Rabbit, OP, Dobras, DXF e retries.
- `DXFAnalyzer.cs` tem cerca de 2242 linhas e concentra regras complexas de analise DXF.
- Componentes Angular como `montagem.component.ts`, `order-details-modal.component.ts`, `pipeline-board.component.ts` e `orders.component.ts` acumulam regra de negocio, chamadas API, WebSocket, estado visual e manipulacao de modal.
- Existem endpoints publicos e configuracoes sensiveis que precisam ser separados por ambiente antes de qualquer abertura externa.
- Flyway esta habilitado, mas `spring.jpa.hibernate.ddl-auto=update` tambem esta ativo, o que enfraquece controle de schema.
- RabbitMQ nao tem contrato versionado claro, DLQ/retry padronizado e idempotencia forte em todos os consumidores.
- Monitoramento existe como intencao, mas precisa virar telemetria util: logs estruturados, metricas por fluxo, tracing e alertas acionaveis.

## 3. Contratos que nao podem quebrar

Antes de reescrever qualquer modulo, estes contratos devem virar testes automatizados e documentacao viva.

### 3.1 REST API

Preservar ou versionar com compatibilidade:

- `/api/auth/register`
- `/api/auth/login`
- `/api/users/me`
- `/api/users/assignable`
- `/api/orders`
- `/api/orders/{id}`
- `/api/orders/search-cursor`
- `/api/orders/{id}/history`
- `/api/dxf-analysis`
- `/api/dxf-analysis/order/{nr}`
- `/api/dxf-analysis/recent`
- `/api/ops`
- `/ops`
- Endpoints de clientes, transportadoras, enderecos e telas operacionais existentes.

Endpoints que parecem existir no frontend, mas precisam ser confirmados ou corrigidos:

- `/api/orders/updateAdm/{id}`
- `/api/orders/deleteAdm/{id}`
- `/ops/{nr}/arquivo`
- `/api/order-history`

### 3.2 WebSocket

Preservar:

- Endpoint STOMP: `/ws/orders`
- Topicos: `/topic/orders`, `/topic/prioridades`, `/topic/status`, `/topic/dxf-analysis`
- Entradas atuais: `/app/orders`, `/app/prioridades`, `/app/status/ping-now`
- Fluxo de exclusao deve ser corrigido com compatibilidade: frontend hoje publica `/orders/delete/{id}`, backend espera `/app/orders/delete/{id}`.

### 3.3 RabbitMQ

Preservar filas e semantica:

- `laser_notifications`
- `facas_notifications`
- `dobra_notifications`
- `op.imported`
- `file_commands`
- `filewatcher.rpc.ping`
- `facas.analysis.request`
- `facas.analysis.result`

Contratos de payload a estabilizar:

- Notificacao Laser/Facas: nome do arquivo, prioridade/cor, numero NR/CL e caminho.
- Dobras: arquivos `.m.dxf` e `.dxf.fcd` atualizam fluxo de dobra.
- DXF base: `.dxf` deve disparar analise, mas nao necessariamente atualizar dobra.
- OP importada: `numeroOp`, `codigoProduto`, `descricaoProduto`, `cliente`, datas, materiais, flags, endereco/cliente sugerido e arquivo de origem.
- Comando de arquivo: acao de renomear prioridade do sistema para o FileWatcher.
- Ping RPC: resposta com `ok`, `instanceId`, `ts`, `version`.
- Resultado DXF: `analysisId`, `opId`, `fileHash`, `metrics`, `flags`, `image`, `score`, `explanations`, `version`, `durationMs`, `shadowMode`.

### 3.4 Nomeacao e arquivos

Preservar regras do FileWatcher:

- `NR` e `CL` devem continuar reconhecidos com e sem espaco.
- Arquivos Laser/Facas devem manter prioridade por cor.
- `.dxf` base deve acionar analise DXF.
- `.m.dxf` e `.dxf.fcd` devem acionar fluxo de Dobras.
- Quando `PersistLocalImageCopy=false`, o backend deve montar URL de imagem por `imageKey` + `APP_DXF_ANALYSIS_IMAGE_BASE_URL`.
- O bucket MinIO `facas-renders` e o prefixo usado pelo FileWatcher devem ser preservados ou migrados com compatibilidade.

## 4. Problemas criticos a corrigir antes da reescrita profunda

### 4.1 Configuracao e seguranca

Arquivos relevantes:

- `src/main/resources/application.properties`
- `docker-compose.yml`
- `src/main/java/.../infra/security/SecurityConfiguration.java`
- `nginx/organizador.conf`

Problemas:

- Senhas e segredos aparecem em arquivos de configuracao.
- `server.error.include-message=always` e `include-binding-errors=always` expoem detalhes demais em producao.
- `spring.jpa.hibernate.ddl-auto=update` compete com Flyway.
- `/actuator/**`, `/api/orders/**` e `/api/dxf-analysis/**` estao publicos no backend.
- Nginx nao esta com TLS local e `deny all` esta comentado.

Correcao proposta:

- Mover segredos para `.env`/secret manager e remover defaults sensiveis.
- Criar perfis `dev`, `homolog`, `prod`.
- Trocar `ddl-auto=update` para `validate`.
- Revisar endpoints publicos por caso de uso real.
- Restringir actuator em producao, mantendo `/health` publico se necessario.
- Padronizar CORS por ambiente.

### 4.2 Transacoes, async e consistencia

Arquivos relevantes:

- `OrderService.java`
- `OpImportService.java`
- `DXFAnalysisService.java`

Problemas:

- Metodos `@Async` e `@Transactional` chamados internamente nao passam pelo proxy Spring.
- Jobs agendados fazem varreduras amplas com `findAll()`.
- Atualizacoes de pedido, OP e historico estao espalhadas.
- Falta `@Version` para concorrencia otimista nas entidades centrais.

Correcao proposta:

- Separar use cases em beans publicos pequenos.
- Usar transacoes nos limites corretos de caso de uso.
- Adicionar controle de concorrencia em `Order`, `OpImport` e entidades sensiveis.
- Substituir varreduras por queries orientadas a estado e indices.
- Publicar eventos externos somente depois do commit.

### 4.3 RabbitMQ e resiliencia

Arquivos relevantes:

- `AmqpConfig.java`
- `RabbitMQConfig.java`
- `RabbitRpcConfig.java`
- `DXFAnalysisResultListener.java`
- `FileWatcherService.java`
- `DXFAnalysisWorker.cs`
- `RabbitMqConnection.cs`

Problemas:

- Consumidores Java capturam excecao e podem confirmar mensagem ruim sem retry/DLQ.
- Nao ha padrao unico de retry, nack, dead-letter e idempotencia.
- O FileWatcher usa uma conexao/canal Rabbit compartilhado por varios fluxos, o que aumenta risco de concorrencia.
- Contratos sao inferidos por classes soltas, nao por uma especificacao comum.

Correcao proposta:

- Declarar exchanges, bindings, DLQs e politicas de retry explicitamente.
- Usar `messageId`, `correlationId`, `schemaVersion`, `source`, `occurredAt`.
- Implementar inbox/idempotencia no backend para eventos externos.
- Implementar outbox para eventos publicados apos mudancas de banco.
- Criar canais Rabbit separados por consumer/publisher no FileWatcher.
- Definir contratos em JSON Schema ou arquivos DTO versionados compartilhados por documentacao.

### 4.4 Frontend

Arquivos relevantes:

- `organizer-front/src/app/services/*.ts`
- `organizer-front/src/app/components/**/*.ts`
- `organizer-front/src/environments/enviroment.ts`

Problemas:

- Muitos componentes carregam regra de negocio e estado remoto diretamente.
- Ha subscriptions sem ciclo de vida claro.
- WebSocket, REST e atualizacao local competem entre si.
- Ha endpoints chamados pelo frontend que nao parecem existir no backend.
- Ambiente dev usa `production: true` e `ws://` fixo.
- Varias telas pre-carregam DXF para listas inteiras, gerando chamadas N+1.

Correcao proposta:

- Criar camada de `ApiClient` tipada por dominio.
- Criar facades/stores por fluxo: pedidos, montagem, borracha, entrega, DXF, usuarios.
- Usar `takeUntilDestroyed`, signals ou RxJS com ownership claro.
- Centralizar WebSocket em um adapter com reconexao moderada e normalizacao de mensagens.
- Mover regras de transicao de status para backend.
- Carregar DXF sob demanda ou em endpoint batch.
- Corrigir ambientes e remover endpoints fantasmas ou criar compatibilidade.

## 5. Arquitetura alvo

Manter uma arquitetura simples: monolito modular no backend, Angular organizado por features e FileWatcher como worker separado.

Nao ha necessidade de criar microservicos adicionais agora. O custo operacional seria maior que o ganho. O desenho recomendado e:

```text
Angular
  Features
    pedidos
    montagem
    borracha
    entrega
    clientes
    dxf
    usuarios
  Shared
    auth
    http
    websocket
    ui
    models

Spring Boot
  api
    controllers + DTOs + validators
  application
    use cases
    command/query handlers
  domain
    entities
    value objects
    business rules
    domain services
  infrastructure
    jpa repositories
    rabbit adapters
    websocket publishers
    minio/url adapters
    security
    observability

FileWatcherApp
  watchers
    laser
    facas
    dobras
    op
  messaging
    publishers
    consumers
    contracts
  dxf
    analysis
    rendering
    scoring
    storage
  operations
    file commands
    naming
```

## 6. Modulos backend propostos

### 6.1 Orders / Production Flow

Responsavel por:

- Pedido.
- Status operacional.
- Prioridade.
- Historico.
- Atribuicao de usuario.
- Transicoes validas.
- Integracao com WebSocket.

Classes alvo:

- `OrderCommandService`
- `OrderQueryService`
- `OrderTransitionService`
- `OrderPriorityService`
- `OrderHistoryService`
- `OrderWebSocketPublisher`
- `OrderSearchRepository`

Boas praticas:

- Controllers recebem DTOs, nao entidades JPA.
- Toda transicao passa por um metodo explicito.
- Historico e publicado por evento interno.
- Busca usa filtros reais, indices e paginacao.

### 6.2 OP Import

Responsavel por:

- Consumir payload `op.imported`.
- Validar contrato.
- Normalizar campos.
- Criar/atualizar `OpImport`.
- Sincronizar pedido relacionado.
- Preservar locks manuais.
- Enriquecer cliente/endereco.

Classes alvo:

- `OpImportedConsumer`
- `ImportOpUseCase`
- `OpPayloadValidator`
- `OpNormalizer`
- `OpMaterialMapper`
- `OpOrderLinker`
- `ManualLockPolicy`
- `ClientEnrichmentUseCase`

Boas praticas:

- Separar parse de mensagem, regra de negocio e persistencia.
- Idempotencia por `numeroOp` + hash do payload relevante.
- Retry com DLQ para payload invalido ou falha temporaria.
- Testes de caracterizacao para payloads reais do FileWatcher.

### 6.3 FileWatcher Integration

Responsavel por:

- Eventos Laser/Facas/Dobras.
- Comandos para renomeacao de prioridade.
- Ping/health do worker.
- Normalizacao NR/CL.

Classes alvo:

- `LaserNotificationConsumer`
- `FacasNotificationConsumer`
- `DobrasNotificationConsumer`
- `FileCommandPublisher`
- `FileWatcherStatusService`
- `FileWatcherNamingAdapter`

Boas praticas:

- Contratos versionados.
- Idempotencia por nome/caminho/hash/event timestamp.
- Nao duplicar regras de nomeacao: backend deve consumir o resultado canonico do FileWatcher.
- Comandos de arquivo devem ter `commandId`, estado e resposta/auditoria quando possivel.

### 6.4 DXF Analysis

Responsavel por:

- Publicar requisicao de analise.
- Consumir resultado.
- Persistir metricas, score, flags e imagem.
- Resolver URL de imagem.
- Expor consulta por pedido/OP.
- Enviar atualizacao WebSocket.

Classes alvo:

- `DxfAnalysisRequestService`
- `DxfAnalysisResultConsumer`
- `PersistDxfAnalysisUseCase`
- `DxfAnalysisMapper`
- `DxfImageUrlResolver`
- `DxfAnalysisQueryService`
- `DxfAnalysisPublisher`

Boas praticas:

- `imageKey` e `imageBaseUrl` sao o caminho principal.
- `imageUri` e `imagePath` ficam como fallback legado.
- Resultado DXF deve ser idempotente por `analysisId` e `fileHash`.
- Endpoint batch para cards/listas deve evitar N+1 no frontend.

### 6.5 Customers / Carriers

Responsavel por:

- Clientes.
- Transportadoras.
- Enderecos.
- Defaults de entrega.
- Enriquecimento automatico.

Classes alvo:

- `ClienteCommandService`
- `ClienteQueryService`
- `TransportadoraService`
- `EnderecoService`
- `ClienteNormalizationService`
- `ClienteDefaultPolicy`

Boas praticas:

- Normalizacao por tabela/lista de regras, nao centenas de `replaceAll`.
- DTOs para criar/atualizar.
- Erros tipados, nao `RuntimeException` generica.

### 6.6 Identity / Security

Responsavel por:

- Usuarios.
- Roles.
- Login.
- JWT.
- Autorizacao por endpoint.

Classes alvo:

- `AuthController`
- `UserController`
- `UserCommandService`
- `UserQueryService`
- `JwtService`
- `SecurityPolicy`

Boas praticas:

- Nunca retornar entidade `User` diretamente.
- JWT com issuer/audience/expiracao clara.
- Permissoes descritas por caso de uso.
- Auditoria para alteracoes administrativas.

## 7. FileWatcherApp - reestruturacao sem quebrar integracao

O FileWatcher deve continuar separado, mas com responsabilidades menores.

### 7.1 Modulos C# propostos

- `Watchers/LaserWatcher`
- `Watchers/FacasWatcher`
- `Watchers/DobrasWatcher`
- `Watchers/OpWatcher`
- `Messaging/RabbitConnectionFactory`
- `Messaging/RabbitPublisher`
- `Messaging/RabbitConsumer`
- `Messaging/Contracts`
- `Naming/FileWatcherNaming`
- `Dxf/DxfAnalysisWorker`
- `Dxf/DxfAnalyzer`
- `Dxf/DxfRenderer`
- `Dxf/DxfScorer`
- `Storage/S3ImageStorageClient`
- `Commands/FileCommandConsumer`
- `Commands/RenamePriorityHandler`
- `Health/RpcPingResponderService`

### 7.2 Regras a preservar

- Base `.dxf` entra em analise DXF.
- `.m.dxf` e `.dxf.fcd` atualizam Dobras.
- O backend depende de `image.storageKey` quando `PersistLocalImageCopy=false`.
- Renomeacao por prioridade deve continuar fisicamente no arquivo.
- OP importada deve manter os campos atuais, inclusive enriquecimento de cliente/endereco.

### 7.3 Melhorias obrigatorias

- Um canal Rabbit por consumidor/publicador ou pool controlado.
- Retry com backoff por operacao de arquivo.
- Logs estruturados com `correlationId`, `filePath`, `opId`, `analysisId`.
- Metricas: eventos detectados, eventos publicados, falhas, retries, tempo ate arquivo estabilizar, duracao de analise DXF, upload MinIO.
- Testes de contrato contra payloads usados pelo backend.

## 8. Banco de dados

### 8.1 Diretriz

Flyway deve ser a unica fonte de evolucao de schema.

Mudancas:

- `spring.jpa.hibernate.ddl-auto=validate`.
- Criar migrations para indices e constraints ausentes.
- Adicionar `@Version` em entidades de escrita concorrente.
- Definir unicidade onde a regra exige, por exemplo `orders.nr` se o negocio permitir apenas um pedido ativo por NR.
- Criar indices para buscas por status, prioridade, numero, datas e relacionamentos.
- Criar tabelas de inbox/outbox se Rabbit continuar sendo usado para integracao critica.

### 8.2 Cuidados de migracao

- Antes de criar constraint unica, identificar duplicidades reais.
- Toda migracao deve ter script de validacao.
- Backup e teste de restore antes do primeiro deploy da reescrita.
- Usar migrations pequenas e reversiveis operacionalmente.

## 9. Observabilidade e monitoramento

### 9.1 Logs

Padrao:

- JSON estruturado em producao.
- Campos minimos: `timestamp`, `level`, `service`, `environment`, `traceId`, `correlationId`, `userId`, `orderNr`, `opId`, `analysisId`, `queue`, `eventType`.
- Remover `System.out.println` e `Console.WriteLine` soltos dos fluxos principais.

### 9.2 Metricas

Backend:

- Latencia HTTP por endpoint.
- Erros 4xx/5xx.
- Tempo de processamento por consumidor Rabbit.
- Falhas por fila.
- Quantidade de WebSockets ativos.
- Jobs agendados: duracao, sucesso, falha.
- Pedidos por status e atrasos de fluxo.

FileWatcher:

- Arquivos detectados por pasta.
- Eventos ignorados por regra.
- Publicacoes Rabbit por fila.
- Falhas de publish/consume.
- Tempo de estabilizacao do arquivo.
- Duracao de analise DXF.
- Uploads MinIO por status.

RabbitMQ:

- Profundidade de fila.
- Mensagens prontas/nao confirmadas.
- Taxa de publish/consume.
- Dead letters.
- Consumers conectados.

PostgreSQL:

- Latencia de queries.
- Conexoes ativas.
- Locks/deadlocks.
- Crescimento de tabelas.
- Queries lentas.

Frontend:

- Erros JavaScript.
- Falhas HTTP por rota.
- Tempo de carregamento das telas criticas.
- Falhas de WebSocket/reconexoes.

### 9.3 Alertas minimos

- Backend indisponivel por mais de 1 minuto.
- FileWatcher sem ping por mais de 2 minutos.
- Fila `facas.analysis.request` crescendo continuamente.
- Fila `facas.analysis.result` com mensagens paradas.
- Qualquer DLQ com mensagem.
- MinIO upload failure acima de limite.
- PostgreSQL com conexoes acima de 80%.
- Erro 5xx acima de 1% por 5 minutos.

### 9.4 Tracing

Usar OpenTelemetry ou Micrometer Tracing.

Fluxos que devem ser rastreaveis ponta a ponta:

- Arquivo Laser/Facas -> Rabbit -> backend -> pedido -> WebSocket -> frontend.
- OP PDF/JSON -> FileWatcher -> Rabbit -> backend -> cliente/pedido.
- DXF -> request -> worker -> MinIO -> result -> backend -> frontend.
- Alteracao de prioridade no frontend -> backend -> `file_commands` -> FileWatcher -> arquivo renomeado.

## 10. Plano de execucao por fases

### Fase 0 - Inventario e base de seguranca

Objetivo: congelar comportamento antes de mudar arquitetura.

Entregas:

- Matriz de funcionalidades atuais.
- Lista de endpoints realmente usados pelo frontend.
- Lista de filas e payloads com exemplos reais.
- Export de payloads reais do Rabbit/FileWatcher para testes.
- Testes de caracterizacao dos fluxos criticos.
- CI rodando backend, frontend e FileWatcher.
- Documento de decisao dos endpoints publicos.

Criterio de saida:

- Conseguir provar automaticamente que os fluxos atuais principais continuam funcionando.

### Fase 1 - Estabilizacao sem reescrita grande

Objetivo: reduzir risco operacional rapidamente.

Entregas:

- Segredos fora do codigo.
- `ddl-auto=validate`.
- CORS e actuator por ambiente.
- Correcoes de endpoints fantasmas ou camada de compatibilidade.
- Correcao do destino WebSocket de delete.
- DLQ/retry padrao para Rabbit.
- Correcoes de `@Async`/`@Transactional` por self-invocation.
- Busca cursor com filtros reais.
- Logs estruturados nos fluxos Rabbit/DXF/OP.

Criterio de saida:

- Sistema atual mais previsivel, com menos falhas silenciosas e observabilidade minima.

### Fase 2 - Backend por modulos

Objetivo: separar responsabilidades sem troca brusca de tecnologia.

Ordem recomendada:

1. Criar DTOs e mappers para pedidos, usuarios, clientes e DXF.
2. Extrair `OrderTransitionService` e centralizar regras de status.
3. Dividir `OpImportService` em use cases menores.
4. Dividir `DXFAnalysisService` em request, result, image resolver e queries.
5. Separar consumidores Rabbit por fila e contrato.
6. Introduzir outbox/inbox para eventos criticos.
7. Adicionar testes unitarios e de integracao por modulo.

Criterio de saida:

- Nenhum controller atualizando entidade manualmente com regra espalhada.
- Nenhum servico de aplicacao com centenas de linhas misturando varias responsabilidades.

### Fase 3 - FileWatcher robusto

Objetivo: manter o worker leve, mas confiavel.

Ordem recomendada:

1. Extrair watchers por tipo de pasta.
2. Separar publicacao Rabbit, consumo Rabbit e contratos.
3. Separar pipeline DXF: estabilizacao de arquivo, analise, render, upload, publish result.
4. Trocar canal Rabbit compartilhado por canais dedicados.
5. Adicionar testes de nomeacao e contratos.
6. Adicionar metricas e health checks.

Criterio de saida:

- FileWatcher pode falhar parcialmente sem derrubar todos os fluxos.
- Cada evento gerado tem log, metrica e contrato validavel.

### Fase 4 - Frontend organizado por features

Objetivo: manter as mesmas telas, mas com estado previsivel.

Ordem recomendada:

1. Corrigir ambientes e URLs.
2. Criar modelos TypeScript alinhados aos DTOs do backend.
3. Criar `ApiClient` por dominio.
4. Criar facades/stores para pedidos, montagem, borracha, entrega e DXF.
5. Centralizar WebSocket e reconexao.
6. Remover regra de negocio duplicada das telas.
7. Trocar N+1 de DXF por carregamento sob demanda ou batch.
8. Aplicar `takeUntilDestroyed` em subscriptions.

Criterio de saida:

- Componentes cuidam principalmente de UI.
- Fluxos de estado sao testaveis e nao dependem de manipulacao manual de modal/DOM.

### Fase 5 - Observabilidade e operacao

Objetivo: operar o sistema com evidencia, nao por tentativa e erro.

Entregas:

- Dashboards Grafana por servico e fluxo.
- Alertas minimos.
- Runbooks de incidentes.
- Smoke tests pos-deploy.
- Backup/restore testado.
- SLOs simples: disponibilidade, latencia, atraso de fila e sucesso de analise DXF.

Criterio de saida:

- Uma falha de arquivo, fila, banco ou MinIO pode ser detectada e diagnosticada rapidamente.

### Fase 6 - Cutover e rollback

Objetivo: trocar partes do sistema sem parada longa.

Estrategia:

- Migrar por modulo, nao por big bang.
- Manter contratos antigos enquanto novos clientes entram.
- Usar feature flags para novos fluxos.
- Rodar consumidores novos em modo shadow quando possivel.
- Reprocessar payloads salvos para comparar resultado.
- Ter plano de rollback por imagem Docker e migration.

Criterio de saida:

- Nova versao entrega as mesmas funcionalidades com logs, testes e rollback conhecido.

## 11. Testes obrigatorios

### 11.1 Backend

- Unitarios para regras de status, prioridade, locks manuais e normalizacao NR/CL.
- Integracao com PostgreSQL via Testcontainers.
- Integracao Rabbit com filas reais ou Testcontainers.
- Contract tests para payloads do FileWatcher.
- Testes de seguranca para endpoints publicos/privados.
- Testes de concorrencia para atualizacao de pedido.

### 11.2 FileWatcher

- Nomeacao de arquivos NR/CL.
- Sanitizacao de Laser/Facas.
- Sanitizacao de Dobras.
- Publicacao de OP importada.
- Publicacao de requisicao DXF.
- Consumo de comando `file_commands`.
- Retry quando arquivo esta bloqueado.
- Upload MinIO e montagem de `storageKey`.

### 11.3 Frontend

- Testes de services/facades.
- Testes de guards e roles.
- Testes de WebSocket adapter.
- Testes dos fluxos principais de tela.
- E2E para login, pedido, montagem, borracha, entrega, DXF e prioridade.

### 11.4 E2E de contrato entre sistemas

Cenarios obrigatorios:

- Criar/atualizar pedido a partir de arquivo Laser.
- Atualizar Facas a partir de `FACASOK`.
- Importar OP com cliente/endereco sugerido.
- Alterar prioridade no frontend e renomear arquivo no FileWatcher.
- Enviar `.dxf` base e exibir imagem MinIO no frontend.
- Enviar `.m.dxf`/`.dxf.fcd` e atualizar Dobras.
- Simular FileWatcher offline e exibir status correto.
- Simular mensagem invalida e cair em DLQ sem perder rastreabilidade.

## 12. Padroes de codigo

### Java

- Injecao por construtor.
- Controllers com DTOs e validacao.
- Services pequenos por caso de uso.
- Repositorios sem regra de negocio.
- Exceptions tipadas e `@ControllerAdvice`.
- Sem `System.out.println`.
- Sem entidade JPA exposta diretamente na API.
- Sem wildcard imports.

### TypeScript/Angular

- Interfaces/DTOs tipados.
- Componentes sem regra de negocio pesada.
- Services/facades por dominio.
- `takeUntilDestroyed` ou ownership equivalente para subscriptions.
- Sem `any` em fluxos principais.
- Sem `alert` em UX de producao.
- Sem acesso direto a `window.bootstrap` espalhado.

### C#

- Hosted services pequenos.
- Contratos em classes imutaveis quando possivel.
- Logging estruturado via `ILogger`.
- Canais Rabbit dedicados.
- CancellationToken respeitado.
- Regras de nomeacao testadas.
- Sem `Console.WriteLine` solto nos fluxos principais.

## 13. Sequencia recomendada de arquivos para atacar

Primeiro estabilizar:

- `application.properties`
- `docker-compose.yml`
- `SecurityConfiguration.java`
- `OrderRepositoryImpl.java`
- `WebSocketService` no frontend
- `DXFAnalysisResultListener.java`
- `FileWatcherService.java`
- `DXFAnalysisWorker.cs`

Depois dividir:

- `OpImportService.java`
- `DXFAnalysisService.java`
- `OrderController.java`
- `OrderWebSocketController.java`
- `FileWatcherService.cs`
- `DXFAnalyzer.cs`
- Componentes Angular grandes de producao.

## 14. Riscos de migracao

| Risco | Impacto | Mitigacao |
| --- | --- | --- |
| Perder compatibilidade com FileWatcher | Alto | Contract tests com payloads reais antes de reescrever consumidores |
| Quebrar imagens DXF | Alto | Preservar `imageKey` + `imageBaseUrl` como regra principal |
| Duplicar pedidos por NR/CL | Alto | Validar dados atuais antes de criar constraints |
| Mensagens Rabbit perdidas | Alto | DLQ, retry, idempotencia e outbox/inbox |
| Tela ficar inconsistente com WebSocket | Medio/alto | Normalizar envelopes e centralizar adapter frontend |
| Reescrita virar big bang | Alto | Migrar por modulo com compatibilidade e feature flags |
| Aumentar complexidade operacional | Medio | Evitar novos microservicos agora |

## 15. Definicao de pronto

A reescrita so pode ser considerada pronta quando:

- Todas as funcionalidades atuais estiverem cobertas por testes ou checklist operacional.
- Backend, frontend e FileWatcher passarem no CI.
- Contratos Rabbit e REST estiverem versionados/documentados.
- Observabilidade minima estiver ativa em producao.
- Segredos nao estiverem no repositorio.
- Banco estiver sob Flyway com `ddl-auto=validate`.
- FileWatcher puder ficar offline sem travar a aplicacao inteira.
- Mensagens invalidas forem rastreaveis em DLQ.
- Imagens DXF abrirem pelo frontend usando MinIO.
- Alteracao de prioridade continuar renomeando arquivos fisicos.
- Houver rollback conhecido para deploy.

## 16. Primeiro sprint recomendado

Objetivo: estabilizar sem mudar a cara do produto.

Tarefas:

1. Criar testes de contrato com amostras reais de Rabbit/FileWatcher.
2. Corrigir configuracao por ambiente e remover segredos dos arquivos versionados.
3. Trocar `ddl-auto=update` para `validate` apos validar migrations.
4. Corrigir WebSocket delete e endpoints divergentes frontend/backend.
5. Implementar DLQ/retry nos consumidores Rabbit principais.
6. Corrigir filtros reais em `search-cursor`.
7. Corrigir `@Async`/`@Transactional` que hoje nao aplicam por chamada interna.
8. Adicionar logs estruturados nos fluxos OP, DXF e FileWatcher.
9. Criar dashboard minimo: API, Rabbit, Postgres, FileWatcher e DXF.
10. Comecar extracao de `OpImportService` por testes de caracterizacao.

Resultado esperado:

- Menos instabilidade imediata.
- Mapa claro do comportamento atual.
- Base segura para reescrever por partes sem perder funcionalidade.
