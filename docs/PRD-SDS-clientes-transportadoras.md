# PRD & SDS — Clientes e Transportadoras (Admin + FileWatcher)

## 1. Visão Geral
Criar uma nova área de Administração com abas para **Clientes** e **Transportadoras**, permitindo cadastro e manutenção dos dados-chave (localização, horário de funcionamento, nome oficial, apelidos, transportadora associada, último serviço, padrão de entrega). Complementar isso com enriquecimento automático a partir das **Ordens de Produção (OP)** lidas pelo FileWatcherApp, reduzindo lançamentos manuais e preservando todo o funcionamento atual do Organizador e do FileWatcher.

## 2. Problema / Oportunidade
- Hoje o sistema não centraliza informações de clientes/transportadoras, limitando roteirização e histórico de atendimento.
- O cadastro manual de novos clientes é custoso, especialmente em períodos de promoções com alto volume.
- A OP já contém dados úteis (cliente, modalidade de entrega, datas) que não são reaproveitados para um cadastro estruturado.
- Precisamos enriquecer sem quebrar o fluxo existente de pedidos, DXF, WebSockets e watchers.

## 3. Objetivos
1. Disponibilizar aba **Clientes** e aba **Transportadoras** na tela de administração.
2. Registrar/editar localização, horário de funcionamento, nome oficial, apelidos, transportadora vinculada (apenas cliente), último serviço (data/hora) e padrão de entrega (retira x entregamos).
3. Preencher automaticamente o máximo possível desses campos a partir da OP capturada pelo FileWatcher; campos não inferidos ficam vazios para revisão manual.
4. Manter intactos os fluxos atuais (DXF, filas RabbitMQ, telas existentes) e permitir rollback simples via feature flag.

## 4. Escopo
### 4.1 Incluído
- CRUD de clientes e transportadoras na aba Admin, com busca por nome/apelido e destaque de “último serviço”.
- Associação de transportadora a cliente (seleção entre transportadoras já cadastradas).
- Padrão de entrega do cliente (RETIRA / A ENTREGAR) e mesmo atributo para transportadora (quando aplicável).
- Enriquecimento automático via OP (FileWatcher → backend) para criar/atualizar clientes e preencher campos quando possível.
- Atualização automática de “último serviço” a partir de eventos do pedido (status entregue/retirado) e da data/hora da OP quando o pedido ainda não concluiu.

### 4.2 Fora de Escopo
- Multi-transportadora por cliente ou regras de roteirização.
- Geocodificação/rota em mapa, cálculo de frete ou SLA dinâmico.
- App mobile ou autenticação dedicada para transportadoras.
- Migração retroativa massiva de histórico antigo (apenas preenchimento incremental).

## 5. Personas e Casos de Uso
| Persona | Necessidade | Caso de Uso |
| --- | --- | --- |
| Backoffice / Adm | Manter base de clientes/transportadoras atualizada sem planilha externa | Criar/editar cliente, ligar a uma transportadora, ajustar horário de funcionamento |
| Logística | Planejar retirada/entrega rápida | Visualizar padrão de entrega, localização e último serviço do cliente |
| Operação (watcher) | Evitar input manual em promoções | FileWatcher gera/atualiza cadastro ao importar OP |
| TI | Garantir compatibilidade | Habilitar/monitorar flags e logs sem quebrar filas ou WebSockets |

## 6. Requisitos Funcionais
### 6.1 Cadastro de Clientes
- Campos: `id`, `nomeOficial` (obrigatório), `apelidos` (lista de strings), `padraoEntrega` (`RETIRA` | `A_ENTREGAR`), `observacoes` (opcional), `ativo` (bool), `ultimoServicoEm` (timestamp).
- Endereços múltiplos: coleção de endereços com UF, cidade, logradouro, bairro (campos separados), `label` (ex.: Matriz, Filial A), `horarioFuncionamento` (texto livre + etiqueta padrão “Seg-Sex HH:MM-HH:MM”), `padraoEntrega` opcional por endereço, `isDefault`.  
  - A OP define o endereço da faca: tentar casar pelo texto (cidade/UF/CEP). Se achar, gravar `enderecoId` no pedido/OP.  
  - Se o usuário alterar o endereço pelo frontend, prevalece a decisão manual (respeitar manualLock por endereço).  
  - Se não houver match seguro, deixar vazio e marcar “pendente” no pedido.
- `transportadoraId` (nullable) para vínculo.
- Busca por nome/apelido, ordenação por `ultimoServicoEm desc` e paginação.
- Regras de edição: manter `nomeOficial` único (case-insensitive); não sobrescrever campos manualmente preenchidos por dados de baixa confiança vindos da OP.

### 6.2 Cadastro de Transportadoras
- Campos: `id`, `nomeOficial` (obrigatório), `apelidos` (lista), `localizacao`, `horarioFuncionamento`, `ultimoServicoEm`, `padraoEntrega` (quando aplicável), `observacoes`, `ativo`.
- Uso em referência por clientes; não possui `transportadoraId`.

### 6.3 UI (Admin)
- Nova barra de abas em `DeliveredAdminComponent`: `Entregues/Retiradas`, `Produção`, `Clientes`, `Transportadoras`.
- Listagens com colunas principais (nome, apelidos, endereço selecionado, horário, transportadora/padrão entrega, último serviço, status).
- Modal/form para criar/editar com validação mínima e aviso quando campos vieram “auto” da OP (badge).
- Exibir/permitir troca do `enderecoId` da faca; registrar horário de funcionamento aplicado e permitir override manual.
- Ação “associar transportadora” direto na tabela de clientes.

### 6.4 Integração com Pedidos
- `orders` mantém campo string `cliente` (compatibilidade) e recebe novos campos referenciais opcionais: `cliente_id`, `transportadora_id`, `endereco_id`. A `dataRequerida` já existe no pedido (definida no front) e é usada para confrontar o horário de funcionamento do cliente/transportadora.
- Ao criar/atualizar pedido manualmente, usuário pode escolher cliente e endereço; o sistema preenche `cliente` (string) com o `nomeOficial` ou apelido e seta `cliente_id`/`endereco_id`.
- Quando um pedido muda para status entregue/retirado (4/5) ou “tirada” (6), atualizar `ultimoServicoEm` do cliente e, se houver, da transportadora associada.
- `OpImport` armazena referência `cliente_id`/`endereco_id` quando o match for encontrado; se o usuário alterar via front, prevalece o manual.
- Se a `dataRequerida` do pedido cair fora do horário de funcionamento registrado, ainda assim obedecer a `dataRequerida` (pedido) para aquele serviço e apenas gerar flag de alerta/log (não exige novo campo persistido).

### 6.5 Enriquecimento Automático via OP (FileWatcher)
- Ao importar OP, o FileWatcher envia no payload campos adicionais opcionais para o backend:
  - `clienteNomeOficial` (preferir o campo Cliente do PDF, normalizado).
  - `apelidosSugeridos` (derivados do nome do arquivo, alias normalizado, maiúsculas sem acento).
  - `enderecosSugeridos` (lista com UF/cidade/logradouro/bairro, `horarioFuncionamento`, `padraoEntrega` quando inferido).
  - `padraoEntregaSugerido` (derivado de `ModalidadeEntrega` já extraída: `RETIRADA` → RETIRA, `A ENTREGAR` → ENTREGAMOS).
  - `dataUltimoServicoSugerida` (prioridade: data/hora de entrega na OP; fallback: data da OP).
- Backend aplica estratégia **upsert** em clientes:
  - Normaliza `nomeOficial`/apelidos (trim, upper, remove diacríticos) para match antes de criar novo.
  - Match de endereço tenta bater `cidade/UF/CEP` ou trecho igual; se achar, preenche `endereco_id` do pedido/OP.
  - Se encontrou cliente: preenche campos vazios, cria endereço novo se não houver match; não sobrescreve valores manuais nem endereço selecionado pelo usuário.
  - Se não encontrou: cria cliente `origin=OP`, `ativo=true`, com endereço padrão se houver; registra campos vazios como “pendente”.
- Se nenhuma informação for inferida, não cria cliente novo; apenas mantém pedido com `cliente` string habitual.

### 6.6 Qualidade de Dados e Proteções
- Marcar no registro o `source` e `confidence` (`HIGH` para valor explícito, `LOW` para heurística de bloco textual).
- Travar sobrescrita quando `manualLock=true` (campo por atributo) para preservar ajustes humanos.
- Logs claros no backend e FileWatcher quando a OP não fornecer dados suficientes; campos permanecem em branco.

## 7. Requisitos Não Funcionais
- **Compatibilidade**: mensagens RabbitMQ existentes permanecem válidas; novos campos são opcionais no payload e ignorados por versões antigas.
- **Disponibilidade**: enriquecimento não deve atrasar o fluxo principal; operações de cliente rodam em transação curta/independente do fluxo de pedido.
- **Performance**: match de cliente/transportadora/endereço deve ser O(1) por índice em colunas normalizadas; cache in-memory (expiração curta) para nomes normalizados/aliases; limitador de autocriados por minuto para campanhas.
- **Segurança**: sem secrets novos; reutilizar autenticação atual. Dados de endereço seguem dentro do mesmo escopo de acesso atual.
- **Observabilidade**: métricas de acerto (`cliente_auto_match_total`, `cliente_auto_created_total`), contagem de campos vazios e flag `foraHorario` calculada em tempo de requisição (sem persistir); logs com `origin=OP`.

## 8. Métricas de Sucesso
- ≥70% dos novos clientes criados automaticamente durante campanhas promocionais.
- ≤10% de cadastros duplicados após normalização de nome/apelido.
- Tempo adicional no listener OP < 300 ms p95.
- Zero regressões nos fluxos de pedidos/DXF/WebSocket (sem aumento de erros 5xx ou filas travadas).

## 9. Riscos e Mitigações
- **OP incompleta ou ilegível**: fallback para cadastro manual, campos vazios. Mitigar com badge “incompleto”.
- **Duplicidade de clientes**: usar normalização + aliases e bloquear criação se similaridade alta; expor ação de merge manual futura.
- **Sobrescrita indevida**: manualLock por campo e only-fill-empty para dados de baixa confiança.
- **Parsing frágil**: regexes tolerantes já usadas no PdfParser; adicionar testes unitários novos para endereço/horário/padrão entrega.

---

# SDS — Design da Solução

## 10. Arquitetura e Componentes
- **Backend (Spring Boot)**: novos módulos `ClienteService`, `TransportadoraService`, `ClienteRepository`, `TransportadoraRepository`, `ClienteMatcher` (normalização + fuzzy simples), `ClienteAutoEnrichmentService` (consumido pelo `OpImportService`).
- **FileWatcherApp (.NET)**: estender `PdfParser` para extrair sugestões de localização/horário e enviar novos campos opcionais no payload `OpImportRequestDTO` (mantendo compatibilidade JSON).
- **Frontend (Angular)**: novos componentes standalone `ClientesAdminComponent` e `TransportadorasAdminComponent`, rotas/tabs na página Admin, services `ClienteService`/`TransportadoraService` para chamadas REST e badges de origem dos dados.
- **Banco (PostgreSQL)**: novas tabelas `clientes`, `transportadoras`, `cliente_apelido`; colunas opcionais em `orders` e `op_import` para FK; índices em nome normalizado.

## 11. Modelo de Dados Proposto
### 11.1 clientes
| Coluna | Tipo | Notas |
| --- | --- | --- |
| id | bigserial PK | |
| nome_oficial | varchar(180) | único (case/diacrítico insensitive) |
| nome_normalizado | varchar(180) | index para match |
| apelidos | jsonb ou tabela auxiliar | lista de strings normalizadas |
| padrao_entrega | varchar(20) | `RETIRA` | `A_ENTREGAR` |
| observacoes | text | |
| ativo | boolean | default true |
| transportadora_id | bigint FK transportadoras | nullable |
| ultimo_servico_em | timestamptz | atualizado em status 4/5/6 |
| origin | varchar(20) | `OP`, `MANUAL`, `IMPORT` |
| manual_lock_mask | smallint | bitmask por campo (opcional) |
| created_at/updated_at | timestamptz | audit |

### 11.2 cliente_endereco
| Coluna | Tipo | Notas |
| --- | --- | --- |
| id | bigserial PK | |
| cliente_id | bigint FK clientes | index |
| label | varchar(60) | ex.: Matriz, Filial A |
| uf | varchar(2) | |
| cidade | varchar(120) | |
| bairro | varchar(120) | |
| logradouro | varchar(180) | |
| horario_funcionamento | varchar(180) | |
| padrao_entrega | varchar(20) | opcional |
| is_default | boolean | default false |
| origin | varchar(20) | `OP`, `MANUAL` |
| confidence | varchar(10) | `HIGH`/`LOW` |
| manual_lock | boolean | impede sobrescrita |
| created_at/updated_at | timestamptz | |

### 11.3 transportadoras
Mesma estrutura de cliente (sem FK transportadora) e pode ter tabela `transportadora_endereco` com campos equivalentes.

### 11.4 cliente_apelido (opcional caso não use jsonb)
| id | cliente_id | apelido | apelido_normalizado | source (`OP`/`MANUAL`) | unique(apelido_normalizado) |

### 11.5 orders
- Novas colunas opcionais: `cliente_id` (FK clientes), `transportadora_id` (FK transportadoras), `endereco_id` (FK cliente_endereco), `horario_funcionamento_aplicado` (varchar), `fora_horario` (bool).
- Coluna existente `cliente` continua obrigatória para compatibilidade com front atual; ao selecionar cliente/endereço, gravar ambos.

### 11.6 op_import
- Novas colunas opcionais: `cliente_id`, `endereco_id`, além do campo `cliente` existente.

## 12. APIs (REST)
- `GET /api/clientes?search=...&page=&size=` → lista paginada com filtro por nome/apelido/localização.
- `POST /api/clientes` → cria cliente; aceita `apelidos`, `padraoEntrega`, `transportadoraId`.
- `PATCH /api/clientes/{id}` → atualiza campos; permite `manualLock` por campo.
- `GET /api/transportadoras` / `POST` / `PATCH` seguindo o mesmo padrão.
- `PUT /api/orders/{id}/cliente/{clienteId}` → associa cliente a pedido (atualiza string `cliente`).
- WebSocket opcional `/topic/clientes` para refletir alterações na aba Admin sem reload (padrão usado em orders/ops).

## 13. Fluxos Principais
### 13.1 Enriquecimento Automático via OP
1. FileWatcher extrai sugestões (nome, apelidos, endereços com horário/padrão, datas) ao processar a OP.
2. Publica `op.imported` com campos novos opcionais.
3. Backend `OpImportService` chama `ClienteAutoEnrichmentService.upsertFromOp(dto)`:
   - Normaliza nome/apelidos e tenta match por `nome_normalizado` ou alias (cache in-memory + índice).
   - Tenta match de endereço por cidade/UF/CEP/substring; se encontrado, associa `endereco_id` no pedido/OP.
   - Se match: preenche campos vazios, cria endereço novo se necessário, registra origem/confidence e associa `cliente_id`/`endereco_id` em `op_import` e pedido se existir, salvo se o usuário já tiver alterado (manual prevalece).
   - Se não match e houver `nomeOficial`: cria cliente `origin=OP` com endereço sugerido (se houver), `is_default=true` na primeira criação.
4. Pedido segue fluxo normal (status, DXF, WebSocket). Se a `dataRequerida` do pedido estiver fora do horário de funcionamento do endereço escolhido, apenas gerar flag de alerta/log (sem bloquear ou sobrescrever a data).

### 13.2 Atualização de Último Serviço
1. Quando pedido muda para status 4/5/6, disparar atualização `ultimoServicoEm` do cliente e transportadora (se setada) e registrar o `endereco_id` usado.
2. Caso o cliente tenha sido criado via OP mas o pedido ainda não concluiu, usar data/hora da OP como valor inicial (menor confiança) e sobrescrever quando o pedido for entregue/retirado.

### 13.3 Admin UI
1. Usuário abre aba Clientes; front chama `/api/clientes` e exibe lista com badges `AUTO`/`MANUAL`.
2. Em “Novo Cliente”, envia POST; em edição, PATCH com flags de `manualLock`.
3. Ao associar transportadora, front faz PATCH no cliente e, se o pedido aberto estiver linkado, sugere preencher `transportadora_id` nele.

## 14. Observabilidade e Testes
- Métricas Micrometer: `cliente_auto_match_total{result=match|create|skip}`, `cliente_auto_conflict_total`, `cliente_manual_lock_total`.
- Logs estruturados no listener `op.imported` indicando campos preenchidos e campos vazios.
- Testes:
  - Backend unit: matching/normalização, preenchimento parcial, respeito a manualLock.
  - Backend integration: listener OP cria cliente novo e não quebra fluxo de pedido.
  - FileWatcher unit: regex de endereço/horário/padrão entrega com amostras de OP.
  - Frontend: testes de componente para tabs novas e formulários (validação mínima).

## 15. Implantação e Migração
- Flyway: criar tabelas `clientes`, `transportadoras`, FK em `orders`/`op_import`, índices de nome normalizado.
- Feature flag (`app.client.enrichment.enabled=true`, `app.client.autocreate.enabled=true`) para ativar/pausar criação automática.
- Rollout seguro: subir backend com flag off, validar UI CRUD manual; depois habilitar flag e monitorar métricas/duplicidade.
- Não remover ou alterar contratos existentes; novos campos no payload são opcionais para manter compatibilidade com versões anteriores do FileWatcher/Organizador.

## 16. Manutenção e Backlog Futuro
- Ação de merge de clientes duplicados.
- Geocodificação e rota para logística.
- Permitir múltiplas transportadoras por cliente com pesos/prioridades.
- Exportação CSV para conferência periódica de dados.
