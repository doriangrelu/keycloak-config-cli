# Roles Configuration

Roles in Keycloak define permissions and can be assigned to users or groups. Keycloak Config CLI supports both realm roles and client roles.

## Realm Roles

Realm roles are global to the realm and can be assigned to any user or group.

### Basic Role Definition

```yaml
realm: my-realm
roles:
  realm:
    - name: admin
    - name: user
    - name: guest
```

### Roles with Description

```yaml
roles:
  realm:
    - name: admin
      description: "Full administrative access"
    - name: user
      description: "Standard user access"
    - name: auditor
      description: "Read-only access for audit purposes"
```

### Roles with Attributes

```yaml
roles:
  realm:
    - name: premium-user
      description: "Premium subscription user"
      attributes:
        subscription-level:
          - "premium"
        max-api-calls:
          - "10000"
```

## Client Roles

Client roles are specific to a client application.

### Basic Client Roles

```yaml
clients:
  - clientId: my-api
    # ... other client config

roles:
  client:
    my-api:
      - name: read
      - name: write
      - name: admin
```

### Client Roles with Description

```yaml
roles:
  client:
    my-api:
      - name: read
        description: "Can read resources"
      - name: write
        description: "Can create and modify resources"
      - name: delete
        description: "Can delete resources"
```

## Composite Roles

Composite roles include other roles. When assigned, all included roles are granted.

### Realm Composite Roles

```yaml
roles:
  realm:
    - name: viewer
      description: "Basic viewing permissions"
    - name: editor
      description: "Can edit content"
    - name: publisher
      description: "Can publish content"
    - name: content-manager
      description: "Full content management"
      composite: true
      composites:
        realm:
          - viewer
          - editor
          - publisher
```

### Client Role Composites

```yaml
roles:
  realm:
    - name: api-full-access
      composite: true
      composites:
        client:
          my-api:
            - read
            - write
            - delete
```

### Mixed Composites

```yaml
roles:
  realm:
    - name: super-admin
      description: "Complete system access"
      composite: true
      composites:
        realm:
          - admin
          - user-manager
        client:
          realm-management:
            - manage-users
            - manage-clients
            - manage-realm
          my-api:
            - admin
```

## Complete Roles Example

```yaml
realm: my-realm

roles:
  # Realm-level roles
  realm:
    # Basic roles
    - name: user
      description: "Standard user"
    - name: admin
      description: "Administrator"

    # Feature-based roles
    - name: can-export
      description: "Can export data"
    - name: can-import
      description: "Can import data"

    # Composite roles
    - name: data-manager
      description: "Full data management access"
      composite: true
      composites:
        realm:
          - can-export
          - can-import

    - name: super-admin
      composite: true
      composites:
        realm:
          - admin
          - data-manager
        client:
          realm-management:
            - manage-users
            - manage-clients

  # Client-specific roles
  client:
    # API service roles
    api-service:
      - name: read
        description: "Read API resources"
      - name: write
        description: "Write API resources"
      - name: admin
        description: "Administer API"
        composite: true
        composites:
          client:
            api-service:
              - read
              - write

    # Web application roles
    web-app:
      - name: viewer
      - name: editor
      - name: publisher
```

## Assigning Roles

### To Groups

```yaml
groups:
  - name: administrators
    realmRoles:
      - admin
      - user
    clientRoles:
      realm-management:
        - manage-users
```

### To Users

```yaml
users:
  - username: john.doe
    realmRoles:
      - user
    clientRoles:
      my-api:
        - read
        - write
```

### Default Roles

Set roles that are automatically assigned to new users:

```yaml
realm: my-realm
defaultRole:
  name: default-roles-my-realm
  composites:
    realm:
      - user
```

## Full Management Mode

To enable automatic deletion of roles not in your configuration:

```yaml
import:
  managed:
    realm-role: full
    client-role: full
```

### Protected Roles

Some built-in roles are never deleted:
- `offline_access`
- `uma_authorization`
- Default client roles

## Role Naming Best Practices

### Use Descriptive Names

```yaml
roles:
  realm:
    - name: can-manage-users        # Good: action-based
    - name: invoice-approver        # Good: role-based
    - name: role1                   # Bad: unclear
```

### Use Consistent Naming

```yaml
roles:
  realm:
    # Consistent pattern: action-resource
    - name: read-reports
    - name: write-reports
    - name: delete-reports
    - name: read-users
    - name: write-users
```

### Prefix Client Roles

```yaml
roles:
  client:
    my-api:
      - name: api-read      # Prefixed for clarity
      - name: api-write
      - name: api-admin
```

## Troubleshooting

### Role Not Created

Check that:
- The realm exists
- For client roles, the client exists
- Role names are unique within their scope

### Composite Role Failed

Ensure referenced roles exist:

```yaml
roles:
  realm:
    # Define base roles first
    - name: viewer
    - name: editor

    # Then define composite
    - name: content-manager
      composite: true
      composites:
        realm:
          - viewer    # Must exist
          - editor    # Must exist
```

### Role Assignment Failed

Verify the role exists before assigning:

```yaml
# In file 01-roles.yaml
roles:
  realm:
    - name: admin

# In file 02-groups.yaml (processed after roles)
groups:
  - name: administrators
    realmRoles:
      - admin  # Now exists
```

## Migration Tips

### From Manual Configuration

1. Export existing roles from Keycloak Admin Console
2. Convert to YAML/JSON format
3. Remove auto-generated fields (id, containerId)
4. Add to your configuration files

### Reorganizing Roles

When restructuring roles:
1. Add new roles first
2. Update group/user assignments
3. Remove old roles (with `full` mode) or manually

### Handling Role Dependencies

Process files in order:
```
01-realm.yaml       # Realm settings
02-clients.yaml     # Clients (for client roles)
03-roles.yaml       # All roles
04-groups.yaml      # Groups with role assignments
05-users.yaml       # Users with role assignments
```
