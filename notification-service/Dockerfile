# Build depuis la racine du projet : docker build -f notification-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY grpc-api grpc-api
COPY user-service/pom.xml user-service/
COPY notification-service/pom.xml notification-service/
COPY frontend/pom.xml frontend/
COPY notification-service/src notification-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl notification-service -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/notification-service/target/notification-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
