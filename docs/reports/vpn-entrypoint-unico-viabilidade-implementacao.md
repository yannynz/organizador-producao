# Viabilidade e Plano: Acesso Completo via VPN

Data: 2026-05-01

## Objetivo

Fazer o Organizer funcionar pela VPN com o mesmo comportamento do acesso local:

- Frontend Angular carregando pelo endereço VPN.
- API acessível por `/api`.
- WebSocket STOMP acessível por `/ws/orders`.
- Status RPC do FileWatcher visível no front.
- Imagens DXF visíveis por `/facas-renders/...`.
- FileWatcher conectado ao RabbitMQ e MinIO do mesmo ambiente.

## Viabilidade

Viabilidade: alta.

A maior parte da solução é configuração de deploy. O código atual já favorece esse modelo:

- O front de produção monta o WebSocket com `window.location.host`.
- A API já usa paths relativos como `/api`.
- O backend já aceita `APP_CORS_ALLOWED_ORIGINS`.
- O backend já aceita `APP_DXF_ANALYSIS_IMAGE_BASE_URL`.
- O Nginx já tem blocos para `/api/`, `/ws/` e `/facas-renders/`.
- O FileWatcher já aceita configuração por variáveis de ambiente.

O ponto principal é evitar mistura de ambientes. O browser deve acessar um único host público, e esse host deve encaminhar front, API, WebSocket e imagens.

## Diagnóstico Atual

O problema observado não é falta de recurso no front. É divergência de host.

Exemplo validado:

- `10.122.76.32` respondeu WebSocket com `101 Switching Protocols`.
- `10.122.76.196` respondeu WebSocket com `403`.
- `10.122.76.196` apresentou outro Nginx e outro deploy.

Como o front usa `window.location.host`, se o usuário abre:

```text
http://10.122.76.196
```

o WebSocket será:

```text
ws://10.122.76.196/ws/orders
```

Se esse host não estiver configurado corretamente, RPC, atualizações em tempo real e status do FileWatcher falham na interface.

## Decisão de Arquitetura

Usar um entrypoint único por ambiente.

Exemplo:

```text
http://10.122.76.196
```

ou:

```text
http://organizador-vpn.local
```

Esse host deve resolver tudo:

```text
/                  -> frontend-container
/api/              -> backend-container:8080
/ws/orders         -> backend-container:8080
/facas-renders/    -> minio:9000
```

O browser não deve depender de:

- `192.168.x.x:8081`
- `192.168.x.x:9000`
- IP interno do Docker
- IP de outra máquina fora da VPN

## Configuração Alvo

### `.env`

Exemplo para VPN:

```env
SERVER_HOST=10.122.76.196
MINIO_HOST=10.122.76.196
APP_DXF_ANALYSIS_IMAGE_BASE_URL=/facas-renders
APP_CORS_ALLOWED_ORIGINS=http://10.122.76.196,http://10.122.76.196:80,http://localhost:4200,http://localhost,http://nginx-container,http://nginx-container:80,http://frontend-container
```

Se usar domínio:

```env
SERVER_HOST=organizador-vpn.local
MINIO_HOST=organizador-vpn.local
APP_DXF_ANALYSIS_IMAGE_BASE_URL=/facas-renders
APP_CORS_ALLOWED_ORIGINS=http://organizador-vpn.local,http://10.122.76.196,http://localhost:4200,http://localhost,http://nginx-container,http://nginx-container:80,http://frontend-container
```

### Nginx

O proxy precisa preservar o path.

Configuração esperada:

```nginx
location /ws/ {
    proxy_pass http://backend-container:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_cache_bypass $http_upgrade;
}
```

Para imagens:

```nginx
location ^~ /facas-renders/ {
    proxy_pass http://minio:9000;
    proxy_set_header Host $http_host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Não usar `proxy_pass http://backend-container:8080/` no bloco `/ws/`, porque a barra final pode reescrever o path e transformar `/ws/orders` em `/orders` ou `/ws/`, dependendo do bloco.

## Forma de Codar

Priorizar configuração antes de código.

Não hardcodar IP no front.

Manter no front:

```ts
wsUrl: `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/orders`
```

Manter API relativa:

```ts
apiBaseUrl: '/api'
```

Manter imagens relativas quando vierem do backend:

```text
/facas-renders/...
```

No backend, continuar usando configuração externa:

```properties
app.dxf.analysis.image-base-url=${APP_DXF_ANALYSIS_IMAGE_BASE_URL:/facas-renders}
app.cors.allowed-origins=${APP_CORS_ALLOWED_ORIGINS:...}
```

Só considerar mudança de código se for necessário suportar múltiplos hosts dinâmicos com segurança. Nesse caso, criar uma propriedade explícita:

```properties
app.public-base-url=http://10.122.76.196
```

e derivar URLs públicas a partir dela. Não espalhar IPs pelo código.

## FileWatcher

O FileWatcher deve apontar para o mesmo ambiente do Organizer acessado pela VPN.

Configuração esperada no Windows Server ou serviço equivalente:

```env
RabbitMq__HostName=10.122.76.196
RabbitMq__Port=5672
DXFAnalysis__RabbitMq__HostName=10.122.76.196
DXFAnalysis__RabbitMq__Port=5672
DXFAnalysis__ImageStorage__Endpoint=http://10.122.76.196:9000
DXFAnalysis__ImageStorage__PublicBaseUrl=http://10.122.76.196/facas-renders
DXFAnalysis__ImageStorage__Bucket=facas-renders
```

Mesmo que o FileWatcher use `:9000` para upload no MinIO, o browser deve receber a URL relativa:

```text
/facas-renders/...
```

## Plano de Implementação

1. Escolher o host canônico da VPN.

Definir se será IP direto ou DNS interno.

2. Atualizar `.env` do servidor.

Incluir o host VPN em:

- `SERVER_HOST`
- `MINIO_HOST`
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_DXF_ANALYSIS_IMAGE_BASE_URL=/facas-renders`

3. Recriar backend e Nginx.

Comando esperado:

```bash
docker compose up -d --build backend-container nginx-container
```

4. Conferir Nginx gerado dentro do container.

```bash
docker exec nginx-container nginx -T | sed -n '/location \\/ws\\//,/}/p'
docker exec nginx-container nginx -T | sed -n '/facas-renders/,/}/p'
```

5. Configurar FileWatcher para o mesmo ambiente.

Revisar RabbitMQ e MinIO no serviço do Windows.

6. Reiniciar FileWatcher.

Validar que ele consumiu as filas:

```bash
docker exec rabbitmq-container rabbitmqctl list_queues name messages consumers
```

## Matriz de Testes

### Teste 1: Front via VPN

```bash
curl -i http://10.122.76.196/
```

Esperado:

```text
HTTP/1.1 200
```

### Teste 2: API via VPN

```bash
curl -i http://10.122.76.196/api/orders
```

Esperado:

```text
HTTP/1.1 200
```

### Teste 3: WebSocket via VPN

```bash
curl --max-time 5 -v --http1.1 \
  -H 'Origin: http://10.122.76.196' \
  -H 'Connection: Upgrade' \
  -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  -H 'Sec-WebSocket-Version: 13' \
  http://10.122.76.196/ws/orders
```

Esperado:

```text
HTTP/1.1 101
```

Falhas comuns:

- `403 Invalid CORS request`: host VPN ausente em `APP_CORS_ALLOWED_ORIGINS`.
- `403 Access Denied` com path `/ws/`: Nginx está reescrevendo path ou backend não tem `/ws/orders` liberado nesse deploy.
- `Connection refused`: não existe serviço ouvindo nesse IP/porta.

### Teste 4: RPC/FileWatcher

Verificar consumidores:

```bash
docker exec rabbitmq-container rabbitmqctl list_queues name messages consumers
```

Esperado:

```text
filewatcher.rpc.ping     0    1
file_commands            0    1
facas.analysis.request   0    1
facas.analysis.result    0    1
```

No front, o status do FileWatcher deve ficar online. Esse status depende do WebSocket.

### Teste 5: Imagem DXF via VPN

Buscar uma análise recente:

```bash
docker exec postgres-container psql -U postgres -d teste01 -c \
"select analysis_id, order_nr, image_key, image_upload_status from dxf_analysis order by analyzed_at desc limit 5;"
```

Testar URL pelo Nginx:

```bash
curl -I http://10.122.76.196/facas-renders/<image_key>
```

Esperado:

```text
HTTP/1.1 200
Content-Type: image/png
```

### Teste 6: Fluxo Completo DXF

1. Enviar ou copiar DXF para o FileWatcher.
2. Confirmar publicação em `facas.analysis.request`.
3. Confirmar resultado em `facas.analysis.result`.
4. Confirmar registro `uploaded` em `dxf_analysis`.
5. Confirmar front carregando imagem pela URL relativa.

## Reports de Validação

Criar um arquivo por rodada em:

```text
docs/reports/
```

Nome sugerido:

```text
vpn-validation-YYYY-MM-DD.md
```

Template:

```md
# Validação VPN - YYYY-MM-DD

## Ambiente

- Host VPN:
- Host LAN:
- Branch/tag:
- Commit:
- Docker compose:
- FileWatcher host:

## Resultado

- Front `/`: PASS/FAIL
- API `/api/orders`: PASS/FAIL
- WebSocket `/ws/orders`: PASS/FAIL
- RPC FileWatcher: PASS/FAIL
- DXF request/result: PASS/FAIL
- Imagem `/facas-renders`: PASS/FAIL

## Evidências

### WebSocket

Comando:

Resultado:

### RabbitMQ

Comando:

Resultado:

### DXF

Analysis ID:
Order NR:
Image key:
Image URL:
HTTP status:

## Problemas Encontrados

- 

## Ações Corretivas

- 
```

## Critério de Aceite

A implementação está concluída quando:

- O usuário acessa o Organizer pelo host VPN canônico.
- O console do navegador não mostra erro em `ws://.../ws/orders`.
- O WebSocket retorna `101` no teste manual.
- O status RPC do FileWatcher aparece online no front.
- As filas RabbitMQ têm consumidores ativos.
- Uma alteração de prioridade chega no FileWatcher.
- Uma análise DXF nova gera imagem.
- A imagem abre via `http://<host-vpn>/facas-renders/...`.
- Nenhuma URL pública retornada ao browser contém `192.168.x.x:9000`.

## Riscos

- IP VPN mudar.
- Usuário abrir host antigo.
- Dois deploys diferentes servindo o mesmo app.
- Cache de bundle antigo no navegador.
- CORS configurado sem o host VPN.
- Nginx com barra final errada em `proxy_pass`.
- FileWatcher apontando para RabbitMQ de outro ambiente.
- MinIO acessível para upload, mas não para leitura via Nginx.

## Recomendação

Padronizar o acesso por um único host VPN ou DNS interno e tratar o Nginx como única entrada pública. Essa é a solução com menor risco, porque mantém front, API, WebSocket e imagens no mesmo origin.
