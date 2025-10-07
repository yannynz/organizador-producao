# Atualização de Vinco e Montagem

## Backend (organizador-producao)
- DTO `OpImportRequestDTO`, entidades `OpImport` e `Order` receberam o campo `vaiVinco` e o respectivo manual lock.
- Migração Flyway `V20250926__add_vinco_flags.sql` cria as colunas `vai_vinco` e `manual_lock_vai_vinco` em `op_import` e `orders`.
- `OpImportService` passa a sincronizar o novo indicador com o pedido, respeitando locks e ajustando propagação quando a faca é montada.
- Introduzida `OrderStatusRules.applyAutoProntoEntrega`, promovendo automaticamente pedidos sem emborrachamento para status 2 após montagem/vinco.
- `OrderController` aplica a regra ao receber atualizações manuais e mantém os locks consistentes.

## Front-end (organizer-front)
- `orders` model expõe `vaiVinco` para toda a UI.
- Montagem agora finaliza facas sem vinco (e sem emborrachamento) diretamente como `Pronto para entrega`; vinco continua partindo de `Montada (corte)`.
- Tela de Entregas aceita/status exibe facas `Montada (corte)` e `Montada e vincada`, adicionando coluna e filtro por status.
- Listagens de pedidos e entregues exibem legendas atualizadas para os novos estados.

## Testes
- `OpImportServiceVincoTest` cobre importação e locks do novo flag.
- `OrderStatusRulesTest` valida a promoção automática para `Pronto para entrega`.
- Suite Maven: `mvn test -q`.
