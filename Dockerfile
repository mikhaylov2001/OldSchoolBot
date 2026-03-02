# Этап сборки
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Собираем и складываем JAR в /app/app.jar
RUN mvn clean package -DskipTests && \
    cp target/*.jar app.jar

# Этап запуска
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
