# Overview

Keycloak Config CLI is a powerful tool for managing Keycloak configurations using the **Configuration as Code** paradigm.

## What Problem Does It Solve?

Managing Keycloak through its Admin UI is fine for initial setup or one-off changes, but it becomes problematic when you need to:

- **Reproduce configurations** across multiple environments (dev, staging, production)
- **Version control** your identity and access management setup
- **Automate** configuration changes in CI/CD pipelines
- **Review changes** before applying them (code review for IAM)
- **Roll back** to previous configurations easily

Keycloak Config CLI solves these problems by allowing you to define your entire Keycloak configuration in JSON or YAML files.

## How It Works

```
┌─────────────────┐     ┌─────────────────────┐     ┌──────────────┐
│  Configuration  │     │   Keycloak Config   │     │   Keycloak   │
│     Files       │────▶│        CLI          │────▶│    Server    │
│  (YAML/JSON)    │     │                     │     │              │
└─────────────────┘     └─────────────────────┘     └──────────────┘
```

1. **Define** your configuration in YAML or JSON files
2. **Run** Keycloak Config CLI with your files
3. **CLI compares** the desired state with the current Keycloak state
4. **CLI applies** only the necessary changes

## Core Principles

### Declarative Configuration

You declare the desired end state, not the steps to get there:

```yaml
# You declare: "I want these groups to exist"
groups:
  - name: administrators
  - name: users
  - name: guests

# The CLI figures out: "Create administrators, update users, keep guests"
```

### Idempotency

Running the same import multiple times produces the same result. If nothing changed in your configuration, nothing changes in Keycloak.

### Incremental Updates

Only the differences are applied:

```
First run:  Create realm, create 10 groups, create 5 clients
Second run: (no changes in config) → "Nothing to update"
Third run:  (added 1 group) → Create 1 group only
```

## Architecture

Keycloak Config CLI is built on Spring Boot and consists of several layers:

```
┌─────────────────────────────────────────────────────────────┐
│                    Import Services                          │
│  (RealmImportService, GroupImportService, ClientImport...) │
├─────────────────────────────────────────────────────────────┤
│                     Repositories                            │
│  (RealmRepository, GroupRepository, ClientRepository...)    │
├─────────────────────────────────────────────────────────────┤
│                   Keycloak Admin Client                     │
│              (REST API Communication)                       │
├─────────────────────────────────────────────────────────────┤
│                     Keycloak Server                         │
└─────────────────────────────────────────────────────────────┘
```

### Import Services

Each resource type has a dedicated import service that handles:
- Creating new resources
- Updating existing resources
- Deleting orphaned resources (when managed mode is `full`)

### Repositories

Repositories abstract the Keycloak Admin Client API and provide:
- CRUD operations for each resource type
- Query methods for finding resources
- Bulk operations where applicable

### State Management

The CLI can track the state of previous imports to:
- Skip unchanged configurations
- Detect external changes
- Optimize import performance

## Supported Resources

| Resource | Create | Update | Delete (full mode) |
|----------|--------|--------|-------------------|
| Realms | Yes | Yes | No |
| Clients | Yes | Yes | Yes |
| Client Scopes | Yes | Yes | Yes |
| Groups (hierarchical) | Yes | Yes | Yes |
| Roles (realm & client) | Yes | Yes | Yes |
| Users | Yes | Yes | Yes |
| Identity Providers | Yes | Yes | Yes |
| Authentication Flows | Yes | Yes | Yes |
| Required Actions | Yes | Yes | Yes |
| Components | Yes | Yes | Yes |
| Scope Mappings | Yes | Yes | Yes |

## Next Steps

- [Managed Resources](managed-resources.md) - Learn about resource lifecycle management
- [Import Modes](import-modes.md) - Understand different import strategies
- [Quick Start](../getting-started/quick-start.md) - Get hands-on experience
