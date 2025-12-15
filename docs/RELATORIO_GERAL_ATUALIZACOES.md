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

## 🔜 Pendências e Próximos Passos (Snapshot Atual)

### Automação e Inteligência
- **Calibração de Complexidade:** Ajuste fino dos scores para materiais sensíveis e cortes específicos.
- **Busca Cursorial:** Finalizar implementação backend para busca paginada eficiente de pedidos entregues.

### Monitoramento
- **Observabilidade:** Concluir a exibição em tempo real de logs do `FileWatcherApp` no frontend.
