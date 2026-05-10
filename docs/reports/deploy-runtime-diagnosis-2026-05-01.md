# Diagnostico runtime - 2026-05-01

## Resultado local

- Branch local esta em `Versao 1.4.0`.
- Codigo contem correcoes de WebSocket.
- Codigo contem fallback de imagem via `/facas-renders`.
- `docker compose ps` local nao mostra Organizer rodando.
- `docker ps` local nao mostra containers do Organizer.
- `curl http://192.168.10.13` deu timeout.
- `curl http://192.168.2.228:9000/facas-renders/` deu timeout.

Conclusao local:

- Este host nao validou runtime do server.
- Nao ha stack Organizer local ativa.
- Rede/VPN daqui nao alcanca os IPs configurados.

## Testes executados

Frontend:

- Comando: `npm test -- --watch=false --browsers=ChromeHeadless`
- Resultado: `32 SUCCESS`

Backend:

- Comando: `./mvnw test -Dtest=WebSocketSecurityTest,DXFAnalysisRequestPublisherTest`
- Resultado: `BUILD SUCCESS`
- Resultado: `4 tests, 0 failures`

## Achado principal

Ha divergencia de ambiente.

Organizer:

- `.env`: `SERVER_HOST=192.168.10.13`
- `docker-compose.yml`: Rabbit interno `rabbitmq-container`
- `docker-compose.yml`: MinIO interno `minio:9000`
- `APP_DXF_ANALYSIS_IMAGE_BASE_URL`: `http://192.168.10.13/facas-renders`

FileWatcherApp:

- `RabbitMq.HostName`: `192.168.2.228`
- `DXFAnalysis.RabbitMq.HostName`: `192.168.2.228`
- `ImageStorage.Endpoint`: `http://192.168.2.228:9000`
- `ImageStorage.PublicBaseUrl`: `http://192.168.2.228:9000/facas-renders`

Risco:

- FileWatcher pode estar publicando em outro RabbitMQ.
- Organizer pode estar consumindo outro RabbitMQ.
- Imagens podem estar indo para outro MinIO.
- Front pode buscar imagens em ambiente diferente.

Esse ponto explica:

- OP import nao chega.
- Analise DXF nao chega.
- Imagem nao aparece.
- Ping/status FileWatcher falha.
- WebSocket parece sem efeito.

## Achado secundario

`MINIO_BROWSER_REDIRECT_URL` esta hardcoded:

- `http://192.168.10.13:9091`

Se o server real mudou de IP:

- Console MinIO redireciona errado.
- Links manuais quebram.
- Diagnostico pelo browser confunde.

## Validacao no server

Rodar no server Linux:

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
```

Esperado:

- `backend-container`
- `frontend-container`
- `nginx-container`
- `rabbitmq-container`
- `postgres-container`
- `minio`

Ver env real do backend:

```bash
docker inspect backend-container --format '{{range .Config.Env}}{{println .}}{{end}}'
```

Conferir estes valores:

- `SPRING_RABBITMQ_HOST=rabbitmq-container`
- `APP_DXF_ANALYSIS_IMAGE_BASE_URL=http://<ip-correto>/facas-renders`
- `APP_CORS_ALLOWED_ORIGINS` contem host da VPN.

Conferir filas:

```bash
docker exec rabbitmq-container rabbitmqctl list_queues name messages consumers
```

Esperado:

- `filewatcher.rpc.ping` com consumidor.
- `facas.analysis.request` com consumidor.
- `facas.analysis.result` com consumidor.
- `op.imported` com consumidor.

Se `consumers = 0`:

- FileWatcher nao esta conectado.
- Ou Organizer nao esta conectado.
- Ou ambos estao em Rabbits diferentes.

Conferir logs backend:

```bash
docker logs backend-container --tail 200
```

Procurar:

- `[AMQP] op.imported received`
- `DXF analysis request`
- `Erro ao processar resultado`
- `Rabbit`
- `WebSocket`

Conferir imagem via Nginx:

```bash
curl -I http://localhost/facas-renders/
```

Esperado:

- `403`
- ou `404`
- mas vindo do MinIO.

Nao esperado:

- `502`
- `connection refused`
- HTML do frontend.

Conferir frontend novo:

```bash
curl -s http://localhost/pedidos | grep -o 'main-[^"]*.js' | head -1
```

Depois:

```bash
curl -s http://localhost/<arquivo-main-js> | grep '/facas-renders'
```

Esperado:

- Encontrar `/facas-renders`.

Se nao encontrar:

- Front antigo esta rodando.
- Imagem Docker nao foi rebuildada.
- Browser pode estar cacheando bundle antigo.

## Validacao no Windows FileWatcher

No log do FileWatcher, confirmar:

- `RabbitMQ conectado em <ip-server-correto>:5672`
- `vhost=/`
- `Iniciando DXFAnalysisWorker`
- `fila=facas.analysis.request`
- `Request publicado`
- `Resultado publicado`
- `UploadStatus=uploaded`

Config esperada:

```json
"RabbitMq": {
  "HostName": "<ip-server-correto>",
  "Port": 5672
}
```

```json
"DXFAnalysis": {
  "RabbitMq": {
    "HostName": "<ip-server-correto>",
    "Port": 5672
  },
  "ImageStorage": {
    "Endpoint": "http://<ip-server-correto>:9000",
    "Bucket": "facas-renders"
  }
}
```

## Acao recomendada

1. Definir um unico IP de prod.
2. Atualizar `.env` do Organizer.
3. Atualizar `appsettings.json` do FileWatcherApp.
4. Rebuildar backend e frontend.
5. Recriar containers.
6. Limpar cache do browser.
7. Validar filas Rabbit.
8. Validar uma OP real.
9. Validar uma imagem real.

Comando recomendado de deploy:

```bash
SERVER_HOST=<ip-correto> \
MINIO_HOST=<ip-correto> \
APP_DXF_ANALYSIS_IMAGE_BASE_URL=http://<ip-correto>/facas-renders \
APP_CORS_ALLOWED_ORIGINS=http://<ip-correto>,http://<ip-correto>:80,http://localhost,http://localhost:4200,http://nginx-container,http://nginx-container:80,http://frontend-container \
docker compose up -d --build --force-recreate
```

## Conclusao

Implementacao local esta presente.

Testes locais passam.

O sintoma aponta para deploy/config.

Principal suspeita:

- FileWatcherApp e Organizer nao usam o mesmo IP/Rabbit/MinIO.

Segunda suspeita:

- Frontend antigo segue servido no server.
