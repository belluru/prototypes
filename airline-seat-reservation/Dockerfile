FROM maven:3.8.4-openjdk-11-slim AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package

FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=build /app/target/airline-seat-reservation-1.0-SNAPSHOT.jar app.jar
CMD ["java", "-jar", "app.jar"]
