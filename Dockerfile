# Сборка
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Запуск
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 markethub
COPY --from=build /app/target/markethub-*.jar app.jar
USER markethub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
