FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

EXPOSE 10001

CMD ["java", "-jar", "target/booking-bot-1.0.0.jar"]
