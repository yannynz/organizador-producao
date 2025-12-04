FileWatcherApp 0.3.0 (vs 0.2.x)

  - O commit b2971d8 introduziu o pipeline completo de análise DXF dentro
    do próprio serviço .NET: Program inicializa opções e serviços para pré-
    processamento, análise, renderização, cache, storage S3 e o worker
    dedicado que consome/publica em RabbitMQ (FileWatcherApp/Program.cs:5,
    FileWatcherApp/Services/DXFAnalysis/DXFAnalysisWorker.cs:1).
  - HandleDobrasFileAsync agora bloqueia o envio para a fila
    dobra_notifications quando o arquivo não tiver os sufixos .m.dxf/.dxf.fcd,
    mas ainda dispara a análise para gerar score e preview; somente versões
    “salvas” seguem para o fluxo de produção (FileWatcherApp/Services/
    FileWatcher/FileWatcherService.cs:587).
  - O motor de scoring passou a ser configurável (serrilha, materiais
    sensíveis, cortes secos) e expõe explicações detalhadas, conforme descrito
    em ComplexityScorer e na doc recém-adicionada (FileWatcherApp/Services/
    DXFAnalysis/ComplexityScorer.cs:1, FileWatcherApp/docs/complexidade-
    facas.md:1).
  - Renderização + upload para MinIO/S3 foram encapsulados em DXFImageRenderer
    e IImageStorageClient, permitindo que o Organizer consuma URLs públicas
    em vez de caminhos Windows (FileWatcherApp/Services/DXFAnalysis/
    DXFImageRenderer.cs:1, FileWatcherApp/docs/dxf-image-storage.md:1).
  - Ferramentas auxiliares (Tools/DxfFixtureGenerator, Tools/DxfInspector) e
    novos artefatos/fixtures foram incluídos para testes e calibração.

  Organizador Producao 0.6.9 (vs 0.6.8)

  - O commit c053a3b1 adicionou o domínio de DXF ao backend Spring: entidade/
    tabela dxf_analysis, repositório e serviço responsável por normalizar
    o payload, relacionar com a OP e guardar métricas, imagem e explicações
    (organizador-producao/src/main/java/git/yannynz/organizadorproducao/
    service/DXFAnalysisService.java:1).
  - Um listener RabbitMQ consome facas.analysis.result e publica via
    WebSocket /topic/dxf-analysis, enquanto o publisher REST permite
    que o Organizer force análises sob demanda enviando mensagens para
    facas.analysis.request (organizador-producao/src/main/java/git/yannynz/
    organizadorproducao/service/DXFAnalysisResultListener.java:1, .../
    service/DXFAnalysisRequestPublisher.java:1, .../controller/
    DXFAnalysisController.java:1).
  - O frontend Angular ganhou o modelo/serviço DxfAnalysis + FilesizePipe
    e o modal de detalhes da OP agora exibe score (com estrelas
    fracionárias), histórico, status de upload e preview do render resolvido
    automaticamente para URL pública ou fallback local (organizador-producao/
    organizer-front/src/app/models/dxf-analysis.ts:1, .../services/dxf-
    analysis.service.ts:1, .../components/order-details-modal/order-details-
    modal.component.ts:1).
  - Documentação, scripts e dashboards foram atualizados para cobrir o novo
    fluxo ponta a ponta (organizador-producao/docs/dxf-complexity-engine.md:1,
    docs/pending-updates.md:1, monitoring/grafana/provisioning/dashboards/json/
    organizador-backend.json).
  - Flyway trouxe duas migrações: criação da tabela e ampliação
    para campos de storage (organizador-producao/src/main/
    resources/db/migration/V20251020__create_dxf_analysis.sql, .../
    V20251028__augment_dxf_analysis_storage.sql).

  Backups e script atual

  - Existem dumps válidos em backup_database/backup_2025-11-05_02-00-01.dump.gz
    e backup_2025-11-06_02-00-02.dump.gz, cobrindo todo o banco via pg_dump
    -F c.
  - backup_postgres.sh já exporta, comprime, mantém apenas os últimos
    dois arquivos e replica para a máquina remota via rsync+SSH, com logs
    persistidos (backup_database/backup_postgres.sh:1).
  - Com as novas features, o backup precisa incluir (a) o schema dxf_analysis
    recém-criado e (b) os renders hospedados no bucket/MinIO para que seja
    possível restaurar imagens exibidas no Organizer; atualmente apenas o banco
    está sendo preservado.

  Plano detalhado de implementação ponta a ponta

      1. Inventário e preparação: validar que os dois backups recentes abrem
         com pg_restore --list, tirar snapshot manual antes das mudanças e
         documentar o ponto de retorno; mapear o host onde o FileWatcher roda
         (Windows) e onde o Organizer está em Docker para saber em que ordem
         publicar.
      2. Atualizar FileWatcherApp: criar branch a partir de b2971d8, revisar
         appsettings.{Environment}.json para preencher DXFAnalysis (pastas,
         RabbitMQ, MinIO) e calibrar os pesos pedidos no README; rodar dotnet
         publish nos dois targets (linux/windows) e validar localmente o
         fluxo dobros→analysis garantindo que somente .m.dxf/.dxf.fcd vão para
         dobra_notifications (logs em FileWatcherService).
      3. Provisionar infraestrutura complementar: subir/validar MinIO
         (ou S3 real) com bucket facas-renders, aplicar política pública
         ou gerar URL assinada, e garantir que o RabbitMQ tem as filas
         facas.analysis.request/result declaradas antes de iniciar o worker;
         alinhar o relógio e timezone porque o score adiciona timestamps ISO
         usados no backend.
      4. Atualizar Organizador backend: parar docker compose, aplicar as
         migrações Flyway em staging usando um dump recente, verificar se
         o schema dxf_analysis aparece no mesmo banco (\dt dxf_analysis),
         configurar app.dxf.analysis.* no application.properties ou variáveis
         do compose (filas, base URL de imagem, diretórios locais bridando o
         FileWatcher) e só então subir containers para que o listener comece a
         consumir; monitorar logs para garantir sucesso no binding do STOMP /
         topic/dxf-analysis.
      5. Atualizar frontend Angular: preencher environment.ts/.prod.ts com
         imagePublicBaseUrl apontando para o MinIO/Nginx que expõe os renders,
         fazer npm run build --configuration=production, validar o modal
         novo em staging (score fracionário, warnings de upload, histórico e
         preview) e só então reconstruir a imagem Docker usada em produção.
      6. Teste end-to-end guiado: copiar um DXF real para a pasta monitorada,
         confirmar no RabbitMQ que a mensagem entrou na fila de request,
         acompanhar o worker até publicar o resultado com URL pública, checar
         o backend via GET /api/dxf-analysis/order/{NR} e garantir que o
         websocket atualiza o modal sem reload; repetir com arquivos com e sem
         sufixo .m.dxf para verificar o bloqueio correto no fluxo Dobras.
      7. Estender cobertura de backup: após as migrações, executar
         backup_postgres.sh manualmente para registrar o novo schema e, em
         seguida, criar etapa adicional no script (ou em cron separado) que usa
         mc mirror facas-renders local/backup/minio e envia o TAR para o mesmo
         host remoto; documentar também como restaurar o bucket (mc mirror
         inverso) junto ao pg_restore.
      8. Observabilidade e checklist final: validar os novos contadores
         Micrometer (organizador_dxf_*) e os dashboards Grafana atualizados,
         configurar alertas para falha de upload/ausência de resultados, e
         manter docs/pending-updates.md atualizado conforme calibragens forem
         aplicadas; somente depois disso gerar uma tag/hotfix.
      9. Plano de rollback: caso algo falhe, reverter o stack Docker ao commit
         0.6.8 (imagens atuais), derrubar o worker DXF e restaurar o dump pré-
         alteração; os renders em MinIO devem ser versionados junto com a nova
         rotina de backup para permitir retorno rápido.

  Próximos passos naturais: 1) definir exatamente como estender o backup
  para o bucket MinIO (ex.: mc mirror + retenção), 2) ajustar os pesos do
  ComplexityScorer conforme os alvos descritos no README e em docs/pending-
  updates.md, ou 3) ensaiar a atualização em staging usando um dos dumps
  listados para validar rollback.
