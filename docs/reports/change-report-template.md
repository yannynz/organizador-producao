# Template Obrigatorio de Report de Mudanca

Use este modelo para toda mudanca funcional, operacional ou de infraestrutura.

## Identificacao

- Data:
- Autor:
- Branch:
- Commit antes:
- Commit depois:
- Ambiente alvo:

## Objetivo

Descreva o problema ou melhoria em uma frase direta.

## Escopo

- Arquivos alterados:
- Servicos afetados:
- Integracoes afetadas:
- Fora de escopo:

## Evidencia antes

Inclua comandos, logs, prints ou respostas HTTP que provem o estado anterior.

```bash
# comandos usados
```

```text
# saida relevante
```

## Mudanca aplicada

Liste as alteracoes tecnicas objetivas.

## Evidencia depois

Inclua os smoke tests e resultados.

```bash
# comandos de validacao
```

```text
# resultado esperado/obtido
```

## Riscos

- Risco principal:
- Impacto se falhar:
- Como detectar:

## Rollback

Descreva o rollback executavel, sem depender de memoria.

```bash
git revert <commit>
docker compose build <servico>
docker compose up -d --force-recreate <servico>
```

## Criterio de aceite

- [ ] Fluxo principal validado.
- [ ] Logs sem erro novo relevante.
- [ ] Rollback documentado.
- [ ] Usuario/operacao sabe como validar.
