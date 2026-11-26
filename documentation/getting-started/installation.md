# Installation

This guide covers the different ways to install and run Keycloak Config CLI.

## Prerequisites

- **Java 25** or higher
- A running **Keycloak** instance (this version is built and tested against Keycloak 26.5.3, see [Compatibility with Keycloak](../../README.md#compatibility-with-keycloak))
- Network access to the Keycloak Admin API

## Installation Methods

### 1. Download JAR (Recommended)

Download the JAR of a release from the [releases page](https://github.com/doriangrelu/keycloak-config-cli/releases), and run it with `java -jar keycloak-config-cli.jar`, as shown in the [Quick Start Guide](quick-start.md).

### 2. Docker

No container image is published for now: build one from the JAR, as explained in the [Docker section of the README](../../README.md#docker), then run it with your files mounted:

```bash
docker run --rm \
  -v $(pwd)/config:/config \
  -e KEYCLOAK_URL=http://keycloak:8080 \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD=admin \
  -e IMPORT_FILES_LOCATIONS='/config/*.yaml' \
  keycloak-config-cli
```

### 3. Build from Source

Clone the repository and build with Maven:

```bash
git clone https://github.com/doriangrelu/keycloak-config-cli.git
cd keycloak-config-cli

# Build without tests (faster)
./mvnw clean package -DskipTests

# The JAR is target/keycloak-config-cli.jar
```

keycloak-config-cli is an executable Spring Boot application, not a library: it is not published as a Maven dependency.

## Verifying Installation

There is no `--help` option. Check the installation and the connection to Keycloak with a dry run, which changes nothing:

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=realm.yaml \
  --import.dry-run=true
```

Exit code `0` or `10` means the files were read and compared with Keycloak; see [Exit Codes](../reference/exit-codes.md) for the others.

## Next Steps

- [Quick Start Guide](quick-start.md) - Get up and running quickly
- [Configuration Properties](configuration.md) - Learn about all available options
- [Migrating to 7.0.0](migration-7.md) - Upgrading from 6.x
