# Keycloak Config CLI Documentation

Welcome to the **Keycloak Config CLI** documentation. This tool enables you to import JSON/YAML-formatted configuration files into Keycloak using its REST interface, following the **Configuration as Code** approach.

## Table of Contents

### Getting Started
- [Installation](getting-started/installation.md)
- [Quick Start](getting-started/quick-start.md)
- [Configuration Properties](getting-started/configuration.md)
- [Migrating to 7.0.0](getting-started/migration-7.md)

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
- [Dry Run](advanced/dry-run.md)
- [Normalization](advanced/normalization.md)
- [Protected Resources](advanced/protected-resources.md)
- [Templating with Mustache](advanced/templating.md)

### Technical Reference
- [Supported Features](reference/features.md)
- [Import File Patterns](reference/import-patterns.md)
- [Managed Resources Details](reference/managed-resources.md)
- [Normalization Technical Details](reference/normalization-details.md)
- [Red Hat SSO Compatibility](reference/rhsso-compatibility.md)
- [Skip Server Info](reference/skip-server-info.md)
- [Exit Codes](reference/exit-codes.md)

---

## What is Keycloak Config CLI?

Keycloak Config CLI is a command-line tool that allows you to:

- **Apply** realm configurations to Keycloak from JSON or YAML files, like `kubectl apply` with manifests
- **Update** existing configurations: only what differs from Keycloak is changed
- **Delete** what the files no longer declare, for the resource types they declare, except users, technical resources of Keycloak and protected resources
- **Preview** the changes with a dry run before applying them
- **Automate** Keycloak configuration in CI/CD pipelines, with distinct exit codes

## Key Features

- **Declarative Configuration**: The import files are the only source of truth
- **Idempotent Operations**: Run the same import multiple times safely
- **Incremental Updates**: Only apply changes when necessary
- **Safe Cleanup**: Users, technical resources and protected resources are never deleted
- **Templating**: Mustache variables for the values that differ between environments
- **Multiple File Formats**: Support for JSON and YAML

Upgrading from 6.x? Read [Migrating to 7.0.0](getting-started/migration-7.md).

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

- Java 25 or higher
- Keycloak 26.x (built and tested against Keycloak 26.5.3)

## License

This project is licensed under the Apache License 2.0.
