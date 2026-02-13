# Protected Resources

Keycloak Config CLI protects certain built-in and critical resources from accidental deletion, even when using `managed: full` mode.

## Overview

Protected resources are resources that:
- Are essential for Keycloak operation
- Were created by Keycloak itself
- Could break functionality if deleted

These resources are **never deleted** by the CLI, regardless of management mode.

## Protected Clients

The following built-in clients are protected:

| Client ID | Purpose |
|-----------|---------|
| `account` | User account management |
| `account-console` | Account management UI |
| `admin-cli` | CLI administration |
| `broker` | Identity brokering |
| `realm-management` | Realm administration |
| `security-admin-console` | Admin console |

### Example

```yaml
import:
  managed:
    client: full  # Won't delete protected clients

clients:
  - clientId: my-app
    # ...
```

Even with `full` mode, the protected clients remain untouched.

## Protected Roles

Built-in realm roles are protected:

| Role | Purpose |
|------|---------|
| `offline_access` | Offline token access |
| `uma_authorization` | UMA authorization |
| `default-roles-{realm}` | Default role assignments |

### Default Client Roles

Each built-in client has default roles that are protected:

**account client:**
- `manage-account`
- `manage-account-links`
- `view-profile`

**realm-management client:**
- `view-realm`
- `manage-realm`
- `view-users`
- `manage-users`
- And many more...

## Protected Client Scopes

Built-in client scopes:

| Scope | Purpose |
|-------|---------|
| `profile` | User profile claims |
| `email` | Email claim |
| `address` | Address claims |
| `phone` | Phone claim |
| `offline_access` | Refresh tokens |
| `roles` | Role claims |
| `web-origins` | CORS origins |
| `microprofile-jwt` | MicroProfile JWT |
| `acr` | Authentication context |

## Protected Groups

Groups with certain prefixes are protected:

```java
// Protected prefixes
"_keycloak_"
```

### Example

```yaml
groups:
  - name: _keycloak_internal
    # This group is protected - won't be deleted
```

## Protected Authentication Flows

Built-in authentication flows:

| Flow | Purpose |
|------|---------|
| `browser` | Standard browser login |
| `direct grant` | Resource owner password |
| `registration` | User registration |
| `reset credentials` | Password reset |
| `clients` | Client authentication |
| `docker auth` | Docker registry auth |
| `http challenge` | HTTP challenge |

## Customizing Protection

### Using Prefixes

Create your own protection patterns:

```yaml
# Groups with special prefix
groups:
  - name: _protected_admin_group
    # Use a naming convention for critical groups
```

Then document that groups starting with `_protected_` should not be removed.

### Environment-Specific Protection

Use different configurations per environment:

```yaml
# production.yaml
import:
  managed:
    client: no-delete  # Never delete clients in production
    group: full        # But manage groups fully
```

```yaml
# development.yaml
import:
  managed:
    client: full
    group: full
```

## Behavior Matrix

| Resource Type | `no-delete` | `full` (protected) | `full` (not protected) |
|---------------|-------------|-------------------|----------------------|
| Built-in client | Keep | Keep | Keep |
| Custom client | Keep | Keep | Delete if not in config |
| Built-in role | Keep | Keep | Keep |
| Custom role | Keep | Keep | Delete if not in config |
| Protected group | Keep | Keep | Keep |
| Custom group | Keep | Keep | Delete if not in config |

## Detecting Protected Resources

### In Logs

Protected resources are logged when encountered:

```
DEBUG Skipping protected client 'account'
DEBUG Skipping protected role 'offline_access'
```

### Verification

To see what would be deleted (dry run isn't available, but you can):

1. Export current configuration
2. Compare with your import files
3. The difference shows potential deletions
4. Protected resources won't be in that list

## Common Questions

### Why can't I delete a built-in client?

Built-in clients are required for Keycloak functionality:
- Removing `account` breaks user self-service
- Removing `admin-cli` breaks CLI administration
- Removing `realm-management` breaks admin console

### Can I disable a built-in client instead?

Yes, you can disable them:

```yaml
clients:
  - clientId: account-console
    enabled: false  # Disabled but not deleted
```

### How do I replace a built-in flow?

Create a custom flow and bind it instead:

```yaml
authenticationFlows:
  - alias: "custom-browser"
    # Your custom flow

# Bind the custom flow
browserFlow: "custom-browser"
# The built-in "browser" flow remains but is unused
```

### What if I really need to delete a protected resource?

You must delete it manually via:
- Keycloak Admin Console
- Keycloak Admin REST API directly
- Keycloak CLI (kcadm.sh)

This is intentional to prevent accidental damage.

## Best Practices

### 1. Understand Before Managing

Know which resources are protected before using `full` mode:

```bash
# List built-in resources
curl -s http://keycloak:8080/admin/realms/my-realm/clients | \
  jq '.[] | select(.id != .clientId) | .clientId'
```

### 2. Use Naming Conventions

Prefix your custom resources for clarity:

```yaml
groups:
  - name: org-engineering    # Organization prefix
  - name: team-backend       # Team prefix

clients:
  - clientId: app-frontend   # App prefix
```

### 3. Document Protected Resources

Maintain documentation of protected resources in your project:

```markdown
# Protected Resources

The following resources are managed by Keycloak and should not be modified:
- Client: account, admin-cli, realm-management
- Roles: offline_access, uma_authorization
- Scopes: profile, email, roles
```

### 4. Test Before Production

Always test your full management configuration in a non-production environment to understand what would be deleted.
