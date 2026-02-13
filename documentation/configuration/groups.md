# Groups Configuration

Groups in Keycloak provide a way to manage common attributes and role mappings for a set of users. Keycloak Config CLI provides full support for hierarchical group management.

## Basic Group Definition

```yaml
realm: my-realm
groups:
  - name: employees
  - name: contractors
  - name: guests
```

## Group with Attributes

```yaml
groups:
  - name: engineering
    attributes:
      department:
        - "Engineering"
      cost-center:
        - "ENG-001"
```

## Group with Realm Roles

Assign realm-level roles to a group:

```yaml
groups:
  - name: administrators
    realmRoles:
      - admin
      - user
  - name: users
    realmRoles:
      - user
```

## Group with Client Roles

Assign client-specific roles to a group:

```yaml
groups:
  - name: api-consumers
    clientRoles:
      my-api-client:
        - read
        - write
      another-client:
        - view
```

## Hierarchical Groups (Subgroups)

Groups can be nested to create a hierarchy:

```yaml
groups:
  - name: company
    subGroups:
      - name: engineering
        subGroups:
          - name: backend
          - name: frontend
          - name: devops
      - name: sales
        subGroups:
          - name: north-america
          - name: europe
          - name: asia
      - name: hr
```

This creates the structure:
```
/company
├── /company/engineering
│   ├── /company/engineering/backend
│   ├── /company/engineering/frontend
│   └── /company/engineering/devops
├── /company/sales
│   ├── /company/sales/north-america
│   ├── /company/sales/europe
│   └── /company/sales/asia
└── /company/hr
```

## Subgroups with Roles

Each level can have its own role assignments:

```yaml
groups:
  - name: engineering
    realmRoles:
      - employee
    subGroups:
      - name: backend
        realmRoles:
          - developer
        clientRoles:
          api-service:
            - admin
      - name: frontend
        realmRoles:
          - developer
        clientRoles:
          web-app:
            - admin
```

## Complete Group Example

```yaml
groups:
  - name: organization
    attributes:
      type:
        - "root"
    realmRoles:
      - base-access
    subGroups:
      - name: administrators
        attributes:
          level:
            - "admin"
        realmRoles:
          - admin
          - user-manager
        clientRoles:
          realm-management:
            - manage-users
            - view-users
      - name: departments
        subGroups:
          - name: engineering
            attributes:
              budget-code:
                - "ENG-2024"
            realmRoles:
              - developer
            subGroups:
              - name: platform
                realmRoles:
                  - platform-admin
              - name: product
                clientRoles:
                  product-api:
                    - write
          - name: finance
            attributes:
              budget-code:
                - "FIN-2024"
            realmRoles:
              - finance-viewer
```

## Full Management Mode

To enable automatic deletion of groups not in your configuration:

```yaml
import:
  managed:
    group: full
```

### How Recursive Deletion Works

When using `full` management mode, the CLI:

1. **Builds a path map** of all groups in your configuration (including subgroups)
2. **Compares with Keycloak** groups at each level
3. **Deletes orphaned groups** recursively from leaf to root

Example:
```yaml
# Your configuration
groups:
  - name: dept-a
    subGroups:
      - name: team-1
      - name: team-2
```

If Keycloak has `/dept-a/team-1`, `/dept-a/team-2`, `/dept-a/team-3`:
- `/dept-a/team-3` will be deleted (not in config)
- `/dept-a/team-1` and `/dept-a/team-2` remain

### Protected Groups

Groups with special prefixes are never deleted:

```yaml
# These groups won't be deleted even in full mode
groups:
  - name: _keycloak_internal  # Protected prefix
```

## Updating Groups

### Adding a Subgroup

```yaml
# Before
groups:
  - name: engineering
    subGroups:
      - name: backend

# After
groups:
  - name: engineering
    subGroups:
      - name: backend
      - name: frontend  # New subgroup
```

### Changing Roles

```yaml
# Before
groups:
  - name: developers
    realmRoles:
      - user

# After
groups:
  - name: developers
    realmRoles:
      - user
      - developer  # Added role
```

### Restructuring Groups

```yaml
# Before
groups:
  - name: team-a
  - name: team-b

# After (move under department)
groups:
  - name: engineering
    subGroups:
      - name: team-a
      - name: team-b
```

> **Note**: This creates new groups under `engineering`. With `full` mode, old root-level `team-a` and `team-b` are deleted.

## Best Practices

### 1. Use Meaningful Names

```yaml
groups:
  - name: finance-department  # Good: clear purpose
  - name: grp1               # Bad: unclear
```

### 2. Document with Attributes

```yaml
groups:
  - name: contractors
    attributes:
      description:
        - "External contractors with limited access"
      owner:
        - "hr@company.com"
```

### 3. Organize Hierarchically

```yaml
groups:
  - name: by-department
    subGroups:
      - name: engineering
      - name: sales
  - name: by-role
    subGroups:
      - name: managers
      - name: developers
```

### 4. Start with no-delete

```yaml
import:
  managed:
    group: no-delete  # Safe default
```

### 5. Test Full Mode in Development

Before enabling `full` mode in production:
1. Export current groups from Keycloak
2. Compare with your configuration
3. Verify which groups would be deleted
4. Test in development environment first

## Troubleshooting

### Group Not Created

Check that:
- Parent groups exist (for subgroups)
- Realm roles exist (if assigning roles)
- Client exists (if assigning client roles)
- Client roles exist within the client

### Group Not Deleted

With `full` mode, groups might not delete if:
- They have a protected prefix
- The path doesn't match exactly
- There's a configuration error

### Role Assignment Failed

Ensure roles are defined before groups:

```yaml
# Define roles first
roles:
  realm:
    - name: developer

# Then assign to groups
groups:
  - name: engineering
    realmRoles:
      - developer  # Now this works
```
