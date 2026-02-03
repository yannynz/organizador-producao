FROM public.ecr.aws/docker/library/node:20-slim AS build

# Configurar o fuso horário para São Paulo
ENV TZ=America/Sao_Paulo
RUN apt-get update && apt-get install -y tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copiar apenas arquivos necessários para o build
COPY package*.json ./

# Instalar dependências
RUN npm cache clean --force && npm install --legacy-peer-deps

# Copiar o restante do código
COPY . .

# Build da aplicação (sem SSR/prerender no pipeline Docker para evitar falhas
# intermitentes do esbuild durante renderização server-side)
RUN npm run build -- --configuration=production --ssr=false --prerender=false

# Etapa final: Servir com nginx
FROM public.ecr.aws/docker/library/nginx:alpine
COPY --from=build /app/dist/ /usr/share/nginx/html
CMD ["nginx", "-g", "daemon off;"]
