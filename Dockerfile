FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=build /app/target/booking-bot-1.0.0.jar app.jar

EXPOSE 10001

CMD ["java", "-jar", "app.jar"]
