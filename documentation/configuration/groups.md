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

## Cleanup

When at least one import file of the realm has a `groups` key (even `groups: []`), the groups that no file declares are deleted. Without the key, groups are not touched. See [Managed Resources](../concepts/managed-resources.md).

### How Cleanup Works

The CLI performs two cleanup phases during the `cleanRealm` step:

#### Phase 1: Role Mappings Cleanup

Before deleting any group, the CLI removes orphaned role mappings from all imported groups:

1. **Flattens the group hierarchy** into a path-based map (including all subgroups)
2. **For each imported group**, fetches its current state from Keycloak
3. **Compares realm roles**: any realm role present in Keycloak but absent from the import is removed
4. **Compares client roles**: for each client, roles present in Keycloak but absent from the import are removed. If an entire client is absent from the import, all its roles are removed from the group

Example:
```yaml
# Your configuration
groups:
  - name: developers
    realmRoles:
      - user
    clientRoles:
      my-api:
        - read
```

If Keycloak has:
- Realm roles: `user`, `admin` -> `admin` will be removed
- Client roles for `my-api`: `read`, `write` -> `write` will be removed
- Client roles for `old-client`: `view` -> all roles for `old-client` will be removed

#### Phase 2: Orphaned Groups Deletion

After role mappings cleanup, the CLI deletes orphaned groups:

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

Groups matched by a `protectedResources` rule are never deleted:

```yaml
protectedResources:
  - rule: EQUALS
    matcher: external-admins
  - rule: PATTERN
    matcher: "ext-.*"
```

See [Protected Resources](../advanced/protected-resources.md).

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

> **Note**: This creates new groups under `engineering`. Since `groups` is declared, the old root-level `team-a` and `team-b` are deleted.

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

### 4. Declare Groups Only When the Files Hold Them All

Once a `groups` key is present, every group missing from the files is deleted. Leave the key out while groups are still managed elsewhere, or protect those groups with `protectedResources`.

### 5. Run a Dry Run First

Before adding the `groups` key in production:
1. Export current groups from Keycloak
2. Compare with your configuration
3. Run a [dry run](../advanced/dry-run.md) to see which groups would be deleted
4. Test in development environment first

## Troubleshooting

### Group Not Created

Check that:
- Parent groups exist (for subgroups)
- Realm roles exist (if assigning roles)
- Client exists (if assigning client roles)
- Client roles exist within the client

### Group Not Deleted

A group missing from the files might not be deleted if:
- No import file of the realm has a `groups` key
- A `protectedResources` rule matches its name
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
