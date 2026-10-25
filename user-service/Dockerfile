# Build depuis la racine du projet : docker build -f user-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY grpc-api grpc-api
COPY user-service/pom.xml user-service/
COPY notification-service/pom.xml notification-service/
COPY frontend/pom.xml frontend/
COPY user-service/src user-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl user-service -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/user-service/target/user-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
