# Managed Resources

This document explains how Keycloak Config CLI decides what to create, update and delete.

## The Import Files Are the Source of Truth

Like `kubectl apply` with manifests, keycloak-config-cli makes Keycloak match the import files:

- **Creates** the resources the files declare and Keycloak does not have
- **Updates** the resources that differ from the files
- **Deletes** the resources of a declared type that no file declares

There is no per-type mode and no option to turn deletion off: the former per-type properties under `import` (`managed.group`, `managed.client`, ...) no longer exist. A configuration that still sets one fails at startup, because unknown `import.*` properties are rejected.

> **Tip**: A cleanup deletes what is missing from the files. Run a [dry run](../advanced/dry-run.md) (`--import.dry-run=true`) before applying a change, and review the deletions it reports.

## The Cleanup Rule

The rule is evaluated per realm, over all the import files of that realm.

### A declared type is cleaned

A resource type is declared when at least one import file of the realm contains its key, even with an empty list. Every resource of that type that exists in Keycloak but that no file declares is deleted.

### An absent type is not touched

A type whose key is absent from all the files of the realm is left as it is in Keycloak.

| Import files | Effect on groups |
|--------------|------------------|
| No `groups` key | No group is created, updated or deleted |
| `groups: []` | Every group is deleted (except protected ones) |
| `groups: [ {name: admins} ]` | `admins` is created or updated, every other group is deleted (except protected ones) |

### What a declared resource omits is removed

Inside a declared resource, the files describe the whole resource:

- A group declared without role mappings loses the role mappings it has in Keycloak
- A subgroup missing from a declared group is deleted
- The roles of a scope mapping are exactly the declared ones
- The texts of a message bundle locale are exactly the declared ones

Two exceptions:

- A component that declares at least one sub-component has exactly those; a component that declares none keeps its own, such as the mappers Keycloak creates with an LDAP user storage
- A client with `allowRemoteResourceManagement` (the Keycloak default) keeps the authorization resources the files do not declare, except the `Default Resource`

Settings the files do not mention at all, such as a realm attribute absent from the import, keep their value.

## Guarantees

Three guarantees are fixed in the code. They have no option.

### 1. Users are never deleted

Users are created and updated from the `users` key, but a user missing from the files is never deleted.

### 2. Technical resources created by Keycloak are never deleted

Built-in clients, roles, client scopes, authentication flows, required actions, scope mappings and components are kept even when no file declares them. See [Protected Resources](../advanced/protected-resources.md#technical-resources) for the full list.

### 3. Resources protected by `protectedResources` are never deleted

An import file can protect resources by name, whatever their type:

```yaml
realm: my-realm
protectedResources:
  - rule: EQUALS
    matcher: legacy-app
  - rule: PATTERN
    matcher: "team-.*"
```

See [Protected Resources](../advanced/protected-resources.md) for the details.

## How Deletion Works

### Groups

Groups support nested subgroups. For each declared group, the CLI compares its current state in Keycloak with the files:

- **Realm roles** not in the files are removed from the group
- **Client roles** not in the files are removed from the group
- **Subgroups** not in the files are deleted

Top-level groups that no file declares are deleted.

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

### Clients

Client deletion removes the client and everything attached to it in Keycloak:
- Client roles
- Protocol mappers
- Authorization settings
- Service account (if any)

## Several Files for One Realm

When several files target the same realm, the declared types and resources are the union of all the files. A resource declared in one file is not deleted because another file of the same realm does not mention it.

## Best Practices

### Run a Dry Run First

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=realm.yaml \
  --import.dry-run=true
```

The report lists the creations, updates and deletions the import would make. See [Dry Run](../advanced/dry-run.md).

### Declare Only the Types You Manage

Leave out the keys of the types managed elsewhere (by hand, by another tool): keycloak-config-cli does not touch them.

### Protect What Is Managed Outside the Files

When a type is declared but some of its resources are managed elsewhere, protect them with `protectedResources` instead of leaving the type out.

### Review Before Declaring a New Type

Before adding a key for a type that already has resources in Keycloak:

1. Export the current configuration from Keycloak
2. Compare it with your configuration files
3. Run a dry run and check the deletions it reports
4. Add the missing resources to the files, or protect them

## Handling External Changes

Once a type is declared:

- Resources of that type created manually in the Keycloak Admin UI are deleted on the next run, unless protected
- Changes made by other tools to declared resources are overwritten
- The configuration files are the only source of truth

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

### Scenario 2: Remove a Team

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

Result: the `legacy` group is deleted, unless a `protectedResources` rule matches it.

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

Result:
- Old `team-a` and `team-b` at root level are deleted
- New `department-1/team-a` and `department-1/team-b` are created

> **Note**: This is a delete + create, not a move. Group IDs will change.

### Scenario 4: Stop Managing a Type

Remove the `groups` key from all the files of the realm. The groups stay in Keycloak as they are, and later runs no longer touch them.
