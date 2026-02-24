FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY smartdocs-common ./smartdocs-common
COPY smartdocs-ai-service ./smartdocs-ai-service
RUN --mount=type=cache,target=/root/.m2 \
    cd smartdocs-common && mvn install -DskipTests -q && \
    cd ../smartdocs-ai-service && mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/smartdocs-ai-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
