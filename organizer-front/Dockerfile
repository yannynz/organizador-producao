FROM public.ecr.aws/docker/library/node:20-slim AS build

# Configurar o fuso horário para São Paulo
ENV TZ=America/Sao_Paulo
RUN apt-get update && apt-get install -y tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app
ENV NODE_OPTIONS=--max-old-space-size=2048
ENV NG_BUILD_MAX_WORKERS=1
ENV npm_config_jobs=1

# Copiar apenas arquivos necessários para o build
COPY package*.json ./

# Instalar dependências
RUN npm cache clean --force && npm install --legacy-peer-deps

# Copiar o restante do código
COPY . .

# Build da aplicação (sem SSR/prerender no pipeline Docker para evitar falhas
# intermitentes do esbuild durante renderização server-side)
RUN npm run build -- --configuration=production --ssr=false --prerender=false

# Etapa final: servir os arquivos estaticos via Node.
# Evita depender da imagem nginx:alpine no build do frontend; o proxy externo
# continua sendo o nginx-container definido no docker-compose.
FROM build AS runtime
WORKDIR /app/dist/organizer-front/browser
EXPOSE 80
CMD ["node", "/app/docker-static-server.mjs"]
