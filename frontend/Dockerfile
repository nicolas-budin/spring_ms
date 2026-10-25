# Build depuis la racine du projet : docker build -f frontend/Dockerfile .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY grpc-api grpc-api
COPY user-service/pom.xml user-service/
COPY notification-service/pom.xml notification-service/
COPY frontend/pom.xml frontend/
COPY frontend/src frontend/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl frontend -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/frontend/target/frontend-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
