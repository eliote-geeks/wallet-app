FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar
RUN addgroup --system appgroup && adduser --system --ingroup appgroup --uid 10001 appuser \
    && chown appuser:appgroup /app/app.jar
USER 10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
