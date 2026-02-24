FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY smartdocs-common ./smartdocs-common
COPY auth-service ./auth-service
RUN --mount=type=cache,target=/root/.m2 \
    cd smartdocs-common && mvn install -DskipTests -q && \
    cd ../auth-service && mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
