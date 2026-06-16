# Aviso de FileWatcherApp offline na tela inicial

## Identificacao

- Data: 2026-06-16
- Autor: OpenClaude
- Branch: main
- Commit antes: 5207008e
- Commit depois: pendente
- Ambiente alvo: Frontend Angular

## Objetivo

Exibir um popup fechavel e incisivo na tela inicial quando o FileWatcherApp ficar offline por 1 minuto e meio.

## Escopo

- Arquivos alterados:
  - `organizer-front/src/app/app.component.ts`
  - `organizer-front/src/app/app.component.html`
  - `organizer-front/src/app/app.component.css`
  - `docs/reports/2026-06-16-filewatcher-offline-popup.md`
- Servicos afetados: frontend Angular
- Integracoes afetadas: status websocket/RPC ja existente do FileWatcherApp
- Fora de escopo: backend, endpoints, RPC, WebSocket, autenticacao, Docker, banco de dados

## Evidencia antes

```bash
cd /home/ynz/Documents/organizador-producao/organizer-front && npm run build
```

```text
Build concluido com sucesso antes da alteracao.
```

## Mudanca aplicada

- Adicionado controle no `AppComponent` para iniciar temporizador quando o status recebido indicar `online === false`.
- O popup aparece apenas apos 90000 ms continuos offline.
- O popup e fechavel pelo usuario e volta a poder aparecer quando o FileWatcherApp ficar online novamente e depois cair de novo.
- Adicionado markup Bootstrap responsivo para o aviso.
- Adicionado CSS com largura adaptativa para desktop e mobile.

## Evidencia depois

```bash
cd /home/ynz/Documents/organizador-producao/organizer-front && npm run build
```

```text
Build concluido com sucesso apos a alteracao.
Avisos existentes/nao bloqueantes do build Angular:
- bundle initial exceeded maximum budget
- pipeline-board.component.css exceeded maximum budget
- 1 regra CSS ignorada por selector error `.form-floating>~label`
```

## Riscos

- Risco principal: popup aparecer sobre algum conteudo no topo em telas muito pequenas.
- Impacto se falhar: usuario pode nao ver parte do conteudo ate fechar o aviso.
- Como detectar: validar a pagina inicial em desktop e mobile quando status do FileWatcherApp estiver offline.

## Rollback

```bash
git checkout -- organizer-front/src/app/app.component.ts organizer-front/src/app/app.component.html organizer-front/src/app/app.component.css docs/reports/2026-06-16-filewatcher-offline-popup.md
cd /home/ynz/Documents/organizador-producao/organizer-front && npm run build
```

Se a mudanca ja tiver sido commitada:

```bash
git revert <commit-da-mudanca>
cd /home/ynz/Documents/organizador-producao/organizer-front && npm run build
```

## Criterio de aceite

- [x] Fluxo principal validado por build Angular.
- [x] Logs sem erro novo relevante no build.
- [x] Rollback documentado.
- [x] Usuario/operacao sabe como validar: manter FileWatcherApp offline por 90 segundos e confirmar exibicao do popup fechavel.
