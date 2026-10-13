# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Warm the dependency cache before copying sources, so code changes
# don't re-download the world.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# mvnw may arrive with Windows line endings; the shell needs LF.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
RUN ./mvnw -q package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/legacy-explainer-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
