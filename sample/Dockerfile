# keycloak-config-cli from the JAR built by ./mvnw package (target/keycloak-config-cli.jar)
FROM eclipse-temurin:25-jre
COPY target/keycloak-config-cli.jar /app/keycloak-config-cli.jar
ENTRYPOINT ["java", "-jar", "/app/keycloak-config-cli.jar"]
