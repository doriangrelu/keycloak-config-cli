# Resource Management Reference

This document provides technical details on how keycloak-config-cli manages resources. For an introduction, see [Managed Resources](../concepts/managed-resources.md).

## Cleanup Rule

The rule applies per realm, over all the import files of the realm. There is no option to change it.

| Import files | Create | Update | Delete |
|--------------|--------|--------|--------|
| Type key absent from all files | No | No | No |
| Type key present with an empty list | No | No | Every resource of the type |
| Type key present with resources | Yes | Yes | Every resource of the type that no file declares |

Inside a declared resource, what it omits is removed too: role mappings of a group, subgroups, roles of a scope mapping, texts of a message bundle locale.

Deletions never apply to:

1. Users
2. Technical resources created by Keycloak (see [Protected Resources](../advanced/protected-resources.md#technical-resources))
3. Resources matched by a `protectedResources` rule of the import files

## Removed Properties

The per-type managed mode properties under `import` (`managed.group`, `managed.client`, ...) and their `IMPORT_MANAGED_*` environment variables no longer exist. Unknown `import.*` properties are rejected, so a configuration that still sets one fails at startup. Remove them from your configuration.

| Before | Now |
|--------|-----|
| Type set to never delete | Leave the type key out of the files, or protect its resources with `protectedResources` |
| Type set to delete missing resources | Default behaviour for every declared type |

## Cleaned Resource Types

| Type | Import key | Notes |
|------|------------|-------|
| Groups | `groups` | Subgroups and role mappings included |
| Required Actions | `requiredActions` | Built-in required actions are kept (disable them instead) |
| Client Scopes | `clientScopes` | Built-in and default/optional scopes of the realm are kept |
| Scope Mappings | `scopeMappings` | `offline_access` mapping kept |
| Client Scope Mappings | `clientScopeMappings` | `account-console` mapping kept |
| Realm Roles | `roles.realm` | Built-in realm roles are kept |
| Client Roles | `roles.client` | Roles of Keycloak clients are kept |
| Components | `components` | Key providers, client registration policies and user profile are kept |
| Authentication Flows | `authenticationFlows` | Built-in flows are kept |
| Authenticator Configs | `authenticatorConfig` | Configs used by built-in flows are kept |
| Identity Providers | `identityProviders` | - |
| Identity Provider Mappers | `identityProviderMappers` | - |
| Clients | `clients` | Built-in clients are kept |
| Client Authorization Settings | `authorizationSettings` of a client | Resources, policies and scopes |
| Message Bundles | `messageBundles` | Texts of a locale are exactly the declared ones |
| Users | `users` | Created and updated, never deleted |

## Remote State Management

When `import.remote-state.enabled=true`, deletions are further limited to resources keycloak-config-cli created in a previous run: resources created by other means are kept, even if their type is declared. See [State Management](../advanced/state-management.md).

## User Federation Impact

When a user federation component is deleted and recreated:
- All users created by that federation are deleted
- Associated data (offline tokens, etc.) is also deleted

Keep user federations declared in your files, or protect them with a `protectedResources` rule, and check the deletions with a [dry run](../advanced/dry-run.md) before applying.
