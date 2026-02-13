# Managed Resources

This document explains how Keycloak Config CLI manages the lifecycle of resources.

## Management Modes

Each resource type can be configured with one of two management modes:

### `no-delete` (Default)

- **Creates** new resources that don't exist
- **Updates** existing resources that have changed
- **Never deletes** resources, even if they're not in your configuration

This is the safe default that prevents accidental data loss.

```yaml
import:
  managed:
    group: no-delete  # Default
    client: no-delete
```

### `full`

- **Creates** new resources that don't exist
- **Updates** existing resources that have changed
- **Deletes** resources that exist in Keycloak but not in your configuration

Use this when you want complete control over your configuration.

```yaml
import:
  managed:
    group: full
    client: full
```

## Resource-Specific Configuration

You can configure each resource type independently:

```yaml
import:
  managed:
    # Full lifecycle management
    group: full
    realm-role: full
    client-role: full
    client-scope: full

    # Safe mode - never delete
    client: no-delete        # Clients might have active sessions
    user: no-delete          # Never auto-delete users
    identity-provider: no-delete
```

## How Deletion Works

When using `full` management mode, the deletion process is:

### 1. Groups (Hierarchical)

Groups support nested subgroups. The deletion is recursive:

```yaml
# Your configuration
groups:
  - name: department-a
    subGroups:
      - name: team-1
      - name: team-2
  - name: department-b
```

If Keycloak has:
```
/department-a
  /team-1
  /team-2
  /team-3    <- Not in config, will be DELETED
/department-b
/department-c  <- Not in config, will be DELETED
```

The CLI will delete:
- `/department-a/team-3` (subgroup not in config)
- `/department-c` (group not in config)

### 2. Roles

Realm roles and client roles are managed separately:

```yaml
import:
  managed:
    realm-role: full   # Manages realm-level roles
    client-role: full  # Manages client-level roles
```

### 3. Clients

Client deletion removes the client and all its associated:
- Client roles
- Client scopes
- Protocol mappers
- Service account (if any)

## Protected Resources

Some resources are protected from deletion regardless of management mode:

### Protected Groups

Groups with names starting with certain prefixes are protected:

```java
// These groups won't be deleted even in full mode
"_keycloak_"  // Keycloak internal groups
```

### Default Roles

Built-in Keycloak roles are never deleted:
- `offline_access`
- `uma_authorization`
- Default client roles (like `account` client roles)

### Default Clients

Built-in Keycloak clients are never deleted:
- `account`
- `account-console`
- `admin-cli`
- `broker`
- `realm-management`
- `security-admin-console`

## Best Practices

### Start with `no-delete`

When first setting up, use the default `no-delete` mode:

```yaml
import:
  managed:
    group: no-delete
    client: no-delete
```

### Enable `full` Gradually

Once you're confident in your configuration, enable `full` mode for specific resources:

```yaml
import:
  managed:
    group: full       # Groups are well-defined
    realm-role: full  # Roles are well-defined
    client: no-delete # Still cautious about clients
```

### Use Different Modes per Environment

```yaml
# development.yaml
import:
  managed:
    group: full
    client: full

# production.yaml
import:
  managed:
    group: full
    client: no-delete  # More conservative in production
```

### Review Before Enabling `full`

Before enabling `full` mode:

1. Export current configuration from Keycloak
2. Compare with your configuration files
3. Identify resources that would be deleted
4. Decide if that's the desired outcome

## Handling External Changes

When using `full` mode, be aware that:

- Resources created manually in Keycloak Admin UI will be deleted
- Changes made by other tools will be overwritten
- Only the configuration files are the source of truth

To prevent issues:
- Use `no-delete` mode if multiple tools manage Keycloak
- Document that all changes must go through configuration files
- Use protected prefixes for resources managed externally

## Example Scenarios

### Scenario 1: Add a New Team

```yaml
# Before
groups:
  - name: engineering
    subGroups:
      - name: backend
      - name: frontend

# After (add devops team)
groups:
  - name: engineering
    subGroups:
      - name: backend
      - name: frontend
      - name: devops  # New team
```

Result: `devops` group is created. No deletions.

### Scenario 2: Remove a Team (with full mode)

```yaml
# Before
groups:
  - name: engineering
    subGroups:
      - name: backend
      - name: frontend
      - name: legacy  # Being removed

# After
groups:
  - name: engineering
    subGroups:
      - name: backend
      - name: frontend
```

Result with `group: full`: `legacy` group is deleted.
Result with `group: no-delete`: `legacy` group remains in Keycloak.

### Scenario 3: Reorganize Groups

```yaml
# Before
groups:
  - name: team-a
  - name: team-b

# After (reorganize under departments)
groups:
  - name: department-1
    subGroups:
      - name: team-a
      - name: team-b
```

Result with `group: full`:
- Old `team-a` and `team-b` at root level are deleted
- New `department-1/team-a` and `department-1/team-b` are created

> **Note**: This is a delete + create, not a move. Group IDs will change.
