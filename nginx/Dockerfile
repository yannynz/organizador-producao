# Use a imagem oficial do Nginx
FROM public.ecr.aws/docker/library/nginx:alpine

ENV TZ=America/Sao_Paulo
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    apk del tzdata

# Copie o arquivo de configuração do Nginx para dentro do container
COPY nginx.conf /etc/nginx/nginx.conf

# Copie os arquivos gerados pelo build do Angular para o diretório padrão do Nginx
COPY ../organizer-front/dist/ /usr/share/nginx/html/

# Exponha a porta 80
EXPOSE 80
