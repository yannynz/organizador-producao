# Feature: Listagem de Materiais e Medidas (Montagem)

**Data:** 12/12/2025
**Status:** Implementado e Validado

Este documento detalha as alterações realizadas nos projetos Backend e Frontend para suportar a exibição detalhada de materiais (provenientes da OP/PDF) e medidas técnicas (provenientes do DXF) na tela de Montagem.

---

## 1. Backend (Java/Spring Boot)

### Alterações na API
- **Novo Endpoint:** `GET /api/ops/{nr}`
    - **Controlador:** `OpImportController.java`
    - **Objetivo:** Permitir que o frontend busque os dados brutos da importação da OP (tabela `op_import`) utilizando o número do pedido (`nr`) como chave.
    - **Retorno:** Objeto `OpImport` (JSON), incluindo a lista de `materiais` extraída do PDF.

### Lógica de Serviço
- **Serviço:** `OpImportService.java`
    - **Mapeamento:** Reforçada a lógica de mapeamento para garantir que a lista de materiais (List<String>) recebida via RabbitMQ (`op.imported`) seja corretamente convertida e persistida na coluna `jsonb` do PostgreSQL.

### Testes
- **Novos Testes:**
    - `OpImportControllerTest.java`: Teste de integração web (`@WebMvcTest`) validando o endpoint com dados reais (ex: "MISTA 2PT...", "PICOTE TRAVADO"). Garante retorno 200 OK e JSON correto.
    - `OpImportServiceMaterialsTest.java`: Teste unitário focado na transformação do DTO para Entidade, assegurando que strings complexas de materiais não sejam corrompidas.

---

## 2. Frontend (Angular)

### Componente: Montagem (`montagem.component`)
- **Nova Funcionalidade:** Botão "Materiais" (ícone de lista) adicionado nas visualizações Desktop e Mobile.
- **Modal de Detalhes:**
    - Criado um modal que exibe duas seções principais:
        1.  **Materiais da OP:** Lista não ordenada (`<ul>`) com as descrições literais vindas da OP.
        2.  **Medidas do DXF:** Tabela dinâmica (`keyvalue` pipe) exibindo todas as métricas disponíveis no objeto `metrics` da análise DXF (ex: Área de Borracha, Comprimento de Corte).
- **Lógica de Dados:**
    - Método `verMateriais(nr)`: Dispara duas requisições paralelas:
        *   `OpService.getOpByNr(nr)` para buscar materiais.
        *   `DxfAnalysisService.getLatestByOrder(nr)` para buscar métricas.
    - Estado de carregamento (`loadingMateriais`) gerenciado para feedback visual.

### Serviços
- **Service:** `OpService.ts`
    - Adicionado método `getOpByNr(numeroOp: string)` para consumir o novo endpoint do backend.

---

## 3. FileWatcherApp (.NET - Externo)

### Atualização do Parser (Recomendada)
Para alimentar corretamente a lista de materiais no backend, foi validado e gerado um patch para o arquivo `PdfParser.cs`:

- **Regex Refinado:** `MateriaPrimaBlockRegex` atualizado para parar a leitura em tokens seguros ("Data", "Etapa", "Nº") e evitar capturar rodapés indesejados.
- **Lógica de Limpeza:** Adicionada filtragem para remover linhas de cabeçalho ("Código", "Descrição", "Unid") e linhas contendo apenas códigos numéricos ou unidades soltas, garantindo que apenas a descrição útil do material seja enviada.

---

## 4. Infraestrutura (MinIO)

- **Validação:** Confirmado que o bucket `facas-renders` é criado automaticamente e possui permissões de leitura pública (`download`).
- **Upload:** O worker `DXFAnalysis` está configurado e operando corretamente para enviar os PNGs renderizados para este bucket, permitindo sua visualização no frontend.
