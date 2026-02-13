# Supported Features

This document lists all features supported by Keycloak Config CLI with their version history.

## Feature Matrix

| Feature | Since | Description |
|---------|-------|-------------|
| Create clients | 1.0.0 | Create client configuration (inclusive protocolMappers) while creating or updating realms |
| Update clients | 1.0.0 | Update client configuration (inclusive protocolMappers) while updating realms |
| Manage fine-grained authorization of clients | 2.2.0 | Add and remove fine-grained authorization resources and policies of clients |
| Add roles | 1.0.0 | Add roles while creating or updating realms |
| Update roles | 1.0.0 | Update role properties while updating realms |
| Add composites to roles | 1.3.0 | Add role with realm-level and client-level composite roles |
| Remove composites from roles | 1.3.0 | Remove realm-level and client-level composite roles from existing role |
| Add users | 1.0.0 | Add users (inclusive password!) while creating or updating realms |
| Add users with roles | 1.0.0 | Add users with realm-level and client-level roles |
| Update users | 1.0.0 | Update user properties (inclusive password!) while updating realms |
| Add role to user | 1.0.0 | Add realm-level and client-level roles to user |
| Remove role from user | 1.0.0 | Remove realm-level or client-level roles from user |
| Add groups to user | 2.0.0 | Add groups to user while updating realm |
| Remove groups from user | 2.0.0 | Remove groups from user while updating realm |
| Add authentication flows | 1.0.0 | Add authentication flows and executions |
| Update authentication flows | 1.0.0 | Update authentication flow properties and executions |
| Remove authentication flows | 2.0.0 | Remove existing authentication flow properties and executions |
| Update builtin authentication flows | 2.0.0 | Update builtin authentication flow properties and executions |
| Add authentication configs | 1.0.0 | Add authentication configs while creating or updating realms |
| Update authentication configs | 2.0.0 | Update authentication configs while updating realms |
| Remove authentication configs | 2.0.0 | Remove existing authentication configs |
| Add components | 1.0.0 | Add components while creating or updating realms |
| Update components | 1.0.0 | Update components properties while updating realms |
| Remove components | 2.0.0 | Remove existing sub-components |
| Add groups | 1.3.0 | Add groups (inclusive subgroups!) to realm |
| Update groups | 1.3.0 | Update existing group properties and attributes |
| Remove groups | 1.3.0 | Remove existing groups while updating realms |
| Add/Remove group attributes | 1.3.0 | Add or remove group attributes in existing groups |
| Add/Remove group roles | 1.3.0 | Add or remove roles to/from existing groups |
| Update/Remove subgroups | 1.3.0 | Like groups, subgroups may also be added/updated and removed |
| Add scope-mappings | 1.0.0 | Add scope-mappings while creating or updating realms |
| Add roles to scope-mappings | 1.0.0 | Add roles to existing scope-mappings |
| Remove roles from scope-mappings | 1.0.0 | Remove roles from existing scope-mappings |
| Add required-actions | 1.0.0 | Add required-actions while creating or updating realms |
| Update required-actions | 1.0.0 | Update properties of existing required-actions |
| Remove required-actions | 2.0.0 | Remove existing required-actions |
| Add identity providers | 1.2.0 | Add identity providers while creating or updating realms |
| Update identity providers | 1.2.0 | Update identity providers (improved with 2.0.0) |
| Remove identity providers | 2.0.0 | Remove identity providers while updating realms |
| Add identity provider mappers | 2.0.0 | Add identityProviderMappers while updating realms |
| Update identity provider mappers | 2.0.0 | Update identityProviderMappers |
| Remove identity provider mappers | 2.0.0 | Remove identityProviderMappers |
| Add clientScopes | 2.0.0 | Add clientScopes (inclusive protocolMappers) |
| Update clientScopes | 2.0.0 | Update existing clientScopes |
| Remove clientScopes | 2.0.0 | Remove existing clientScopes |
| Add clientScopeMappings | 2.5.0 | Add clientScopeMapping while creating or updating realms |
| Update clientScopeMappings | 2.5.0 | Update existing clientScopeMappings |
| Remove clientScopeMappings | 2.5.0 | Remove existing clientScopeMappings |
| Synchronize user federation | 3.5.0 | Synchronize the user federation defined on the realm configuration |
| Synchronize user profile | 5.4.0 | Synchronize the user profile configuration |
| Synchronize client-policies | 5.6.0 | Synchronize the client-policies (clientProfiles and clientPolicies) |
| Synchronize message bundles | 5.12.0 | Synchronize message bundles defined on the realm configuration |
| Normalize realm exports | - | Normalize a full realm export to be more minimal |

## Specificities

### Client - authenticationFlowBindingOverrides

Keycloak configures `authenticationFlowBindingOverrides` using UUIDs:

```json
{
  "authenticationFlowBindingOverrides": {
    "browser": "ad7d518c-4129-483a-8351-e1223cb8eead"
  }
}
```

keycloak-config-cli uses **aliases** instead (automatically resolved to IDs):

```json
{
  "authenticationFlowBindingOverrides": {
    "browser": "my awesome browser flow"
  }
}
```

### User - Initial Password

To set an initial password that is only respected while the user is created, set the `userLabel` to `initial`:

```json
{
  "users": [
    {
      "username": "user",
      "email": "user@mail.de",
      "enabled": true,
      "credentials": [
        {
          "type": "password",
          "userLabel": "initial",
          "value": "start123"
        }
      ]
    }
  ]
}
```

## Fine-Grained Admin Permissions (FGAP)

### FGAP V1 (Keycloak < 26.2)

Resources and policies are configured on `realm-management` client. Use dollar syntax for ID resolution:

| Resource | Permission | Resolution |
|----------|------------|------------|
| `client.resource.$client-id` | `<scope>.permission.client.$client-id` | Find client by ID |
| `idp.resource.$alias` | `<scope>.permission.idp.$alias` | Find IdP by alias |
| `role.resource.$Realm Role Name` | `<scope>.permission.$Realm Role Name` | Find realm role |
| `group.resource.$/Full Path/To Group` | `<scope>.permission.group.$/Full/Path` | Find group by path |

### FGAP V2 (Keycloak 26.2+)

V2 uses `admin-permissions` client (system-managed). Enable via:

```yaml
realm: my-realm
adminPermissionsEnabled: true
```

**Important**: Do NOT include `admin-permissions` client in your import configuration.

V2 Resource Types and Scopes:

| Resource Type | Available Scopes |
|---------------|------------------|
| Clients | view, manage, map-roles, map-roles-client-scope, map-roles-composite |
| Groups | manage-members, manage-membership, view, manage, view-members, impersonate-members |
| Users | manage-group-membership, view, map-roles, manage, impersonate |
| Roles | map-role, map-role-composite, map-role-client-scope |

## Keycloak 25.0.1+ Basic Scope

The `basic` scope is required for `sub` claim. Add it to your client configurations:

```yaml
defaultClientScopes:
  - "basic"
  - "email"
```
