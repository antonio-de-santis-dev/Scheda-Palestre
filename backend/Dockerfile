# syntax=docker/dockerfile:1
# --- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/gym-planner-backend-*.jar app.jar

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S gym && adduser -S gym -G gym
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER gym
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
