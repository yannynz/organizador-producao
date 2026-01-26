Extração de Atributos Técnicos direto dos DXF
=============================================

Objetivo
--------
Ler atributos técnicos diretamente dos arquivos DXF — aço, vinco (tipo/altura), serrilhas, pertinax, destacador — sem depender do PDF/OP, mantendo a OP apenas para cliente/endereço.

Visão Geral do Fluxo
--------------------
1) FileWatcherApp (`DXFAnalysisWorker`):
   - Monitora pastas, publica `facas.analysis.request`.
   - Processa o DXF, renderiza PNG, extrai atributos técnicos e publica `facas.analysis.result`.
2) Backend (Organizador):
   - Consome `facas.analysis.result`, persiste em `dxf_analysis` com novos campos.
   - API `/api/dxf-analysis/order/{nr}` expõe os atributos.
3) Frontend:
   - Renderiza imagem e exibe atributos técnicos nas telas de borracha/detalhes.

Modelo de Dados (colunas no banco)
----------------------------------
Novos campos em `dxf_analysis` (colunas ja adicionadas via migracoes):
- `steel_type` (varchar): AÇO CS, AÇO BÖHLER, AÇO BX, AÇO RIP etc.
- `vinco_type` (varchar): ex. VINCO BÖHLER, VINCO PADRÃO.
- `vinco_height_mm` (double): ex. 23.6.
- `serrilha_codes` (jsonb): lista de códigos detectados, ex. `[{"code":"5x5","prefix":"X"},{"code":"3x1","prefix":"Y"}]`.
- `pertinax` (boolean).
- `papel_calibrado` (boolean).
- `poliester` (boolean).
- `destacador` (varchar, ex. M/F/MF).
- `raw_attrs` (jsonb): opcional para armazenar strings cruas encontradas.

Extração no FileWatcherApp
--------------------------
Responsável: nova etapa “AttributeExtractor” dentro do `DXFAnalysisWorker`, após análise geométrica e antes da publicação.

Fontes de informação:
- **Layers**: nomes de layer contendo tokens de aço, vinco, serrilha, pertinax/destacador.
- **Text/MTEXT**: textos livres com padrões (regex) para aço/vinco/serrilha/destacador/pertinax.
- **Blocks/Attributes**: nomes de bloco ou atributos contendo os mesmos tokens (se houver).

Configuração (nova seção em `appsettings.Production.json`, exemplo):
```json
"DXFAnalysis": {
  "Attributes": {
    "SteelPatterns": ["AÇO\\s*CS", "AÇO\\s*B(Ö|O)HLER", "AÇO\\s*BX", "AÇO\\s*RIP"],
    "VincoPatterns": [
      "(VINCO\\s*[A-Z]+)\\s*(\\d{1,2}(?:[\\.,]\\d+)?)",
      "VNC\\s*(\\d{1,2}(?:[\\.,]\\d+)?)"
    ],
    "SerrilhaPatterns": ["[XYZ]?\\s*\\d+x\\d+"],
    "PertinaxPatterns": ["PERTINAX", "PTX"],
    "DestacadorPatterns": ["DESTACADOR\\s*([MF]|M/F|MF)", "\\b(M/F|MF)\\b"]
  }
}
```
Notas:
- Regex devem ser flexíveis para abreviações: AÇO BX, AÇO BOLHER (erro ortográfico), VINCO BOLHER 23.6, VNC 23.6, serrilha X5X5/Y3X1/Z... .
- Serrilha: normalizar removendo espaços, maiúsculas; extrair prefixo opcional (X/Y/Z) e código (5x5, 3x1 etc.).
- Vinco: capturar tipo (token textual) e altura numérica (converter para double com `.`).
- Pertinax/Destacador: flags boolean/enum a partir de textos ou layer names.

Compatibilidade/Cache:
- Incluir os novos campos em `DXFAnalysisResult` (C#) e no cache (`DXFAnalysisCache`) para evitar reprocessamento.
- `PersistLocalImageCopy` permanece false; imagens continuam indo para MinIO.

Persistência no Backend
-----------------------
1) Migracoes Flyway criadas:
   - `V20260107__add_dxf_attributes.sql`: `steel_type`, `vinco_type`, `vinco_height_mm`, `serrilha_codes`, `pertinax`, `destacador`, `raw_attrs`.
   - `V20260110__add_dxf_material_flags.sql`: `papel_calibrado`, `poliester`.
2) Mapear no entity `DXFAnalysis` e no DTO `DXFAnalysisView` (pendente).
3) API ja expoe `/api/dxf-analysis/order/{nr}`; incluir os novos campos no payload quando o mapeamento estiver pronto.

Frontend
--------
- Exibir atributos técnicos junto à imagem DXF (tela borracha e detalhes).
- Se `serrilha_codes` for lista, renderizar em chips/labels.
- Mostrar vinco (tipo + altura mm), aço, pertinax (sim/não), destacador (M/F/MF).

Testes e Validação
------------------
- Unit tests no C# para o extractor com strings representativas:
  - “AÇO BX”, “AÇO BOLHER”, “VINCO BOLHER 23.6”, “VNC 23,6”, “X5X5”, “Y3X1”, “DESTACADOR M/F”, “PERTINAX”.
- Replay com DXFs reais de `~/Desktop/ferreira`:
  - Verificar no Postgres `dxf_analysis` se os campos são preenchidos.
  - Conferir `imageUrl` e atributos via `/api/dxf-analysis/order/<nr>`.
- Ambiente:
  - MinIO acessível em 192.168.10.13:9000 (prod) ou variável `APP_DXF_ANALYSIS_IMAGE_BASE_URL` ajustada.
  - RabbitMQ/MinIO endpoints em `appsettings.Production.json` já apontados para 192.168.10.13.

Considerações de Rede
---------------------
- Se precisar servir imagens para LAN e VPN, avaliar proxy via Nginx (`/facas-renders/`) ou hostname com DNS split; manter por ora o endpoint direto 192.168.10.13:9000.
