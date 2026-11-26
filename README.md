[![CI](https://github.com/doriangrelu/keycloak-config-cli/actions/workflows/ci.yaml/badge.svg)](https://github.com/doriangrelu/keycloak-config-cli/actions/workflows/ci.yaml?query=branch%3Amain)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/doriangrelu/keycloak-config-cli?logo=github&sort=semver)](https://github.com/doriangrelu/keycloak-config-cli/releases/latest)
[![GitHub license](https://img.shields.io/github/license/doriangrelu/keycloak-config-cli)](https://github.com/doriangrelu/keycloak-config-cli/blob/main/LICENSE.txt)

# keycloak-config-cli

keycloak-config-cli applies Keycloak configuration as code: realm files in JSON or YAML, in the Keycloak export format, kept in Git and applied through the admin API, without restarting Keycloak. It covers realms, clients, client scopes, roles, groups, users, authentication flows, identity providers and more.

Like `kubectl apply`, every run compares the files with Keycloak. For each resource type the files of a realm declare, what they declare is created or updated, and what no file declares is deleted. Users, resources Keycloak creates itself and [protected resources](documentation/advanced/protected-resources.md) are never deleted, and types the files do not declare are not touched.

> **Upgrading from 6.x?** Version 7.0.0 removes several options and changes how deletions work. Read the [migration guide](documentation/getting-started/migration-7.md) and run a [dry run](documentation/advanced/dry-run.md) first.

## Contents

- [Features](#features)
- [Quick start](#quick-start)
- [Interactive shell](#interactive-shell)
- [Documentation](#documentation)
- [Configuration](#configuration)
- [Compatibility with Keycloak](#compatibility-with-keycloak)
- [Docker](#docker)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Declarative and idempotent**: the files are the only source of truth; running the same import twice changes nothing the second time.
- **Incremental**: only the differences with Keycloak are applied, in the order of their dependencies.
- **Safe cleanup**: users, technical resources of Keycloak and protected resources are never deleted.
- **Dry run**: the changes, deletions included, are reported before anything is applied, as text or JSON.
- **CI/CD friendly**: distinct [exit codes](documentation/reference/exit-codes.md) for an invalid configuration, an unavailable Keycloak, a refused request, or pending changes.
- **Templating**: [Mustache](documentation/advanced/templating.md) variables for the values that differ between environments.
- **Normalization**: turns a raw realm export into a minimal file, without the defaults and generated identifiers ([Normalization](documentation/advanced/normalization.md)).
- **Interactive shell**: a guided, offline editor of realm files, with configuration rules ([Shell](documentation/shell/README.md)).

## Quick start

Build the JAR (or download one from the [releases](https://github.com/doriangrelu/keycloak-config-cli/releases)):

```bash
./mvnw clean package -DskipTests
```

Start a local Keycloak:

```bash
docker run --rm -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin123 \
  quay.io/keycloak/keycloak:26.5.3 start-dev
```

Write a realm file, `config/my-realm.yaml`:

```yaml
realm: my-realm
enabled: true
roles:
  realm:
    - name: admin
clients:
  - clientId: my-application
    redirectUris:
      - "https://my-app.example.com/*"
groups:
  - name: administrators
    realmRoles:
      - admin
```

Preview the changes, then apply them:

```bash
java -jar ./target/keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin123 \
  --import.files.locations='./config/*' \
  --import.dry-run=true

java -jar ./target/keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin123 \
  --import.files.locations='./config/*'
```

Keep the files small: start from the [examples](documentation/examples/basic-realm.md), or [normalize](documentation/advanced/normalization.md) an export to remove identifiers and default values. More examples are in the [test resources](src/test/resources/import-files).

To try the tool end to end, the [sample](sample/README.md) starts a throwaway Keycloak with Docker Compose and runs scripted scenarios: dry run, import, cleanup, protected resources, exit codes and the shell.

## Interactive shell

The shell (`--run.operation=SHELL`) is a guided editor for realm files: it asks questions, explains the choices, and writes clients, roles and groups in files you import as they are, with rules that keep the result safe. It works offline, interactively or in a script. See the [shell documentation](documentation/shell/README.md).

## Documentation

The full documentation is in [`documentation/`](documentation/README.md).

| Topic | Pages |
|---|---|
| Getting started | [Installation](documentation/getting-started/installation.md), [Quick start](documentation/getting-started/quick-start.md), [Configuration properties](documentation/getting-started/configuration.md), [Migrating to 7.0.0](documentation/getting-started/migration-7.md) |
| Concepts | [Overview](documentation/concepts/overview.md), [Managed resources](documentation/concepts/managed-resources.md), [Operations and import process](documentation/concepts/import-modes.md) |
| Resource types | [Realms](documentation/configuration/realms.md), [Clients](documentation/configuration/clients.md), [Client scopes](documentation/configuration/client-scopes.md), [Roles](documentation/configuration/roles.md), [Groups](documentation/configuration/groups.md), [Users](documentation/configuration/users.md), [Authentication flows](documentation/configuration/authentication-flows.md), [Identity providers](documentation/configuration/identity-providers.md) |
| Advanced | [Dry run](documentation/advanced/dry-run.md), [Normalization](documentation/advanced/normalization.md), [Protected resources](documentation/advanced/protected-resources.md), [Templating with Mustache](documentation/advanced/templating.md) |
| Reference | [Supported features](documentation/reference/features.md), [Import file patterns](documentation/reference/import-patterns.md), [Exit codes](documentation/reference/exit-codes.md) |
| Architecture (French) | [ARCHITECTURE.md](ARCHITECTURE.md) |

## Configuration

Every option is a Spring Boot property: a command line argument (`--keycloak.url=...`), an environment variable (`KEYCLOAK_URL`: upper case, dots replaced by underscores, hyphens removed) or a configuration file (`--spring.config.location=application.yaml`). The main ones:

| Property | Default | Description |
|---|---|---|
| `keycloak.url` | - | Keycloak URL, including the web context |
| `keycloak.user` / `keycloak.password` | `admin` / - | Account used with the `password` grant |
| `keycloak.grant-type` | `password` | `password` or `client_credentials` (with `keycloak.client-id` and `keycloak.client-secret`) |
| `import.files.locations` | - | Files, Ant-style patterns or URLs to import |
| `import.dry-run` | `false` | Report the changes without applying them (exit code `10` when changes are pending) |
| `import.mustache.enabled` | `false` | Render the files as Mustache templates, with `import.mustache.variables.*` |
| `run.operation` | `IMPORT` | `IMPORT`, `NORMALIZE` or `SHELL` |

All the properties, with their environment variables and defaults, logging and secrets read from files, are in [Configuration properties](documentation/getting-started/configuration.md). Unknown `keycloak.*` and `import.*` properties are rejected at startup, so an option removed in 7.0.0 fails with exit code `2` instead of being silently ignored.

## Compatibility with Keycloak

| Component | Version |
|-----------|---------|
| Keycloak server | 26.x: every change is built and tested against Keycloak 26.5.3 |
| Keycloak admin client | 26.0.13 |
| Java | 25 or higher |
| Normalization | Exports of Keycloak 19 to 26: reference realms of 19.0.3, 20.0.3, 25.0.5, 26.0.5 and 26.5.3, the closest one being used for other versions |

At startup, keycloak-config-cli reads the version of the server and logs a warning when its major version differs from the one it was built for; it does not refuse to run. To check another version, run the integration tests against it:

```bash
./mvnw verify -Dkeycloak.version=<version>
```

Red Hat SSO 7.x, based on Keycloak 18 and older, is not supported. See [Red Hat SSO compatibility](documentation/reference/rhsso-compatibility.md).

## Docker

No container image is published for now. Build one from the JAR, with a Java 25 runtime:

```dockerfile
FROM eclipse-temurin:25-jre
COPY target/keycloak-config-cli.jar /app/keycloak-config-cli.jar
ENTRYPOINT ["java", "-jar", "/app/keycloak-config-cli.jar"]
```

```bash
./mvnw clean package -DskipTests
docker build -t keycloak-config-cli .

docker run --rm \
  -e KEYCLOAK_URL="http://<keycloak host>:8080/" \
  -e KEYCLOAK_USER="<admin user>" \
  -e KEYCLOAK_PASSWORD="<admin password>" \
  -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=true \
  -e IMPORT_FILES_LOCATIONS='/config/*' \
  -v <your config path>:/config \
  keycloak-config-cli
```

## Contributing

Issues and pull requests are welcome: read [CONTRIBUTING.md](CONTRIBUTING.md) for the build, the tests and the conventions, and [AGENTS.md](AGENTS.md) for the architecture rules and the development workflow (also the entry point for AI coding agents). Report vulnerabilities privately, as explained in [SECURITY.md](SECURITY.md). Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Apache License 2.0, see [LICENSE.txt](LICENSE.txt) and [NOTICE.txt](NOTICE.txt).
