FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
RUN mvn -s .mvn/settings.xml dependency:go-offline
COPY src src
RUN mvn -s .mvn/settings.xml clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/issue-tracker-1.0.0.jar app.jar
RUN mkdir -p /home/appuser/logs/nacos \
    && chown -R appuser:appuser /home/appuser/logs \
    && mkdir -p /home/appuser/uploads/attachments \
    && chown -R appuser:appuser /home/appuser/uploads
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
