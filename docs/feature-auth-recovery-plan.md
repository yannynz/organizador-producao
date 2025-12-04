# Feature: Recuperação de Senha (Forgot Password)

**Status:** Planejamento (Draft)
**Dependência:** feature-auth-v1.md

Este documento detalha a implementação da funcionalidade "Esqueci minha senha" respeitando a premissa de **não utilizar serviços externos pagos (SaaS)** (como Auth0, SendGrid, Firebase).

A solução utiliza o protocolo padrão **SMTP**, permitindo conexão com servidores de e-mail corporativos existentes (Exchange, Postfix) ou contas genéricas (Gmail/Outlook) configuradas no próprio backend.

---

# 1. Visão Geral do Fluxo

1.  **Solicitação:** Usuário informa o e-mail na tela de login.
2.  **Geração:** Backend verifica se o e-mail existe. Se sim, gera um token único (UUID) com validade curta (ex: 30 min).
3.  **Envio:** Backend envia um e-mail simples contendo um link: `https://app.interno/reset-password?token=xyz...`.
4.  **Redefinição:** Usuário clica no link, o Frontend valida o token (via API) e apresenta o formulário de nova senha.
5.  **Conclusão:** Backend atualiza a senha e invalida o token.

> **Nota:** Em ambientes totalmente isolados (sem internet e sem servidor de e-mail interno), esta funcionalidade não poderá ser ativada. Nesses casos, a recuperação deve ser feita via **Admin (Reset Manual)**.

---

# 2. Backend (Spring Boot)

## 2.1. Dependências (`pom.xml`)
Necessário adicionar o suporte a envio de e-mail do Spring Boot.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

## 2.2. Banco de Dados (Flyway)
Criar tabela para armazenar os tokens de recuperação. Não armazenar o token no `users` para permitir múltiplos pedidos ou histórico, e por segurança.

**Arquivo:** `db/migration/V2025...__create_password_reset_tokens.sql`

```sql
CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_pwd_reset_token ON password_reset_tokens(token);
```

## 2.3. Modelo e Repositório

*   **Entity:** `PasswordResetToken` (mapeando a tabela acima).
*   **Repository:** `PasswordResetTokenRepository` com método `findByToken(String token)`.

## 2.4. Serviços

### `EmailService` (Novo)
Responsável pelo envio técnico via `JavaMailSender`.
*   Método: `sendPasswordResetEmail(String to, String token)`
*   Template: Texto simples ou HTML básico embutido no código (para evitar complexidade de template engines por enquanto).

### `AuthService` (Extensão)
*   `forgotPassword(String email)`:
    *   Busca usuário. Se não achar, retorna OK (silencioso) para evitar enumeração de usuários (Security Best Practice).
    *   Se achar, cria token, salva no DB e chama `EmailService`.
*   `validateResetToken(String token)`:
    *   Verifica existência e data de expiração (`expiryDate > now`).
*   `resetPassword(String token, String newPassword)`:
    *   Valida token.
    *   Atualiza senha do usuário (BCrypt).
    *   Deleta o token (ou marca como usado).

## 2.5. Controller (`AuthController`)
*   `POST /auth/forgot-password`: Body `{ "email": "..." }`
*   `GET /auth/validate-token`: Query param `?token=...` (opcional, para o front saber se mostra o form ou erro antes do submit).
*   `POST /auth/reset-password`: Body `{ "token": "...", "newPassword": "..." }`

## 2.6. Configuração (`application.properties`)
Parâmetros padrão vazios, a serem preenchidos via variáveis de ambiente no Docker (`docker-compose.yml`).

```properties
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

---

# 3. Frontend (Angular)

## 3.1. Novas Rotas
No `app.routes.ts`:
*   `/forgot-password`: Componente público.
*   `/reset-password`: Componente público (mas requer query param `token`).

## 3.2. Componentes

### `ForgotPasswordComponent`
*   **UI:** Input de E-mail e Botão "Enviar Link de Recuperação".
*   **Logica:** Chama API. Ao sucesso, exibe mensagem: "Se o e-mail estiver cadastrado, você receberá instruções em breve." (Feedback seguro).

### `ResetPasswordComponent`
*   **UI:** Inputs "Nova Senha" e "Confirmar Senha".
*   **OnInit:** Captura o token da URL. Se não houver, redireciona pro login. Pode validar o token imediatamente via API para mostrar erro se já estiver expirado.
*   **Submit:** Envia `{ token, password }` para a API.
*   **Sucesso:** Exibe "Senha alterada com sucesso" e redireciona para Login.

---

# 4. Plano de Execução

1.  **Infra:** Adicionar dependência no `pom.xml` e variáveis no `docker-compose.yml`.
2.  **DB:** Criar script de migração Flyway.
3.  **Backend:** Implementar Entidade, Repo, EmailService e lógica no AuthService.
4.  **API:** Expor endpoints no AuthController e liberar no SecurityConfig (`requestMatchers("/auth/**").permitAll()`).
5.  **Frontend:** Criar telas e integrar.
6.  **Teste:** Validar fluxo completo (solicitação -> recebimento e-mail -> troca senha -> login com nova senha).
