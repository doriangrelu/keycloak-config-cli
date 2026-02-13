# Resource Management Reference

This document provides technical details on how keycloak-config-cli manages resources.

## How Resource Tracking Works

- keycloak-config-cli stores information about created resources as **realm attributes** in Keycloak
- This tracking allows the CLI to manage resources across subsequent runs
- By default, resources created by the CLI will be deleted and recreated if changed

## Management Modes

| Mode | Create | Update | Delete |
|------|--------|--------|--------|
| `no-delete` | Yes | Yes | No |
| `full` | Yes | Yes | Yes |

### Behavior Details

1. **No resources defined**: Keycloak does not touch any resources of that type
2. **Resources defined**: Keycloak ensures those resources exist, deletes others (in `full` mode)
3. **Empty array defined**: Keycloak will delete all resources of that type (in `full` mode)

## Fully Managed Resource Types

| Type | Property Name | Notes |
|------|---------------|-------|
| Groups | `group` | - |
| Required Actions | `required-action` | Copy defaults to import JSON |
| Client Scopes | `client-scope` | - |
| Scope Mappings | `scope-mapping` | - |
| Client Scope Mappings | `client-scope-mapping` | - |
| Roles | `role` | - |
| Components | `component` | Copy defaults to import JSON |
| Sub Components | `sub-component` | Copy defaults to import JSON |
| Authentication Flows | `authentication-flow` | Except built-in flows |
| Identity Providers | `identity-provider` | - |
| Identity Provider Mappers | `identity-provider-mapper` | - |
| Clients | `client` | - |
| Client Authorization Resources | `client-authorization-resources` | 'Default Resource' always included |
| Client Authorization Policies | `client-authorization-policies` | - |
| Client Authorization Scopes | `client-authorization-scopes` | - |
| Message Bundles | `message-bundles` | Only CLI-imported bundles |

## Configuration

### Disable Deletion

```properties
import.managed.required-action=no-delete
import.managed.group=no-delete
import.managed.client=no-delete
```

### Environment Variables

```bash
export IMPORT_MANAGED_GROUP=no-delete
export IMPORT_MANAGED_CLIENT=no-delete
```

## Remote State Management

When `import.remote-state.enabled=true` (default):
- CLI only purges resources it created previously
- Manually created resources are preserved

When `import.remote-state.enabled=false`:
- CLI purges ALL existing entities not in import JSON
- Use with caution

## User Federation Impact

When a user federation is deleted and recreated:
- All users created by that federation are deleted
- Associated data (offline tokens, etc.) is also deleted

Consider using `no-delete` mode for user federations in production.
