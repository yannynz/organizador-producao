# Incidente 2026-05-24 - Backend indisponivel por crash da JVM

## Resumo

O sistema em producao respondia o frontend, mas falhava em chamadas para `/api/orders` com `502 Bad Gateway`.
O erro nao estava relacionado a banco, CORS, VPN, senha ou volume Docker. O Nginx local do servidor nao conseguia
encaminhar para o backend porque o processo Java do `backend-container` havia morrido.

## Ambiente afetado

- Servidor LAN: `192.168.10.13`
- Servidor VPN: `10.122.76.196`
- Host observado: `recepcao@recepcao:~/organizador-producao`
- Branch base local: `main`
- Commit de referencia local no momento do report: `9f392ae4`
- Data do registro: `2026-05-23 21:50:02 -03`

## Sintomas

```bash
curl -i http://127.0.0.1/api/orders
```

Resultado observado no servidor:

```text
HTTP/1.1 502 Bad Gateway
Server: nginx/1.29.4
```

Teste direto no backend:

```bash
curl -i http://127.0.0.1:8081/api/orders
```

Resultado observado:

```text
curl: (7) Failed to connect to 127.0.0.1 port 8081: Connection refused
```

## Evidencia raiz

O log do `backend-container` mostrou que a aplicacao Spring Boot iniciou corretamente, conectou ao Postgres,
conectou ao RabbitMQ e depois a JVM encerrou com falha nativa:

```text
JRE version: OpenJDK Runtime Environment (17.0.2+8)
SIGSEGV (0xb)
Problematic frame:
V  [libjvm.so+0xab8bac]  MachProjNode::bottom_type() const+0x1c
```

Conclusao: o backend morreu por crash da JVM `17.0.2`, deixando a porta `8081` sem processo escutando. O Nginx
continuou rodando e por isso passou a responder `502`.

## Hipoteses descartadas

- Banco de dados: o backend havia conectado no Postgres antes do crash.
- RabbitMQ: o backend havia criado conexao AMQP antes do crash.
- VPN: `127.0.0.1/api/orders` ja falhava dentro do proprio servidor.
- CORS: CORS nao causa `502`; o proxy falhava antes da resposta chegar ao navegador.
- Volume Docker: volume afeta dados/senhas, mas nao explica `connection refused` na porta do backend.

## Mudancas aplicadas

1. `Dockerfile`
   - Antes: `public.ecr.aws/docker/library/openjdk:17-jdk-slim`
   - Depois: `public.ecr.aws/docker/library/eclipse-temurin:17-jdk-jammy` no build
   - Depois: `public.ecr.aws/docker/library/eclipse-temurin:17-jre-jammy` no runtime

2. `docker-compose.yml`
   - Adicionado `restart: unless-stopped` no `backend-container`.
   - Corrigido escape do `SERVER_HOST` no comando do Nginx:

```yaml
command: /bin/sh -c "envsubst '$$SERVER_HOST' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf && exec nginx -g 'daemon off;'"
```

3. `.env`
   - Mantido IP de producao:

```env
SERVER_HOST=192.168.10.13
MINIO_HOST=192.168.10.13
APP_DXF_ANALYSIS_IMAGE_BASE_URL=/facas-renders
```

   - Adicionado `APP_CORS_ALLOWED_ORIGIN_PATTERNS` para acesso LAN/VPN/local.

## Validacao local

Imagem backend reconstruida com sucesso:

```text
docker compose build backend-container
BUILD SUCCESS
```

JVM validada no container:

```text
openjdk version "17.0.19" 2026-04-21
OpenJDK Runtime Environment Temurin-17.0.19+10
```

Smoke tests locais:

```bash
curl -i http://127.0.0.1:8081/api/orders
curl -i http://127.0.0.1/api/orders
```

Resultados:

```text
HTTP/1.1 200
```

## Deploy recomendado no servidor

Depois que a mudanca estiver no Git:

```bash
cd ~/organizador-producao
./update-organizer
docker compose up -d --force-recreate backend-container nginx-container
curl -i http://127.0.0.1:8081/api/orders
curl -i http://127.0.0.1/api/orders
curl -i http://10.122.76.196/api/orders
```

Criterio de aceite:

- `docker logs backend-container --tail 80` mostra Java `17.0.19` ou superior, nao `17.0.2`.
- `curl http://127.0.0.1:8081/api/orders` retorna `200`.
- `curl http://127.0.0.1/api/orders` retorna `200`.
- `curl http://10.122.76.196/api/orders` retorna `200`.
- Nao ha novo `SIGSEGV` em `docker logs backend-container`.

## Rollback

Rollback imediato por Git:

```bash
git log --oneline -n 5
git revert <commit-da-mudanca-runtime>
docker compose build backend-container
docker compose up -d --force-recreate backend-container nginx-container
```

Rollback manual emergencial, se o commit ainda nao existir:

1. Voltar o `Dockerfile` para a imagem anterior:

```dockerfile
FROM public.ecr.aws/docker/library/openjdk:17-jdk-slim as build
...
FROM public.ecr.aws/docker/library/openjdk:17-jdk-slim
```

2. Remover `restart: unless-stopped` do `backend-container`, se necessario.
3. Rebuildar e recriar:

```bash
docker compose build backend-container
docker compose up -d --force-recreate backend-container nginx-container
```

Observacao: esse rollback volta tambem o risco de `SIGSEGV` na JVM `17.0.2`. Usar apenas se a nova imagem impedir
o deploy por algum motivo externo.

## Licao operacional

Toda modificacao que afete runtime, deploy, rede, banco, RabbitMQ, MinIO, auth ou FileWatcher deve ter report curto
com: objetivo, arquivos alterados, evidencia antes, evidencia depois, comandos de validacao, risco e rollback.
