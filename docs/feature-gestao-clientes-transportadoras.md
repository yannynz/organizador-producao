# Nova Feature: Gestão de Clientes e Transportadoras (v0.7.0)

**Data:** 30/11/2025
**Status:** Completo

Este documento detalha a implementação da nova camada de gestão de entidades (Clientes e Transportadoras) no sistema Organizador de Produção, substituindo o uso anterior de campos de texto livre por relacionamentos estruturados.

---

## 1. Visão Geral

O objetivo desta feature foi profissionalizar o cadastro de clientes e logística, permitindo:
*   Cadastro único e padronizado de Clientes (CNPJ, IE, Contatos).
*   Gestão de múltiplos endereços de entrega por cliente.
*   Vinculação automática de transportadoras preferenciais.
*   Enriquecimento automático de dados via importação de OPs (RabbitMQ).
*   Resiliência a novos campos de dados externos sem quebrar a aplicação.

---

## 2. Alterações no Banco de Dados (Schema)

Foram criadas três novas tabelas principais e alteradas as existentes (`orders`, `op_import`).

### 2.1. Tabelas Criadas

*   **`transportadoras`**:
    *   `id`, `nome_oficial`, `nome_normalizado` (para busca), `apelidos`, `localizacao`, `horario_funcionamento`, `padrao_entrega`, `ativo`.
*   **`clientes`**:
    *   `id`, `nome_oficial`, `nome_normalizado`, `cnpj_cpf`, `inscricao_estadual`, `telefone`, `email_contato`.
    *   `transportadora_id` (FK): Transportadora padrão do cliente.
    *   `padrao_entrega`, `horario_funcionamento`: Dados logísticos globais.
*   **`cliente_enderecos`**:
    *   `id`, `cliente_id` (FK), `logradouro`, `bairro`, `cidade`, `uf`, `cep`.
    *   `horario_funcionamento`, `padrao_entrega` (específicos por endereço).
    *   `is_default`: Flag para indicar o endereço principal.

### 2.2. Tabelas Alteradas

*   **`orders`**:
    *   Adicionadas FKs: `cliente_id`, `transportadora_id`, `endereco_id`.
    *   Campos de snapshot: `horario_func_aplicado`, `fora_horario`.
    *   *Nota:* O campo legado `cliente` (String) foi mantido para compatibilidade visual imediata, mas o sistema agora prioriza o `clienteRef`.
*   **`op_import`**:
    *   Adicionadas FKs para enriquecimento: `cliente_id`, `endereco_id`.

---

## 3. Backend (Spring Boot)

### 3.1. Serviços e Lógica de Negócio

*   **`OpImportService` (Refatorado):**
    *   Agora, ao importar uma OP, o serviço invoca o `ClienteAutoEnrichmentService`.
    *   **Propagação Automática:** Se o cliente importado tem uma transportadora padrão, ela é automaticamente vinculada ao Pedido (`Order`) recém-criado.
    *   **Sincronização:** A lógica foi replicada no `tryLinkAsync` e `reconcileOpsSemFaca` para garantir que pedidos criados antes da OP (link tardio) também recebam os dados corretos.

*   **`ClienteAutoEnrichmentService` (Novo):**
    *   Responsável por receber o DTO da OP, buscar o cliente pelo `nome_normalizado` (ou criar se não existir).
    *   Atualiza dados cadastrais (CNPJ, IE, Telefone, Email) se vierem no payload.
    *   Gerencia a lista de endereços, tentando fazer *match* com endereços existentes ou criando novos se necessário.
    *   Matching por CEP + logradouro (e UF quando disponível), tolerando variações de bairro/cidade.
    *   Atualização incremental de campos sugeridos quando o endereço não está com `manual_lock`.
    *   Promoção automática do endereço default com base em score de completude.

*   **`ClienteController` (Endpoints adicionais):**
    *   `GET /api/clientes/{id}/enderecos` - lista endereços do cliente.
    *   `GET /api/clientes/{id}/endereco-default` - retorna o endereço padrão (404 se ausente).

*   **`OpImportedListener` (Resiliência):**
    *   O DTO `OpImportRequestDTO` foi blindado com `@JsonIgnoreProperties(ignoreUnknown = true)`. Isso impede que o RabbitMQ trave ou rejeite mensagens se o sistema externo enviar campos novos (como `vendedor`, `obs_producao`) que ainda não foram mapeados.

### 3.2. Entidades e DTOs

*   Mapeamento JPA completo para `Cliente`, `Transportadora`, `ClienteEndereco`.
*   `OpImportRequestDTO` expandido para receber `cnpjCpf`, `inscricaoEstadual`, `telefone`, `emailContato` e lista de endereços sugeridos.

---

## 4. Frontend (Angular)

### 4.1. Admin de Clientes (`app-clientes-admin`)

*   **Nova Tela:** Listagem completa de clientes com busca, filtros e paginação.
*   **Visualização Limpa:**
    *   Endereço principal separado de dados de contato.
    *   Colunas específicas para Logística e Documentos.
    *   Endereço principal passou a ser carregado sob demanda no formulário (não mais listado na tabela).

### 4.2. Formulário de Edição (`app-cliente-form`)

*   **Modal XL:** Interface expandida para suportar a complexidade dos dados.
*   **Gestão de Endereços:**
    *   CRUD completo de endereços dentro do modal do cliente (Adicionar, Remover, Editar).
    *   Definição visual do endereço "Principal".
    *   Carregamento sob demanda dos endereços ao editar clientes existentes.
*   **Campos Completos:** Edição de todos os novos campos (CEP, Email, Horários, etc).

### 4.3. Admin de Transportadoras (`app-transportadoras-admin`)

*   CRUD simples e direto para gestão das transportadoras parceiras.

---

## 5. Validação e Testes

*   **Testes Unitários:** `OpImportServiceTransportadoraTest` valida que a transportadora do cliente é propagada corretamente para o pedido em cenários de importação e link tardio.
*   **Testes de Integração:** A suíte completa (`mvn test`) rodou com sucesso, garantindo que não houve regressão nas regras de negócio de dobras, status ou análise de DXF.
*   **Testes Manuais (Simulados):**
    *   Correção de *bugs* de parse JSON (campos extras no RabbitMQ).
    *   Correção de erros 405 (URL da API no frontend).
    *   Correção de erros de detached entity (ID no save).

---

## 6. Próximos Passos (Recomendados)

1.  **Monitoramento:** Acompanhar os logs do RabbitMQ para verificar se novos campos desconhecidos aparecem com frequência.
2.  **Interface de Pedidos:** Atualizar o Card do Kanban para exibir o nome da Transportadora (agora vindo da relação) de forma mais destacada.
3.  **Relatórios:** Criar relatórios de entrega baseados na nova estrutura de dados (agrupados por Transportadora/Região).
