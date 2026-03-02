# Этап сборки
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Собираем, показываем содержимое target и явно копируем нужный jar
RUN mvn clean package -DskipTests && \
    ls -la target && \
    cp target/booking-bot-1.0.0.jar app.jar && \
    ls -la .

# Этап запуска
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
