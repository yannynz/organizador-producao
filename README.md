Organizador Produção 🚀

Sistema web para gestão de ordens de produção, com frontend em Angular, backend em Spring Boot, WebSocket, RabbitMQ, PostgreSQL e Nginx.
🔧 Tecnologias

    Frontend: Angular, Bootstrap

    Backend: Java Spring Boot (REST + WebSocket + JPA/Hibernate)

    Mensageria: RabbitMQ

    Banco de Dados: PostgreSQL

    Orquestração: Docker Compose

    Servidor HTTP: Nginx (serve o frontend produzido)

⚙️ Como rodar o projeto

    Clone o repositório

git clone https://github.com/yannynz/organizador-producao.git
cd organizador-producao

Suba todos os serviços (frontend, backend, banco, RabbitMQ, Nginx), com build automático do frontend:

docker compose up --build

    ✔️ O Dockerfile do frontend faz o build do Angular (npm run build --configuration=production).

    ✔️ O Nginx serve imediatamente o pacote buildado pela mesma composição.

Acesse os seguintes serviços:
Serviço	URL
Frontend (Angular)	http://localhost
🧩 Arquitetura

    /organizer-front: código fonte Angular

    /src: backend Spring Boot

    /nginx: configuração do Nginx

    Dockerfile:

        Fase build: usa node:18-alpine, instala, pluga timezone, roda npm run build --configuration=production

        Fase final: usa nginx:alpine, copia assets do build Angular

    docker-compose.yml define os containers:

        postgres-container: banco com healthcheck, timezone configurado

        rabbitmq-container: gerencia filas e WebSocket

        backend-container: Spring Boot, depende de Postgres + RabbitMQ

        frontend-container: container usado apenas para build — o conteúdo final é servido pelo Nginx

        nginx-container: container final que serve frontend via porta 80

🛠️ Scripts úteis

    Para rebuild do frontend manual (caso não queira usar Docker completo):

cd organizer-front
npm install
npm run build --configuration=production

Para testar apenas o backend:

cd src
./mvnw spring-boot:run

Para limpar e subir tudo novamente:

    docker compose down --volumes
    docker compose up --build


