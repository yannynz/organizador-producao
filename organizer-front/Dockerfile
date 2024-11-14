# Etapa 1: Construção da aplicação Angular
FROM node:18 AS build

WORKDIR /app

# Copiar package.json e package-lock.json para instalar dependências
COPY package*.json ./

# Instalar dependências
RUN npm install --legacy-peer-deps

# Copiar todos os arquivos do projeto para o contêiner
COPY . .

# Construir o projeto Angular
RUN npm run build --prod

# Etapa 2: Servir a aplicação Angular com Nginx
FROM nginx:alpine

# Copiar os arquivos construídos no Angular para o diretório do Nginx
COPY --from=build /app/dist/organizer-front /usr/share/nginx/html


# Iniciar o servidor Nginx
CMD ["nginx", "-g", "daemon off;"]

