FROM public.ecr.aws/docker/library/eclipse-temurin:17-jdk-jammy as build
# Configurar o fuso horário para São Paulo
ENV TZ=America/Sao_Paulo
RUN apt-get update && apt-get install -y tzdata && \
    echo $TZ > /etc/timezone && \
    ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && \
    dpkg-reconfigure -f noninteractive tzdata && \
    apt-get clean
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN rm -rf ~/.m2 # Clean Maven cache
RUN ./mvnw clean install -DskipTests
FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/organizador-producao-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
