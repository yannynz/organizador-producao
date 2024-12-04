FROM node:18-alpine AS build

# Configurar o fuso horário para São Paulo
ENV TZ=America/Sao_Paulo
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    apk del tzdata

WORKDIR /app

# Copiar apenas arquivos necessários para o build
COPY package*.json ./

# Instalar dependências
RUN npm cache clean --force && npm install --legacy-peer-deps

# Copiar o restante do código
COPY . .

# Build da aplicação
RUN npm run build --prod

# Etapa final: Servir com nginx
FROM nginx:alpine
COPY --from=build /app/dist/ /usr/share/nginx/html
CMD ["nginx", "-g", "daemon off;"]

