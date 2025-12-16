# Relatório Geral de Atualizações do Sistema

Este documento consolida o histórico de implementações, atualizações e melhorias realizadas no sistema Organizador de Produção, ordenadas cronologicamente.

---

## 📅 30/11/2025 - Gestão de Clientes e Transportadoras (v0.7.0)
**Status:** Completo

Implementação da camada de gestão de entidades para profissionalizar o cadastro de clientes e logística, substituindo campos de texto livre.

### Destaques
- **Cadastro Unificado:** Criação de tabelas para `clientes`, `transportadoras` e `cliente_enderecos`.
- **Enriquecimento Automático:** Importação de OPs via RabbitMQ agora cria ou atualiza dados de clientes automaticamente.
- **Vínculo Logístico:** Associação automática de transportadoras preferenciais aos pedidos.
- **Frontend:** Novas telas de administração (`app-clientes-admin`, `app-transportadoras-admin`) com gestão de endereços múltiplos.

---

## 📅 30/11/2025 - Especificação de Autenticação (Auth v1.0)
**Status:** Planejamento e Especificação

Definição da arquitetura de segurança para substituir a identificação por texto livre.

### Definições
- **Arquitetura:** JWT (Stateless) com Spring Security 6.3.
- **RBAC:** Papéis definidos (`ADMIN`, `DESENHISTA`, `OPERADOR`, `ENTREGADOR`).
- **Auditoria:** Rastreabilidade automática (`created_by`, `updated_by`) em operações críticas.

---

## 📅 06/12/2025 - Release V1: Autenticação, Recuperação e UX
**Status:** Implementado e Verificado

Entrega principal que integrou a autenticação robusta e diversas melhorias de usabilidade.

### 1. Segurança e Acesso
- **Login & Cadastro:** Implementação completa de Login, Cadastro (Self-Service) e Recuperação de Senha via SMTP.
- **Proteção:** Rotas de API e Frontend protegidas por Guards e Interceptors.

### 2. Melhorias de UX/UI
- **Busca Rápida:** Filtros em tempo real por NR ou Cliente nas telas de produção.
- **Visualização de Facas:** Recurso de "Accordion" para exibir renders de DXF diretamente nas listas, com cache local.
- **Mobile First:** Adaptação responsiva de formulários e tabelas.

### 3. Ajustes Pós-Release
- **Visibilidade:** Correção para permitir que `ADMIN` e `DESENHISTA` apareçam nos seletores de atribuição de tarefas.
- **Baixa de Entrega:** Preenchimento automático do entregador logado no formulário de baixa.

---

## 📅 15/12/2025 - Otimização de Sessão e Feedback
**Status:** Implementado

Ajustes focados na retenção de sessão e experiência do usuário durante a reautenticação.

### Atualizações
- **Sessão Estendida:** Validade do token JWT aumentada de **24 horas para 7 dias**. Isso reduz a frequência de logins necessários, ideal para tablets de produção.
- **Notificação de Expiração:** Implementação de interceptor global que detecta erros 401/403 e exibe uma notificação amigável (**"Sessão expirada..."**) via `MatSnackBar` antes de redirecionar para o login, eliminando o "estado zumbi" da aplicação.
- **Padronização Visual:** Estilos globais para mensagens de sucesso e erro.

---

## 📅 15/12/2025 - Sincronização Bidirecional e Histórico (v0.8.0)
**Status:** Implementado e Validado

Implementação de controle total sobre a fila de produção e rastreabilidade de alterações.

### 1. Sincronização Bidirecional (Web ↔ Arquivo)
O sistema agora mantém consistência total entre a interface web e os arquivos físicos na rede.
- **Arquivo → Sistema:** Se um arquivo for renomeado na pasta (ex: mudar sufixo de `_VERMELHO` para `_AZUL`), o sistema detecta a mudança e atualiza a prioridade no banco de dados automaticamente.
- **Sistema → Arquivo:** Alterar a prioridade na tela de "Montagem" dispara um comando para o servidor de arquivos, que renomeia o arquivo físico (`.CNC` ou `.DXF`) instantaneamente.

### 2. Histórico de Alterações (Audit Log)
- **Rastreabilidade:** Todas as alterações de **Prioridade** e **Status** agora são gravadas em uma tabela de histórico dedicada (`order_history`).
- **Dados Gravados:** Data/Hora exata, Usuário responsável (ou "Sistema"), campo alterado, valor antigo e valor novo.
- **Visualização:** Novo botão "Ver Histórico" na tela de Montagem abre um modal detalhando o ciclo de vida da faca.

### 3. Tecnologia (FileWatcherApp)
- O serviço externo C# (`FileWatcherApp`) foi atualizado com um novo consumidor RabbitMQ (`FileCommandConsumer`) para processar comandos de renomeação seguros.

---

## 📅 15/12/2025 - Estabilidade de Inicialização (Hotfix)
**Status:** Corrigido

Correção crítica na arquitetura de inicialização do Frontend para evitar falhas de carregamento em ambientes de produção.

### Correção de Dependência Circular
- **Problema:** Um ciclo de dependência (`AuthService` ↔ `AuthInterceptor` ↔ `HttpClient`) causava o erro `TypeError: r.getToken is not a function` durante a inicialização da aplicação, impedindo o carregamento do perfil do usuário.
- **Solução:** Refatoração da lógica de bootstrap. A chamada `loadUserFromToken()` foi movida do construtor do serviço para o `ngOnInit` do componente raiz (`AppComponent`), garantindo que todas as dependências estejam instanciadas antes do uso.

---

## 📅 16/12/2025 - Ajustes de UI/UX, Funcionalidade e Autenticação (Sessão Atual)
**Status:** Implementado (Aguardando Validações Finais)

Diversas melhorias de usabilidade, adição de funcionalidades e correção de problemas críticos de autenticação.

### Destaques
-   **Melhoria de UI/UX na Tela de Montagem:**
    *   **Problema:** Botões "Ver Imagem", "Ver Histórico" e "Ver Componentes" com layout inadequado no desktop.
    *   **Solução:** Reorganização e agrupamento dos botões com rótulos de texto explícitos para melhor usabilidade no desktop.
    *   **Status:** Resolvido e implementado.
-   **Restrição de Acesso ao Histórico:**
    *   **Problema:** Botão "Ver Histórico" visível para todos os usuários.
    *   **Solução:** Implementação de controle de acesso para exibir o botão "Ver Histórico" apenas para usuários com perfis `ADMIN` ou `DESENHISTA`.
    *   **Status:** Resolvido e implementado.
-   **Adição de "Ver Materiais" na Tela de Emborrachamento:**
    *   **Problema:** Ausência de botão para visualizar materiais/componentes na tela de Emborrachamento.
    *   **Solução:** Adição de funcionalidade e botão "Ver Materiais" com modal para exibição de materiais e métricas DXF.
    *   **Status:** Resolvido e implementado.
-   **Correção na Configuração de Exibição de Imagens DXF:**
    *   **Problema:** Imagens DXF e dados de complexidade não estavam sendo exibidos devido a uma `app.dxf.analysis.image-base-url` vazia e/ou sobrescrita incorretamente por uma variável de ambiente no `docker-compose.yml`.
    *   **Solução:** Atualização da variável de ambiente `APP_DXF_ANALYSIS_IMAGE_BASE_URL` no `docker-compose.yml` para `http://minio:9000/facas-renders`, permitindo o acesso correto ao Minio dentro da rede Docker.
    *   **Status:** Configuração de backend para exibição de imagens corrigida. **Aguardando validação do frontend.**
-   **Resolução do Problema de Re-login de Operadores (Sessão Expirada):**
    *   **Problema:** Operadores não conseguiam fazer login novamente após a sessão expirar, recebendo um `403 Forbidden` ao tentar buscar o perfil após o login.
    *   **Causa:** A configuração de segurança no backend (`SecurityConfiguration.java`) estava restringindo o acesso ao endpoint `/api/users/me` (usado para buscar o perfil do usuário logado) apenas para usuários com a role `ADMIN`.
    *   **Solução:** Alteração da regra de segurança em `SecurityConfiguration.java` para permitir que **qualquer usuário autenticado** (não apenas `ADMIN`) possa acessar o endpoint `/api/users/me`.
    *   **Status:** Resolvido e implementado.

### 🔜 Pendências e Próximos Passos (Snapshot Atual)

#### **Validação Essencial (Sua Ação):**

1.  **Valide a exibição das imagens DXF e da complexidade** no frontend.
2.  **Valide o re-login dos operadores.**
3.  **Verifique o `FileWatcherApp` (serviço externo C#)** para garantir que ele está:
    *   Consumindo mensagens da fila RabbitMQ nomeada `file_commands`.
    *   Processando o `FileCommandDTO` e renomeando os arquivos físicos conforme os comandos `RENAME_PRIORITY` enviados pelo backend Java.

#### **Infraestrutura**
- **Acesso Externo:** Avaliar implantação de Cloudflare Tunnel e PWA para acesso remoto.

#### **Automação e Inteligência**
- **Calibração de Complexidade:** Ajuste fino dos scores para materiais sensíveis e cortes específicos.
- **Busca Cursorial:** Finalizar implementação backend para busca paginada eficiente de pedidos entregues.
