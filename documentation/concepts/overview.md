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
3. **CLI reads** every file before changing anything: an invalid file changes nothing in Keycloak
4. **CLI compares** the desired state with the current Keycloak state, on every run
5. **CLI applies** only the necessary changes, and deletes the resources of the declared types that no file declares (see [Managed Resources](managed-resources.md))

## Core Principles

### Declarative Configuration

You declare the desired end state, not the steps to get there:

```yaml
# You declare: "I want these groups to exist"
groups:
  - name: administrators
  - name: users
  - name: guests

# The CLI figures out: "Create administrators, update users, keep guests,
# delete every other group that is not protected"
```

### Idempotency

Running the same import multiple times produces the same result. If nothing changed in your configuration nor in Keycloak, nothing changes in Keycloak. If someone changed a declared resource by hand in Keycloak, the next import undoes the change.

### Incremental Updates

Only the differences are applied:

```
First run:  Create realm, create 10 groups, create 5 clients
Second run: (no changes in config) → "Nothing to update"
Third run:  (added 1 group) → Create 1 group only
```

## Architecture

Keycloak Config CLI is a Spring Boot command line application. It reads and parses the import files, builds the desired state of each realm, and a reconciliation engine compares it with Keycloak through the Keycloak Admin REST API, then creates, updates and deletes what differs.

Nothing is stored between runs: no checksum, no cache, no state in Keycloak. Every run compares every file with Keycloak.

## Supported Resources

| Resource | Create | Update | Delete (missing from a declared type) |
|----------|--------|--------|---------------------------------------|
| Realms | Yes | Yes | No |
| Clients | Yes | Yes | Yes |
| Client Scopes | Yes | Yes | Yes |
| Groups (hierarchical) | Yes | Yes | Yes |
| Roles (realm & client) | Yes | Yes | Yes |
| Users | Yes | Yes | No |
| Identity Providers | Yes | Yes | Yes |
| Authentication Flows | Yes | Yes | Yes |
| Required Actions | Yes | Yes | Yes |
| Components | Yes | Yes | Yes |
| Scope Mappings | Yes | Yes | Yes |

## Next Steps

- [Managed Resources](managed-resources.md) - Learn about resource lifecycle management
- [Import Modes](import-modes.md) - How an import works, file selection and templating
- [Quick Start](../getting-started/quick-start.md) - Get hands-on experience
