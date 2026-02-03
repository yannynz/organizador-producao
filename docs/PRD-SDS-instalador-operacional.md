# PRD + SDS - Instalador Operacional Unificado (Ubuntu Server)

## 1. Resumo executivo
Este documento define a nova feature de instalacao operacional para o Organizador em maquina fisica Ubuntu Server.

Objetivo central: um fluxo unico de instalacao/reconfiguracao que prepara a maquina, instala as ferramentas operacionais e configura automacoes de backup/restart sem exigir edicao manual de scripts no caso padrao.

Decisoes fixas de produto:
- manter estrutura final em `/home/<usuario>`;
- `update-organizer` e `restore` sao ferramentas manuais;
- backup e restart da aplicacao sao automatizaveis com configuracao facil;
- hostname e IP devem ser detectados automaticamente, com opcao de escolha pelo usuario.

### 1.1 Interpretacao do pedido original (obrigatorio)
O instalador deve refletir estes pontos de negocio, sem ambiguidade:
- instalar aplicacao + ferramenta de update + pasta/scripts de backup mantendo o padrao atual;
- usar `/home/<usuario>` como base de instalacao (`/home/<usuario>/organizador-producao` e `/home/<usuario>/backup_database`);
- nao exigir preenchimento manual no script/pasta de backup para o caso padrao;
- capturar automaticamente nome da maquina (hostname) e IP, com opcao de escolha do IP;
- incluir ferramenta de restore completo no pacote de instalacao;
- automatizar apenas backup e restart da aplicacao, mantendo update/restore como uso manual.

---

## 2. Contexto e problema
Troca de maquina fisica hoje envolve varias etapas manuais:
- copiar projeto e scripts para caminhos corretos;
- alinhar IP/hostname em variaveis de ambiente;
- configurar automacao de backup e restart;
- garantir comandos operacionais padronizados.

Isso aumenta risco de:
- erro operacional na primeira subida;
- configuracao inconsistente entre app, backup e MinIO;
- indisponibilidade por falta de agendamento.

---

## 3. Objetivos de produto
1. Reduzir setup inicial para poucos passos guiados.
2. Preservar exatamente a estrutura de pastas ja usada em producao.
3. Centralizar configuracao operacional em um arquivo unico.
4. Criar automacao de backup/restart com timers configuraveis.
5. Permitir reconfigurar sem reinstalar tudo.

### 3.1 Metricas de sucesso
- Tempo medio de instalacao completa <= 15 minutos.
- 0 edicoes manuais obrigatorias para backup basico.
- 100% dos ambientes novos com estrutura em `/home/<usuario>` padronizada.
- Reexecucao do instalador sem quebrar ambiente existente (idempotencia).

---

## 4. Escopo

### 4.1 Incluido
- Instalador CLI para Ubuntu Server (`installer/install-organizer.sh`).
- Deteccao automatica de hostname e IP com confirmacao pelo usuario.
- Provisionamento da estrutura:
  - `~/organizador-producao`
  - `~/backup_database`
- Instalacao dos utilitarios:
  - `update-organizer` (manual)
  - `backup_postgres.sh` (manual + automatizavel)
  - `restore_backup.sh` (manual)
- Criacao de links operacionais em `/usr/local/bin`.
- Configuracao de servicos/timers systemd:
  - startup da stack no boot;
  - backup periodico;
  - restart periodico da stack.
- Modo de reconfiguracao de agendas sem reinstalar (`--reconfigure-schedules`).
- Fluxo de migracao de maquina fisica (instalar, subir stack e permitir restore manual imediato com comando unico).

### 4.2 Fora de escopo
- Alterar regra de negocio do backend/frontend.
- Automatizar `update-organizer`.
- Automatizar `restore_backup.sh`.
- Mudar topologia do `docker-compose.yml` atual.

---

## 5. Requisitos funcionais (RF)

### 5.1 Descoberta de ambiente
- RF-01: detectar hostname automaticamente.
- RF-02: detectar IPv4 validos das interfaces ativas.
- RF-03: sugerir IP da rota default.
- RF-04: permitir escolher outro IP detectado ou informar IP manual.
- RF-05: persistir IP/hostname no arquivo central de ambiente.

### 5.2 Provisionamento
- RF-06: validar/criar pastas padrao em `/home/<usuario>`.
- RF-07: sincronizar repositorio para o caminho padrao da maquina.
- RF-08: instalar scripts com permissao executavel adequada.
- RF-09: instalar guias e comandos de operacao rapida.
- RF-09.1: suportar migracao de ambiente existente sem alterar o destino final em `/home/<usuario>`.

### 5.3 Ferramentas operacionais
- RF-10: instalar `update-organizer` com comportamento equivalente ao atual.
- RF-11: instalar backup completo (Postgres + MinIO).
- RF-12: instalar restore completo (Postgres + MinIO).
- RF-13: exibir resumo final com comandos manuais.
- RF-13.1: backup deve funcionar no modo padrao sem edicao manual de variaveis/scripts.

### 5.4 Automacao
- RF-14: opcao de startup automatico da stack no boot.
- RF-15: opcao de agenda para backup automatico.
- RF-16: opcao de agenda para restart automatico da stack.
- RF-17: garantir que `update` e `restore` fiquem fora da automacao.
- RF-18: permitir reconfiguracao posterior das agendas.

### 5.5 Robustez
- RF-19: idempotencia em reexecucao.
- RF-20: validar dependencias criticas antes da aplicacao.
- RF-21: exibir status final de servicos/timers.

---

## 6. Requisitos nao funcionais (RNF)
- RNF-01: operacao principal via CLI simples e com mensagens acionaveis.
- RNF-02: logs rastreaveis via `journalctl` e arquivo de backup.
- RNF-03: seguranca basica de permissoes em scripts e config.
- RNF-04: compatibilidade com futuras atualizacoes sem reinstalacao total.
- RNF-05: minimizar dependencias externas alem de Docker, systemd e utilitarios shell.

---

## 7. Jornada do usuario
1. Usuario executa o instalador com sudo.
2. Instalador valida pre-condicoes (docker, compose, systemd, ip, rsync).
3. Instalador detecta hostname/IP e pede confirmacao.
4. Usuario escolhe politicas:
   - startup no boot (sim/nao)
   - agenda de backup
   - agenda de restart
5. Instalador provisiona pastas, scripts, links e arquivo `/etc/organizer/organizer.env`.
6. Instalador cria/atualiza units e timers systemd.
7. Instalador mostra resumo final e comandos manuais.

### 7.1 Jornada de troca de maquina fisica
1. Levar backups antigos para `~/backup_database` na nova maquina.
2. Executar instalador e confirmar IP/hostname/agendas.
3. Validar servicos (`organizer-stack.service`, timers).
4. Executar restore manual quando necessario (`organizer-restore --choose`).
5. Seguir operacao normal com backup/restart automaticos e update manual.

---

## 8. SDS - desenho tecnico

## 8.1 Componentes
- **Installer CLI**: orquestra instalacao e reconfiguracao.
- **Env Resolver**: descobre hostname/IP e resolve valores finais.
- **Config Writer**: grava configuracao central em `/etc/organizer/organizer.env`.
- **Ops Installer**: instala scripts e links globais.
- **Systemd Manager**: escreve units/timers e aplica `daemon-reload`.
- **Validator**: mostra status final dos componentes.

## 8.2 Estrutura alvo de diretorios
```text
~/
  organizador-producao/
    update-organizer
    installer/install-organizer.sh
    scripts/ops/
    docker-compose.yml
    ...
  backup_database/
    backup_postgres.sh
    restore_backup.sh
    BACKUP_GUIDE.md
    backuplog.txt
    backup_*.dump.gz
    backup_minio_*.tar.gz
```

## 8.3 Comandos globais instalados
- `/usr/local/bin/update-organizer`
- `/usr/local/bin/organizer-backup`
- `/usr/local/bin/organizer-restore`
- `/usr/local/bin/organizer-installer`

## 8.4 Arquivo central de configuracao
Arquivo: `/etc/organizer/organizer.env`

Campos principais:
- usuario e paths (`TARGET_USER`, `BASE_DIR`, `ORGANIZER_DIR`, `BACKUP_DIR`)
- identificacao de host (`HOSTNAME_VALUE`, `SERVER_HOST`, `MINIO_HOST`)
- endpoint de imagem DXF (`APP_DXF_ANALYSIS_IMAGE_BASE_URL`)
- parametros de backup/restore (Postgres/MinIO)
- politicas de automacao (`STARTUP_ENABLED`, `BACKUP_TIMER_SPEC`, `RESTART_TIMER_SPEC`)

Este arquivo e a fonte de verdade para scripts e servicos.

## 8.5 Servicos e timers systemd
- `organizer-stack.service`
  - sobe stack docker compose no boot (quando habilitado).
- `organizer-backup.service` + `organizer-backup.timer`
  - executa backup de Postgres + MinIO.
- `organizer-restart.service` + `organizer-restart.timer`
  - executa restart da stack.

### 8.5.1 Modelo de agenda
Formato interno:
- `calendar|<expr>` -> mapeia para `OnCalendar=<expr>`
- `interval|<duracao>` -> mapeia para `OnUnitActiveSec=<duracao>`
- `disabled` -> timer removido/desabilitado

Opcoes rapidas esperadas no fluxo:
- desabilitado
- diario (02:00)
- semanal (domingo 04:00)
- a cada 6h
- a cada 12h
- customizado (`OnCalendar` ou `OnUnitActiveSec`)

## 8.6 Ferramentas manuais versus automaticas
- **Manuais (sem timer):**
  - `update-organizer`
  - `organizer-restore`
- **Automaticas (com timer opcional):**
  - `organizer-backup`
  - `organizer-restart.service`

## 8.7 Regras de idempotencia
- reexecutar instalador deve atualizar artefatos sem duplicar estrutura;
- units/timers devem ser sobrescritos com configuracao nova;
- links em `/usr/local/bin` devem apontar para a versao atual;
- reconfiguracao de agenda nao deve reinstalar todo o ambiente.

---

## 9. Criterios de aceite
- CA-01: instalacao completa em maquina nova por um fluxo guiado unico.
- CA-02: estrutura em `/home/<usuario>` preservada como padrao operacional.
- CA-03: `update-organizer` e `organizer-restore` instalados e manuais.
- CA-04: backup automatico executa no horario definido.
- CA-05: restart automatico executa no horario definido.
- CA-06: alteracao de agenda funciona via `--reconfigure-schedules`.
- CA-07: reexecucao do instalador nao corrompe instalacao anterior.
- CA-08: resumo final mostra status dos servicos/timers.
- CA-09: em instalacao padrao, o operador nao precisa editar nada em `~/backup_database` para o backup funcionar.
- CA-10: troca de maquina fica operacional apos install + restore manual, sem ajustes manuais de IP/hostname nos scripts.

---

## 10. Riscos e mitigacoes
- Risco: escolha de IP incorreta.
  - Mitigacao: lista de IPs detectados + modo de reconfiguracao.
- Risco: dependencia ausente no host.
  - Mitigacao: pre-check bloqueante com mensagem objetiva.
- Risco: conflito de janela backup/restart.
  - Mitigacao: orientar janelas separadas na instalacao e documentacao.
- Risco: divergencia entre maquinas.
  - Mitigacao: arquivo central em `/etc/organizer/organizer.env`.

---

## 11. Plano de rollout
1. Entregar scripts e documentacao no repositorio.
2. Validar instalacao limpa em Ubuntu Server sem estado previo.
3. Validar migracao de ambiente existente usando instalador.
4. Publicar guia operacional no README e treinar operadores.

---

## 12. Itens futuros (nao bloqueantes)
- backup de secrets externos com cifragem automatica.
- healthchecks pos-restart com rollback opcional.
- checklist de hardening (firewall, usuarios, least privilege).
- painel CLI simples para status consolidado da operacao.
