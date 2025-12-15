# Feature: Acesso Externo, PWA e Check-in Inteligente

**Status:** Planejamento Técnico (Draft)
**Data:** 15/12/2025
**Risco:** Médio (Alteração de Infraestrutura e Exposição Web)

Este documento detalha a estratégia para habilitar o acesso externo seguro ao "Organizador de Produção" para motoristas, transformar a aplicação em PWA e implementar a funcionalidade de "Baixa de Entrega Inteligente".

---

# 1. Visão Executiva e Objetivos

## 1.1. O Problema Atual
*   O sistema só funciona na rede local (Wi-Fi/Cabo).
*   Motoristas não conseguem dar baixa nas entregas em tempo real (na frente do cliente).
*   Ao retornar à empresa, motoristas esquecem de dar baixa nos canhotos acumulados.
*   Falta de visibilidade do status real das entregas durante o dia.

## 1.2. A Solução Proposta
1.  **Acesso Externo Seguro:** Implementar **Cloudflare Tunnel** para permitir acesso via 4G sem expor portas do roteador ou contratar IP fixo.
2.  **PWA (Progressive Web App):** Transformar o frontend Angular em um app instalável, melhorando a experiência em mobile e permitindo acesso a recursos nativos (câmera, GPS).
3.  **Check-in Inteligente:** O sistema detectará quando o motorista retornar à empresa (via Geofencing GPS) e solicitará a baixa automática das entregas pendentes.

---

# 2. Arquitetura Técnica (SDS)

## 2.1. Infraestrutura de Rede (Cloudflare Tunnel)

Substituição do modelo "Apenas Local" por um modelo Híbrido Seguro.

```mermaid
graph LR
    User[Motorista (4G)] -->|HTTPS| CF[Cloudflare Edge]
    CF -->|Túnel Criptografado| Cloudflared[Agente Cloudflared (Docker)]
    Cloudflared -->|Rede Docker Interna| Nginx[Nginx Container]
    Nginx -->|HTTP| Front[Frontend Angular]
    Nginx -->|HTTP| Back[Backend API]
    
    subgraph "Infraestrutura da Empresa (Sem Portas Abertas)"
        Cloudflared
        Nginx
        Front
        Back
    end
```

### Componentes:
*   **Domínio:** Necessário adquirir (ex: `organizador.ycar.com.br`).
*   **Cloudflared:** Container Docker adicional que mantém a conexão persistente com a Cloudflare.
*   **Autenticação:** A segurança continua sendo feita pelo JWT do sistema, mas agora trafegando obrigatoriamente por HTTPS (gerido pela Cloudflare).

## 2.2. Frontend: Angular PWA

### 2.2.1. Conversão
Utilizar o schematics oficial do Angular: `ng add @angular/pwa`.
*   **Manifest:** Nome, ícones, cor de tema.
*   **Service Worker:** Estratégia de cache `NetworkFirst` para API (garantir dados frescos) e `CacheFirst` para assets (carregamento rápido).

### 2.2.2. Lógica de "Retorno à Base" (Geofencing)

Como o app terá acesso externo, o **GPS (Geolocation API)** será o gatilho primário.

1.  **Monitoramento:** O PWA monitora a posição (se permissão concedida).
2.  **Regra de Negócio:**
    *   `Distancia(Usuario, Empresa) < 200m`
    *   `TempoDesdeUltimaBaixa > 1h` (para evitar alertas repetitivos enquanto almoça na empresa).
    *   `PossuiEntregasPendentes(Usuario) == true`
3.  **Ação:** Exibir Modal: *"Você chegou na empresa. Deseja baixar as entregas pendentes?"*.

---

# 3. Plano de Implementação Detalhado

## Fase 1: Infraestrutura e Acesso (Dia 1)

1.  **Aquisição de Domínio:** Registrar domínio `.com.br` (Custo ~R$ 40/ano).
2.  **Configuração Cloudflare:**
    *   Apontar DNS para Cloudflare.
    *   Criar Tunnel "Zero Trust".
3.  **Setup Docker:**
    *   Adicionar serviço `cloudflared` no `docker-compose.yml`.
    *   Configurar token do túnel via variável de ambiente.
4.  **Validação:** Acessar o sistema via 4G. Verificar se WebSockets (RabbitMQ/Stomp) funcionam através do túnel (requer config específica na Cloudflare).

## Fase 2: Transformação PWA (Dia 2)

1.  **Código Angular:**
    *   Executar `ng add @angular/pwa`.
    *   Configurar ícones (logo da empresa) e cores no `manifest.webmanifest`.
    *   Ajustar `ngsw-config.json` para não cachear respostas da API `/api/*` (dados devem ser sempre online).
2.  **Build e Deploy:**
    *   Rodar build de produção.
    *   Testar "Instalação" no Android (Chrome) e iOS (Safari - "Add to Home Screen").

## Fase 3: Funcionalidade de Check-in (Dia 3)

1.  **Componente de Geolocalização:**
    *   Criar serviço `GeoService` para encapsular `navigator.geolocation`.
    *   Definir coordenadas fixas da empresa (`const COMPANY_COORDS`).
2.  **Lógica de Detecção:**
    *   No `AppComponent` (ou layout principal), verificar posição ao iniciar/retornar foco.
    *   Consultar API: `GET /orders/delivery/my-pending`.
    *   Se `Perto && TemPendencia`, abrir `DeliveryReturnModal`.

---

# 4. Plano de Migração e Rollback

## 4.1. Migração (Sem Parada Crítica)

O acesso local (`192.168.x.x`) **continuará funcionando** paralelamente ao acesso externo. Isso mitiga riscos.

1.  **Deploy Infra:** Subir container `cloudflared`. Isso não afeta os containers existentes.
2.  **Teste Piloto:** Liberar URL externa apenas para 1 motorista e para o admin.
3.  **Deploy PWA:** Atualizar o container frontend. Usuários internos (PC) apenas verão o ícone de instalar (inofensivo).
4.  **Virada de Chave:** Instruir motoristas a acessarem a nova URL e "instalarem" o app.

## 4.2. Rollback (Plano B)

Se houver problemas (ex: lentidão, instabilidade no túnel):
1.  **Parar Acesso Externo:** `docker stop cloudflared`. O acesso externo morre instantaneamente.
2.  **Volta ao Normal:** Motoristas voltam a usar o acesso via Wi-Fi interno (`192.168...`) como fazem hoje. O PWA continua instalado, mas só conecta quando estiverem no Wi-Fi.

---

# 5. Custos e Requisitos

| Item | Custo Estimado | Recorrência |
| :--- | :--- | :--- |
| **Domínio (.com.br)** | R$ 40,00 | Anual |
| **Cloudflare Tunnel** | R$ 0,00 | Mensal (Free Tier) |
| **Servidor Atual** | R$ 0,00 | Já existente |
| **Certificado SSL** | R$ 0,00 | Incluso na Cloudflare |

**Requisito Crítico:** O servidor da empresa deve ter acesso à internet (saída) para manter o túnel conectado. Não precisa de porta de entrada aberta.

---

# 6. Considerações de Segurança

1.  **Forçar HTTPS:** A Cloudflare cuida disso. Impossível acessar via HTTP inseguro externamente.
2.  **Firewall Web (WAF):** A Cloudflare bloqueia bots e ataques comuns automaticamente.
3.  **Token JWT:** Como já aumentamos a validade para 7 dias, o motorista não precisará digitar senha na rua frequentemente, reduzindo risco de "olheiros".
4.  **Restrição de Rota (Opcional):** Podemos configurar no Cloudflare Access para que a URL externa só permita acessar rotas de `/api/auth` e `/api/orders`, bloqueando `/admin` externamente se desejado (camada extra de blindagem).
