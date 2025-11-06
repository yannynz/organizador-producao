# Pending DXF & Dobras Updates

## Context Snapshot (2025-11-01 15:40 UTC)
- Última rodada de execução registrou scores idênticos (5.0) para vários DXFs recém-analisados.
- Objetivo de calibrar `NR 120253` (~1.7) e materiais sensíveis (`NR 120247` ≈ 4.0) segue pendente.
- Busca paginada (`search-cursor`) segue pendente; a tela voltou a operar em modo local até que os filtros JPA sejam implementados.
- Repositório custom de pedidos (`OrderRepositoryImpl`) continua sem filtros implementados; TODO placeholder intacto.
- Ajustes de logs/observabilidade do FileWatcherApp foram parcialmente aplicados; resta concluir a rotina de exibição em tempo real no frontend.

## Próximas Ações Prioritárias
1. **Recalibração de Score**
   - Ajustar pesos de `ComplexityScorer` (serrilha pequena, materiais delicados, corte seco) para atender metas: `NR 120253 → ~1.7`, `NR 120247 → 3.7-4.0`.
   - Atualizar `ComplexityCalibrationTests` com novos alvos e fixtures correspondentes.
2. **Busca Entregues com Cursor**
   - Implementar filtros no `OrderRepositoryImpl.searchDeliveredByCursor`.
   - Cobrir com testes de integração/back-end se possível.
3. **Documentação**
   - Registrar calibragem final (`docs/complexidade-facas.md`) e fluxo front-end/back-end (docs do Organizador).
   - Descrever campos adicionados no modal e mapping scoring → estrelas.

## Concluídos em 2025-11-01
- Lista de entregues voltou ao modo local (paginando apenas facas status 4/5 no front) enquanto a busca cursorial aguarda implementação no backend.
- Modal de detalhes ganhou a sessão de imagem do MinIO (`/api/dxf-analysis/{analysisId}/image`), destacando bucket/key e metadados.
- Docker Compose injeta `APP_DXF_ANALYSIS_IMAGE_BASE_URL=http://localhost:9000/facas-renders`, alinhando o backend com o MinIO padrão da stack.
