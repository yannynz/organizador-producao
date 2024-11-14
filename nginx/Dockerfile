# Use a imagem oficial do Nginx
FROM nginx:alpine

# Copie o arquivo de configuração do Nginx para dentro do container
COPY nginx.conf /etc/nginx/nginx.conf

# Copie os arquivos gerados pelo build do Angular para o diretório padrão do Nginx
COPY ../organizer-front/dist/ /usr/share/nginx/html/

# Exponha a porta 80
EXPOSE 80

