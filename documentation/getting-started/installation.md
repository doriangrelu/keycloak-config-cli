# Installation

This guide covers the different ways to install and run Keycloak Config CLI.

## Prerequisites

- **Java 21** or higher
- A running **Keycloak** instance (version 22.x or higher)
- Network access to the Keycloak Admin API

## Installation Methods

### 1. Download JAR (Recommended)

Download the latest release from the [releases page](https://github.com/doriangrelu/keycloak-config-cli/releases):

```bash
# Download the JAR
curl -L -o keycloak-config-cli.jar \
  https://github.com/doriangrelu/keycloak-config-cli/releases/download/v6.4.1/keycloak-config-cli-6.4.1.jar

# Run it
java -jar keycloak-config-cli.jar --help
```

### 2. Docker

Run using the official Docker image:

```bash
docker run --rm \
  -v $(pwd)/config:/config \
  -e KEYCLOAK_URL=http://keycloak:8080 \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD=admin \
  -e IMPORT_FILES_LOCATIONS=/config/*.yaml \
  ghcr.io/doriangrelu/keycloak-config-cli:latest
```

### 3. Build from Source

Clone the repository and build with Maven:

```bash
git clone https://github.com/doriangrelu/keycloak-config-cli.git
cd keycloak-config-cli

# Build without tests (faster)
./mvnw clean package -DskipTests

# The JAR will be in target/
java -jar target/keycloak-config-cli-*.jar --help
```

### 4. Maven Dependency

If you want to integrate keycloak-config-cli into your own application:

```xml
<dependency>
    <groupId>io.github.doriangrelu.keycloak</groupId>
    <artifactId>keycloak-config-cli</artifactId>
    <version>6.4.1</version>
</dependency>
```

## Verifying Installation

Test your installation by running:

```bash
java -jar keycloak-config-cli.jar --help
```

You should see the available command-line options and their descriptions.

## Next Steps

- [Quick Start Guide](quick-start.md) - Get up and running quickly
- [Configuration Properties](configuration.md) - Learn about all available options
