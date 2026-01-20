# Frontend SDS (Software Design Specification)

Este documento descreve o frontend do projeto "organizer-front" (Angular) de ponta a ponta.
Objetivo: explicar o que cada parte faz, como faz, por que existe, e quais sao os
principais arquivos, fluxos e modelos. Conteudo em ASCII por padrao.

## 1. Escopo e objetivos

- Frontend Angular 17 (standalone components + SSR/hydration).
- UI para pedidos, entrega, montagem, emborrachamento, OP e admin.
- Integra REST (API) e WebSocket STOMP (atualizacoes em tempo real).
- Usa Bootstrap, Angular Material (Snackbar), NG Bootstrap e RxJS.

## 2. Arquitetura de alto nivel

Camadas principais:

- Entry/Config: bootstrap, rotas, providers, SSR.
- Services: HTTP, WebSocket e regras de integracao com backend.
- Models: tipos de dados compartilhados com a API.
- Components: telas e modais (standalone).
- Pipes: utilitarios (ex: tamanho de arquivo).
- Interceptors/Guards: seguranca e UX de sessao.
- Styles: estilos globais e por componente.

## 3. Fluxos principais

### 3.1 Autenticacao

- Tela de login chama `/api/auth/login`.
- Token JWT salvo em `localStorage` (remember) ou `sessionStorage`.
- Interceptor injeta `Authorization: Bearer <token>` em todas as chamadas.
- Guard bloqueia rotas protegidas e redireciona para `/login`.

### 3.2 Pedidos (lista principal)

- `OrdersComponent` lista status 0/1/6 (producao/cortada/tirada).
- Atualizacoes em tempo real via WebSocket `/topic/orders` e `/topic/prioridades`.
- Expansao para ver imagem DXF quando permitido (usuario autenticado).

### 3.3 Entrega

- `DeliveryComponent` lista pedidos elegiveis (status 1/2/6/7/8).
- Seleciona varios pedidos e atualiza status para 3 (saiu) ou 4 (retirada).
- Modal de "saida adversa" cria pedidos extras com prioridade verde.

### 3.4 Entrega Retorno

- `DeliveryReturnComponent` filtra pedidos em status 3 por entregador.
- Agrupa por cliente e marca retorno (status 5) com recebedor.
- Permite cancelar saida e voltar status para 2.

### 3.5 Montagem

- `MontagemComponent` lista status 1/6/7 (cortada/tirada/montada corte).
- Acoes rapidas: montar, vincar, montar+vincar.
- Integra DXF (complexidade) e materiais da OP.
- Abre historico de alteracoes (OrderHistory).

### 3.6 Emborrachamento

- `RubberComponent` lista pedidos montados (7/8) com `emborrachada=true`.
- Permite registrar emborrachador e marcar status 2 (pronto entrega).
- Consulta DXF e materiais de OP para apoio visual.

### 3.7 Entregues (usuario comum)

- `DeliveredComponent` lista pedidos entregues/retirados com paginacao local.
- Permite editar detalhes e criar "saida adversa".

### 3.8 Admin (tabbed)

- `DeliveredAdminComponent` organiza abas:
  - Entregues/Retiradas (lista com filtros).
  - Producao (pipeline board).
  - Clientes.
  - Transportadoras.
  - Usuarios (somente ADMIN).

## 4. Dependencias e build

Principais libs:

- Angular 17, RxJS.
- Angular Material (Snackbar).
- Bootstrap 5, jQuery, moment (scripts globais).
- NG Bootstrap (datepicker).
- RxStomp (WebSocket STOMP).
- Luxon/date-fns (datas).

Build:

- Angular CLI com `@angular-devkit/build-angular:application`.
- SSR habilitado (`main.server.ts`, `app.config.server.ts`).
- `styles` e `scripts` configurados em `angular.json`.

## 5. Configuracoes e ambiente

Arquivos:

- `organizer-front/src/app/enviroment.ts`
- `organizer-front/src/app/enviroment.prod.ts`

Campos relevantes:

- `apiBaseUrl`: base da API REST (default `/api`).
- `apiUrl`: base de pedidos (default `/api/orders`).
- `wsUrl`: STOMP WS (usa host atual).
- `imagePublicBaseUrl`: base publica para imagens DXF (pode ser vazio).

Observacao:

- Os arquivos usam o nome `enviroment` (sem "n" antes do "ment").

## 6. Rotas

Definidas em `organizer-front/src/app/app.routes.ts`:

- `/login` -> LoginComponent
- `/register` -> RegisterComponent
- `/forgot-password` -> ForgotPasswordComponent
- `/reset-password` -> ResetPasswordComponent
- `/pedidos` -> OrdersComponent
- `/entrega` -> DeliveryComponent (authGuard)
- `/entregues` -> DeliveredComponent (authGuard)
- `/entregaVolta` -> DeliveryReturnComponent (authGuard)
- `/montagem` -> MontagemComponent (authGuard)
- `/admin` -> DeliveredAdminComponent (authGuard + roles ADMIN/DESENHISTA)
- `/borracha` -> RubberComponent (authGuard)
- `/op` -> OpComponent (authGuard)
- `/` -> redirect para `/pedidos`

## 7. WebSocket STOMP

Implementacao em `WebsocketService`:

- Broker URL: `environment.wsUrl` (ex: `ws://host/ws/orders`).
- Heartbeat outgoing 20s, reconnect 200ms.

Topicos observados:

- Subscriptions:
  - `/topic/orders`
  - `/topic/prioridades`
  - `/topic/dxf-analysis`
  - `/topic/status`
- Publish:
  - `/app/orders/create`
  - `/app/orders/update`
  - `/orders/delete/{id}` (frontend atual)
  - `/app/status/ping-now`

Observacao:

- Funciona no backend: delete em `/app/orders/delete/{id}`; o frontend publica em `/orders/delete/{id}`, entao delete via WS nao funciona.

## 8. Servicos (HTTP)

### 8.1 AuthService

- Base: `${apiBaseUrl}/auth`.
- `login`, `register`, `forgotPassword`, `validateResetToken`, `resetPassword`.
- Guarda token e extrai dados com `jwt-decode`.
- `rememberMe` controla `localStorage` vs `sessionStorage`.

### 8.2 OrderService

- Base: `environment.apiUrl` (`/api/orders`).
- CRUD e endpoints:
  - `getOrders`, `getOrderById`, `getOrderByNr`.
  - `createOrder`, `updateOrder`, `deleteOrder`.
  - `updateOrderStatus` (PUT com query params).
  - `updatePriority` (PATCH).
  - `getHistory` (GET `/orders/{id}/history`).
  - `updateOrderAdm`, `deleteOrderAdm` (sem endpoints equivalentes no backend SDS).

### 8.3 DxfAnalysisService

- Base: `${apiBaseUrl}/dxf-analysis`.
- `getLatestByOrder`, `listHistory` com fallback para 404.

### 8.4 WebsocketService

- Encapsula RxStomp (STOMP).
- Exposicao de `watchOrders`, `watchPriorities`, `watchDxfAnalysis`, `watchStatus`.

### 8.5 OpService

- Usa `environment.apiUrl` para orders e `opApiUrl` opcional para OP.
- `getOrders`, `getOrderByNr`, `getOpByNr`, `openOpPdf`.
- `getOpByNr` usa `/api/ops/{nr}` (funciona no backend atual).
- `getOpPdfUrl` aponta para `/ops/{nr}/arquivo`, que nao existe no backend SDS (pressupoe servico externo).
- `opApiUrl` nao esta definido no environment por padrao.

### 8.6 ClienteService

- Base: `${apiBaseUrl}/clientes`.
- `search`, `getById`, `create`, `update`.

### 8.7 TransportadoraService

- Base: `${apiBaseUrl}/transportadoras`.
- `search`, `getById`, `create`, `update`, `listAll` (size=1000).

### 8.8 UserService

- Base: `${apiBaseUrl}/users`.
- `getAll`, `getAssignableUsers`, `getById`, `create`, `update`, `delete`.

### 8.9 OrderHistoryService

- Base: `${apiBaseUrl}/order-history` (stub).
- `getHistory` retorna mock; backend oficial usa `/api/orders/{id}/history` (OrderService) e funciona.

### 8.10 NotificationService

- Usa `MatSnackBar` para mensagens com classes `msg-success`/`msg-error`.

## 9. Interceptors e Guards

- `auth.interceptor.ts`
  - Injeta JWT no header `Authorization`.
  - Trata 401 (logout + msg) e 403 (msg).

- `auth.guard.ts`
  - Exige autenticacao.
  - Suporta `roles` nas rotas.
  - Redireciona para `/login` com `returnUrl`.

## 10. Models e tipos

Principais modelos em `organizer-front/src/app/models`:

- `orders`: modelo de pedido; maioria dos campos alinhada ao backend, mas inclui `isOpen` (uso local).
- `OrderStatus`: enum (0..8).
- `OrderFilters`: filtros avancados (inclui `isOpen`, nao suportado no backend).
- `CursorPage<T>`: envelope de pagina com cursor.
- `DxfAnalysis`: resultado da analise DXF.
- `User`, `UserRole`, `AssignableUser`, `AuthResponse`.
- `Cliente`, `ClienteEndereco`.
- `Transportadora`.
- `OrderHistory`, `OrderChange`: formato com `changes[]`; backend retorna registros flat (`fieldName`, `oldValue`, `newValue`).
- `WebSocketMessage`: envelope de WS (create/update).

## 11. Componentes (catalogo)

### Base e layout

- `AppComponent`
  - Navbar com status do FileWatcher (`/topic/status`).
  - Exibe usuario logado e logout.
  - Dispara ping inicial via WS.

### Autenticacao

- `LoginComponent`
  - Formulario de login com "remember me".
- `RegisterComponent`
  - Cadastro e auto-login.
- `ForgotPasswordComponent`
  - Solicita reset.
- `ResetPasswordComponent`
  - Valida token e salva nova senha.

### Pedidos e producao

- `OrdersComponent`
  - Lista pedidos ativos (status 0/1/6).
  - Filtra por prioridade.
  - Mostra imagem DXF sob demanda.

- `DeliveryComponent`
  - Seleciona pedidos para entrega/retirada.
  - Atualiza status e dados do entregador.
  - Modal de saida adversa.

- `DeliveryReturnComponent`
  - Retorno de entregas (status 3 -> 5).
  - Agrupa por cliente e exige recebedor.
  - Cancela saida (status 2).

- `MontagemComponent`
  - Acoes de montar/vincar (status 1/6/7).
  - Exibe complexidade DXF (stars).
  - Materiais OP e historico do pedido.

- `RubberComponent`
  - Emborrachamento (status 7/8 + flag).
  - Mostra imagem DXF e materiais.

- `OpComponent`
  - Lista pedidos e abre PDF da OP.

### Entregues e admin

- `DeliveredComponent`
  - Lista entregues/retiradas (paginacao local).
  - Edita detalhes e cria saida adversa.

- `DeliveredAdminComponent`
  - Abas para entregues, pipeline, clientes, transportadoras, usuarios.

- `DeliveredListComponent`
  - Lista entregues/retiradas com filtros avancados.
  - Pagina local e atualizacao via WS.

- `PipelineBoardComponent`
  - Board por status com scroll virtual.
  - Filtros rapidos (prioridade, emborrachada, atrasos).
  - KPI de pronto para entrega e progress bar.

### Cadastros

- `ClientesAdminComponent`
  - Lista e busca clientes; abre `ClienteForm`.
- `ClienteFormComponent`
  - Form completo de cliente + enderecos + transportadora.
- `TransportadorasAdminComponent`
  - Lista e busca transportadoras; abre `TransportadoraForm`.
- `TransportadoraFormComponent`
  - Form basico de transportadora.
- `UsersAdminComponent`
  - CRUD de usuarios (ADMIN).

### Reutilizaveis

- `AdvancedFiltersComponent`
  - Filtros avancados para listas de pedidos.
  - Converte datas para ISO e emite filtros.
- `OrderDetailsModalComponent`
  - Modal de detalhes do pedido.
  - Inclui date-time picker custom para data requerida.
  - Integra DXF (imagem, metricas, historico).
  - Carrega historico de pedido (OrderHistoryService).
- `UserSelectorComponent`
  - Controle custom para selecionar usuarios (chips).
- `FilesizePipe`
  - Formata bytes em KB/MB/GB.

## 12. Estilos e UI

- `styles.css` define estilos globais, modal e date-time picker custom.
- Bootstrap e Material Icons carregados em `index.html`.
- Muitos componentes usam `table-responsive` e cards para mobile.
- `OrderDetailsModalComponent` adiciona UI de analise DXF (stars).

## 13. Ponto de entrada e SSR

Arquivos:

- `main.ts`: bootstrap do browser.
- `main.server.ts`: bootstrap SSR.
- `app.config.ts`: providers (router, hydration, http with fetch + interceptor, animations).
- `app.config.server.ts`: merge para server rendering.

## 14. Integra com backend (principais endpoints)

- Auth:
  - `/api/auth/login`
  - `/api/auth/register`
  - `/api/auth/forgot-password`
  - `/api/auth/validate-token`
  - `/api/auth/reset-password`
- Orders:
  - `/api/orders`
  - `/api/orders/{id}`
  - `/api/orders/nr/{nr}`
  - `/api/orders/create`
  - `/api/orders/update/{id}`
  - `/api/orders/delete/{id}`
  - `/api/orders/{id}/status`
  - `/api/orders/{id}/priority`
  - `/api/orders/{id}/history`
  - `/api/orders/search-cursor` (backend SDS; nao usado no frontend atual)
- DXF:
  - `/api/dxf-analysis/order/{orderNr}`
  - `/api/dxf-analysis/order/{orderNr}/history`
- OPs:
  - `/api/ops/{nr}`
  - `/api/ops/import` (backend SDS; nao usado no frontend atual)
  - `/api/ops/{id}/vincular-faca/{facaId}` (backend SDS; nao usado no frontend atual)
- Clientes:
  - `/api/clientes`
  - `/api/clientes/{id}`
- Transportadoras:
  - `/api/transportadoras`
  - `/api/transportadoras/{id}`
- Users:
  - `/api/users`
  - `/api/users/{id}`
  - `/api/users/assignable`
  - `/api/users/me`

## 15. Status real e pontos de atencao

- **Funciona**: autenticacao (login/register/forgot/reset) e `/api/users/me`; pedidos REST (GET/POST/PUT/DELETE/status/priority/history); entrega/retorno/montagem/emborrachamento via `/api/orders/{id}/status`; historico na Montagem via `OrderService.getHistory`; OP `/api/ops/{nr}` via `OpService.getOpByNr`; clientes/transportadoras CRUD.
- **Funciona com ressalvas**: WS `/topic/orders`, `/topic/prioridades`, `/topic/dxf-analysis`, `/topic/status` (depende STOMP e RPC ping); lista de usuarios com `UserService.getAll` so para ADMIN (DESENHISTA recebe 403); DeliveredList aplica filtros localmente (nao usa `/api/orders/search-cursor`).
- **Nao funciona / nao existe**: delete via WS (frontend publica `/orders/delete/{id}`; backend espera `/app/orders/delete/{id}`); `OrderHistoryService` (mock + endpoint `/api/order-history` inexistente); `updateOrderAdm`/`deleteOrderAdm`; `openOpPdf` usa `/ops/{nr}/arquivo` (ausente no backend).
- **Observacoes**: `orders.isOpen` e `OrderFilters.isOpen` nao suportados no backend; `OrderHistory` frontend usa `changes[]` enquanto backend retorna registros flat; `enviroment.ts` tem `production: true` em dev.
- **Status por tela**: Login/Register/Forgot/Reset OK; Orders OK (WS delete nao); Delivery OK (lista usuarios ADMIN only); DeliveryReturn OK; Montagem OK (historico via `OrderService.getHistory`); Rubber OK; Delivered OK (detalhes usam historico mock); Admin OK para ADMIN (Usuarios) e parcial para DESENHISTA; OP OK para consulta, PDF externo.

## 16. Validacao em ambiente local (2026-01-15)

O que foi validado na infraestrutura atual (sem clique de UI):

- SPA servida via Nginx respondeu `200 OK` em `http://localhost/`.
- Endpoints usados pelo frontend responderam via curl: auth (register/login), `orders` (GET/POST/PATCH/PUT/DELETE), `clientes`, `transportadoras`, `ops/{nr}`, `users/me`.
- `GET /api/dxf-analysis/order/{nr}` respondeu com payload existente (sem imagem) para NR ja presente no banco.

O que nao foi validado nesta rodada:

- Fluxos de UI no navegador (login, pedidos, entrega, etc.).
- WebSocket STOMP (assinaturas e eventos em tempo real).
- Renderizacao de imagem DXF no frontend (campos `imageUrl` vieram nulos no ambiente atual).

## 16. Diagramas ASCII (fluxos principais)

### 16.1 Autenticacao

```
LoginComponent -> AuthService -> /api/auth/login
   |                       |
   |<- token JWT ----------|
   v
AuthService salva token -> AuthInterceptor injeta em requests
```

### 16.2 Pedidos em tempo real

```
OrdersComponent / Delivery / Montagem / Rubber / Delivered
  |  WebsocketService.watchOrders()
  v
STOMP /topic/orders
  |
  v
Atualiza lista local + UI
```

### 16.3 DXF

```
Component -> DxfAnalysisService -> /api/dxf-analysis/...
  |
  v
Exibe imagem e complexidade (OrderDetailsModal, Montagem, Rubber)
```

## 17. Catalogo de arquivos chave

Arquivos de configuracao:

- `organizer-front/package.json`
- `organizer-front/angular.json`
- `organizer-front/tsconfig*.json`
- `organizer-front/src/index.html`
- `organizer-front/src/styles.css`
- `organizer-front/src/main.ts`
- `organizer-front/src/main.server.ts`
- `organizer-front/src/app/app.config.ts`
- `organizer-front/src/app/app.config.server.ts`
- `organizer-front/src/app/app.routes.ts`
- `organizer-front/src/app/enviroment.ts`
- `organizer-front/src/app/enviroment.prod.ts`

Arquivos de seguranca:

- `organizer-front/src/app/guards/auth.guard.ts`
- `organizer-front/src/app/interceptors/auth.interceptor.ts`

Services:

- `organizer-front/src/app/services/auth.service.ts`
- `organizer-front/src/app/services/orders.service.ts`
- `organizer-front/src/app/services/websocket.service.ts`
- `organizer-front/src/app/services/op.service.ts`
- `organizer-front/src/app/services/dxf-analysis.service.ts`
- `organizer-front/src/app/services/cliente.service.ts`
- `organizer-front/src/app/services/transportadora.service.ts`
- `organizer-front/src/app/services/user.service.ts`
- `organizer-front/src/app/services/order-history.service.ts`
- `organizer-front/src/app/services/notification.service.ts`

Models:

- `organizer-front/src/app/models/*.ts`

Components (principais):

- `organizer-front/src/app/components/orders/*`
- `organizer-front/src/app/components/delivery/*`
- `organizer-front/src/app/components/delivery-return/*`
- `organizer-front/src/app/components/montagem/*`
- `organizer-front/src/app/components/rubber/*`
- `organizer-front/src/app/components/delivered/*`
- `organizer-front/src/app/components/delivered-admin/*`
- `organizer-front/src/app/components/delivered-list/*`
- `organizer-front/src/app/components/op/*`
- `organizer-front/src/app/components/login/*`
- `organizer-front/src/app/components/register/*`
- `organizer-front/src/app/components/forgot-password/*`
- `organizer-front/src/app/components/reset-password/*`
- `organizer-front/src/app/components/clientes-admin/*`
- `organizer-front/src/app/components/cliente-form/*`
- `organizer-front/src/app/components/transportadoras-admin/*`
- `organizer-front/src/app/components/transportadora-form/*`
- `organizer-front/src/app/components/users-admin/*`
- `organizer-front/src/app/components/order-details-modal/*`
- `organizer-front/src/app/components/advanced-filters/*`
- `organizer-front/src/app/components/shared/user-selector/*`

Utilitarios:

- `organizer-front/src/app/pipes/filesize.pipe.ts`
