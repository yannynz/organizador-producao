# Plano: sessao expirada permanece logada

## Escopo

Relatorio de analise e plano de correcao.

Problema relatado:

- Token JWT expira.
- App ainda mostra usuario logado.
- Usuario perde acoes internas.
- Usuario precisa clicar em sair.
- Usuario precisa logar novamente.

## Como funciona hoje

Frontend:

- `organizer-front/src/app/services/auth.service.ts`
- Token fica em `localStorage` ou `sessionStorage`.
- `userSubject` controla o estado visual logado.
- `loadUserFromToken()` decodifica o token.
- `loadUserFromToken()` popula `userSubject`.
- `isAuthenticated()` valida `decoded.exp`.
- `authGuard` usa `isAuthenticated()`.
- `authInterceptor` injeta `Authorization`.
- `authInterceptor` faz logout em `401`.
- `authInterceptor` trata `403` como permissao negada.

Backend:

- `src/main/java/.../infra/security/JwtAuthenticationFilter.java`
- Filtro extrai usuario via `jwtService.extractUsername(jwt)`.
- `JwtService` valida expiracao em `isTokenValid()`.
- Token expirado pode falhar antes disso.
- Excecao pode virar `403`.

## Causa principal

`loadUserFromToken()` nao valida expiracao antes de popular o usuario.

Fluxo problematico:

1. Usuario abre app.
2. Existe token salvo.
3. Token ja expirou.
4. `loadUserFromToken()` decodifica.
5. `userSubject` recebe usuario.
6. Navbar mostra usuario logado.
7. Guardas so rodam em navegacao.
8. Requisicoes falham depois.
9. Se backend retornar `403`, frontend nao faz logout.

Resultado:

- Estado visual fica logado.
- Estado real esta invalido.
- Acoes protegidas nao funcionam.

## Onde corrigir

Frontend:

- `organizer-front/src/app/services/auth.service.ts`
- `organizer-front/src/app/interceptors/auth.interceptor.ts`
- `organizer-front/src/app/guards/auth.guard.ts`
- `organizer-front/src/app/components/login/login.component.ts`

Backend:

- `src/main/java/git/yannynz/organizadorproducao/infra/security/JwtAuthenticationFilter.java`
- `src/main/java/git/yannynz/organizadorproducao/infra/security/JwtService.java`

## Plano de correcao

### 1. Centralizar validade do token

Criar metodo no `AuthService`:

- `getValidToken(): string | null`
- Retorna token somente se existir.
- Retorna token somente se JWT for valido.
- Remove token expirado.
- Limpa `userSubject`.

Usar esse metodo no interceptor.

### 2. Corrigir `loadUserFromToken()`

Antes de popular usuario:

- Buscar token.
- Decodificar token.
- Validar `exp`.
- Se expirado, chamar `clearSession()`.
- Nao chamar `router.navigate()` durante bootstrap.

Separar duas acoes:

- `clearSession()`: limpa token e usuario.
- `logout()`: limpa sessao e navega para login.

Motivo:

- Bootstrap nao deve forcar navegacao.
- Interceptor pode navegar.
- Guard pode navegar.

### 3. Corrigir interceptor

No `authInterceptor`:

- Usar `getValidToken()`.
- Nao enviar token expirado.
- Tratar `401` como sessao invalida.
- Tratar `403` com token expirado como sessao invalida.
- Tratar `403` com token valido como permissao negada.

Motivo:

- Backend pode responder `403`.
- Hoje todo `403` vira permissao.
- Token expirado fica preso.

### 4. Corrigir backend

No `JwtAuthenticationFilter`:

- Capturar `JwtException`.
- Capturar `IllegalArgumentException`.
- Limpar `SecurityContext`.
- Responder `401 Unauthorized`.
- Encerrar o filtro.

Motivo:

- Token expirado e autenticacao invalida.
- Nao deve virar permissao negada.
- Front depende do status correto.

### 5. Validar rota atual

No `authGuard`:

- Usar token valido.
- Se invalido, limpar sessao.
- Redirecionar para login.
- Preservar `returnUrl`.

Motivo:

- Usuario em rota protegida deve sair.
- Usuario volta ao ponto correto apos login.

### 6. Ajustar login

No `LoginComponent`:

- Redirecionar somente se token valido.
- Se token expirado existir, limpar sessao.
- Renderizar login normalmente.

Motivo:

- Evita redirect falso.
- Evita tela presa.

## Testes propostos

Frontend:

- `AuthService` limpa token expirado.
- `loadUserFromToken()` nao popula usuario expirado.
- `authInterceptor` nao injeta token expirado.
- `authInterceptor` faz logout em `401`.
- `authInterceptor` faz logout em `403` com token expirado.
- `authGuard` redireciona token expirado.
- `LoginComponent` nao redireciona token expirado.

Backend:

- Request com token expirado retorna `401`.
- Request com token malformado retorna `401`.
- Request sem token segue fluxo anonimo.
- Request com token valido autentica.

## Testes de integracao necessarios

### Backend: JWT expirado

Arquivo novo:

- `src/test/java/git/yannynz/organizadorproducao/infra/security/JwtAuthenticationIntegrationTest.java`

Configurar:

- `@WebMvcTest(UserController.class)`
- `@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class})`
- `@MockBean UserService`
- `@MockBean JwtService`
- `@MockBean UserDetailsService`
- `@MockBean AuthenticationProvider`

Cenarios:

- `GET /api/users/me` sem token retorna `401`.
- `GET /api/users/me` com token valido retorna `200`.
- `GET /api/users/me` com token expirado retorna `401`.
- `GET /api/users/me` com token malformado retorna `401`.

Validacao esperada:

- `JwtAuthenticationFilter` nao deixa excecao vazar.
- `SecurityContextHolder` fica limpo.
- Status de autenticacao invalida e sempre `401`.

### Frontend: sessao expirada

Arquivo novo:

- `organizer-front/src/app/auth-session.integration.spec.ts`

Configurar:

- `TestBed`
- `provideRouter`
- `provideHttpClient`
- `provideHttpClientTesting`
- `authInterceptor`
- `AuthService`
- `authGuard`

Cenarios:

- Bootstrap com token expirado limpa storage.
- Bootstrap com token expirado mantem `user$` nulo.
- Request HTTP nao injeta token expirado.
- Response `401` limpa storage.
- Response `401` redireciona para `/login`.
- Response `403` com token expirado limpa storage.
- Response `403` com token valido preserva sessao.
- Guard bloqueia rota protegida com token expirado.
- Guard preserva `returnUrl`.

Validacao esperada:

- Usuario nao fica visualmente logado.
- Navbar recebe `user$ = null`.
- Interceptor nao envia credencial invalida.
- Usuario nao precisa clicar em sair.

### Contrato entre backend e frontend

Cenario de ponta a ponta manual:

- Criar token expirado.
- Salvar em `localStorage.auth_token`.
- Abrir `/pedidos`.
- Esperar redirect para `/login?returnUrl=/pedidos`.
- Confirmar ausencia do nome do usuario.
- Logar novamente.
- Confirmar retorno para `/pedidos`.

### Criterio de aceite

- Todos os testes passam.
- Nenhum token expirado permanece salvo.
- Nenhum usuario expirado permanece em `userSubject`.
- Backend retorna `401` para token invalido.
- Front diferencia `403` real de token expirado.

Validacao manual:

- Logar.
- Alterar token para expirado.
- Recarregar app.
- Navbar deve mostrar login.
- Rota protegida deve ir para `/login`.
- Apos login, voltar para `returnUrl`.

## Outro bug encontrado

`refreshUserProfile()` apenas registra erro.

Arquivo:

- `organizer-front/src/app/services/auth.service.ts`

Problema:

- Falha em `/users/me` nao limpa sessao.
- Se interceptor nao classificar erro corretamente, usuario fica stale.

Correcao proposta:

- Se `/users/me` retornar `401`, limpar sessao.
- Se `/users/me` retornar `403` e token estiver expirado, limpar sessao.
- Evitar `console.error` como unica acao.

## Ordem recomendada

1. Adicionar `clearSession()`.
2. Adicionar `isTokenExpired()`.
3. Adicionar `getValidToken()`.
4. Ajustar `loadUserFromToken()`.
5. Ajustar interceptor.
6. Ajustar guard.
7. Ajustar filtro JWT.
8. Criar testes unitarios.
9. Criar testes backend.
10. Validar fluxo manual.

## Resultado esperado

- Token expirado some do storage.
- Navbar nao mostra usuario falso.
- Acoes nao ficam bloqueadas silenciosamente.
- Backend responde `401` corretamente.
- Front redireciona para login.
- Usuario nao precisa clicar em sair.
