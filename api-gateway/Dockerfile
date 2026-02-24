FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY smartdocs-common ./smartdocs-common
COPY api-gateway ./api-gateway
RUN --mount=type=cache,target=/root/.m2 \
    cd smartdocs-common && mvn install -DskipTests -q && \
    cd ../api-gateway && mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
