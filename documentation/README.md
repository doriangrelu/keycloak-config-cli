# Keycloak Config CLI Documentation

Welcome to the **Keycloak Config CLI** documentation. This tool enables you to import JSON/YAML-formatted configuration files into Keycloak using its REST interface, following the **Configuration as Code** approach.

## Table of Contents

### Getting Started
- [Installation](getting-started/installation.md)
- [Quick Start](getting-started/quick-start.md)
- [Configuration Properties](getting-started/configuration.md)

### Core Concepts
- [Overview](concepts/overview.md)
- [Managed Resources](concepts/managed-resources.md)
- [Import Modes](concepts/import-modes.md)

### Configuration Reference
- [Realms](configuration/realms.md)
- [Clients](configuration/clients.md)
- [Groups](configuration/groups.md)
- [Roles](configuration/roles.md)
- [Users](configuration/users.md)
- [Authentication Flows](configuration/authentication-flows.md)
- [Identity Providers](configuration/identity-providers.md)
- [Client Scopes](configuration/client-scopes.md)

### Examples
- [Basic Realm Setup](examples/basic-realm.md)
- [Groups and Roles](examples/groups-and-roles.md)
- [Client Configuration](examples/client-configuration.md)
- [Full Realm Example](examples/full-realm-example.md)

### Advanced Topics
- [Parallel Processing](advanced/parallel-processing.md)
- [State Management](advanced/state-management.md)
- [Normalization](advanced/normalization.md)
- [Protected Resources](advanced/protected-resources.md)

### Technical Reference
- [Supported Features](reference/features.md)
- [Import File Patterns](reference/import-patterns.md)
- [Managed Resources Details](reference/managed-resources.md)
- [Normalization Technical Details](reference/normalization-details.md)
- [Red Hat SSO Compatibility](reference/rhsso-compatibility.md)
- [Skip Server Info](reference/skip-server-info.md)

---

## What is Keycloak Config CLI?

Keycloak Config CLI is a command-line tool that allows you to:

- **Import** realm configurations into Keycloak from JSON or YAML files
- **Update** existing configurations with incremental changes
- **Manage** the full lifecycle of Keycloak resources (create, update, delete)
- **Automate** Keycloak configuration in CI/CD pipelines

## Key Features

- **Declarative Configuration**: Define your Keycloak setup as code
- **Idempotent Operations**: Run the same import multiple times safely
- **Incremental Updates**: Only apply changes when necessary
- **Full Resource Management**: Optionally delete resources not in your configuration
- **Parallel Processing**: Speed up imports with parallel execution
- **Multiple File Formats**: Support for JSON and YAML

## Quick Example

```yaml
# realm-config.yaml
realm: my-realm
enabled: true
clients:
  - clientId: my-application
    enabled: true
    redirectUris:
      - "https://my-app.example.com/*"
groups:
  - name: administrators
    realmRoles:
      - admin
  - name: users
```

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=realm-config.yaml
```

## Requirements

- Java 21 or higher
- Keycloak 22.x or higher (compatible with the Keycloak version specified in your build)

## License

This project is licensed under the Apache License 2.0.
