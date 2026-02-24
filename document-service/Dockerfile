FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY smartdocs-common ./smartdocs-common
COPY document-service ./document-service
RUN --mount=type=cache,target=/root/.m2 \
    cd smartdocs-common && mvn install -DskipTests -q && \
    cd ../document-service && mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/document-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
