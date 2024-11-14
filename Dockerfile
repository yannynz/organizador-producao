# Usar a imagem do OpenJDK para compilar e rodar a aplicação
FROM openjdk:17-jdk-slim as build

# Diretório de trabalho no container
WORKDIR /app

# Copiar o código fonte para o container
COPY . .

# Compilar o projeto (assumindo que você usa Maven)
RUN ./mvnw clean install -DskipTests

# Usar a imagem do OpenJDK para rodar a aplicação
FROM openjdk:17-jdk-slim

# Diretório de trabalho no container
WORKDIR /app

# Copiar o artefato gerado no estágio anterior para o contêiner
COPY --from=build /app/target/organizador-producao-0.0.1-SNAPSHOT.jar /app/app.jar

# Expor a porta onde a aplicação Spring Boot irá rodar
EXPOSE 8080

# Comando para rodar a aplicação
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

