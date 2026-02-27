FROM openjdk:21-jdk-slim

WORKDIR /app

# The backend module builds the runnable jar under backend/target.
COPY backend/target/*.jar app.jar

# Default server.port is 8090 (see backend application.yaml).
EXPOSE 8090

CMD ["java", "-jar", "app.jar"]
