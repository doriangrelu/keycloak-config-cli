# Import Modes

Keycloak Config CLI supports different import modes and strategies to handle various use cases.

## Operation Modes

### IMPORT Mode (Default)

Imports configuration from files into Keycloak:

```bash
java -jar keycloak-config-cli.jar \
  --run.operation=IMPORT \
  --import.files.locations=/config/
```

### NORMALIZE Mode

Exports the current Keycloak configuration to files:

```bash
java -jar keycloak-config-cli.jar \
  --run.operation=NORMALIZE \
  --normalization.files.output-directory=/output/ \
  --normalization.files.output-format=YAML
```

## Import Strategies

### Standard Import

The default behavior: compare and update.

```yaml
import:
  force: false  # Default
  state: true   # Default
```

1. Load configuration files
2. Compare with current Keycloak state
3. Apply only necessary changes

### Force Import

Bypass state checking and apply all configurations:

```yaml
import:
  force: true
```

Use cases:
- After manual changes in Keycloak
- When state has become inconsistent
- During initial migration

### Stateless Import

Disable state management entirely:

```yaml
import:
  state: false
```

Every run compares configuration with live Keycloak state (no caching).

## Parallel vs Sequential Processing

### Sequential Processing (Default)

Resources are processed one at a time:

```yaml
import:
  parallel: false
```

Pros:
- Simpler error handling
- Easier to debug
- Deterministic ordering

Cons:
- Slower for large configurations

### Parallel Processing

Resources are processed concurrently:

```yaml
import:
  parallel: true
```

Pros:
- Significantly faster for large imports
- Better utilization of Keycloak server

Cons:
- More complex error handling
- May require retry logic for newly created resources

## File Processing

### Single File Import

Import a single configuration file:

```bash
--import.files.locations=realm.yaml
```

### Multiple Files Import

Import multiple specific files:

```bash
--import.files.locations=realm.yaml,clients.yaml,groups.yaml
```

### Directory Import

Import all files from a directory:

```bash
--import.files.locations=/config/
```

Files are processed in alphabetical order by filename.

### Glob Pattern Import

Use patterns to select files:

```bash
--import.files.locations=/config/*.yaml
--import.files.locations=/config/**/*.yaml  # Recursive
```

## File Formats

### YAML (Recommended)

```yaml
realm: my-realm
enabled: true
groups:
  - name: admins
    realmRoles:
      - admin
```

### JSON

```json
{
  "realm": "my-realm",
  "enabled": true,
  "groups": [
    {
      "name": "admins",
      "realmRoles": ["admin"]
    }
  ]
}
```

## Variable Substitution

Enable dynamic configuration values:

```yaml
import:
  var-substitution:
    enabled: true
    prefix: "${"
    suffix: "}"
```

Usage in configuration:

```yaml
realm: ${REALM_NAME}
clients:
  - clientId: ${CLIENT_ID}
    secret: ${CLIENT_SECRET}
    redirectUris:
      - ${APP_URL}/*
```

### Default Values

Provide fallback values:

```yaml
realm: ${REALM_NAME:default-realm}
clients:
  - clientId: ${CLIENT_ID:my-app}
```

### Environment Variables

```bash
export REALM_NAME=production
export CLIENT_ID=prod-app
export CLIENT_SECRET=secret123

java -jar keycloak-config-cli.jar ...
```

## Multi-Realm Import

### Single File with Multiple Realms

Not directly supported. Use separate files per realm.

### Directory Structure

Organize files by realm:

```
config/
├── realm-a/
│   ├── 00-realm.yaml
│   ├── 01-roles.yaml
│   ├── 02-groups.yaml
│   └── 03-clients.yaml
├── realm-b/
│   ├── 00-realm.yaml
│   └── 01-clients.yaml
```

Import with:

```bash
--import.files.locations=/config/realm-a/,/config/realm-b/
```

### File Ordering

Files are processed alphabetically. Use numeric prefixes to control order:

```
00-realm.yaml      # Realm settings first
01-roles.yaml      # Roles before groups
02-groups.yaml     # Groups before users
03-clients.yaml    # Clients
04-users.yaml      # Users last
```

## Handling Dependencies

Some resources depend on others:

| Resource | Dependencies |
|----------|--------------|
| Groups | Realm roles, Client roles |
| Users | Groups, Roles |
| Client Scope Mappings | Clients, Client Scopes |
| Role Composites | Other roles |

### Recommended Import Order

1. Realm settings
2. Roles (realm and client)
3. Groups
4. Client Scopes
5. Clients
6. Users
7. Identity Providers

### Using Multiple Files

Split configuration for complex setups:

```yaml
# 00-realm.yaml
realm: my-realm
enabled: true
```

```yaml
# 01-roles.yaml
realm: my-realm
roles:
  realm:
    - name: admin
    - name: user
```

```yaml
# 02-groups.yaml
realm: my-realm
groups:
  - name: administrators
    realmRoles:
      - admin
```

## Error Handling

### Continue on Error

By default, the CLI stops on first error. For bulk imports where partial success is acceptable, consider using parallel mode which has more resilient error handling.

### Retry Logic

The CLI includes built-in retry logic for:
- Newly created resources not immediately available
- Temporary network issues
- Rate limiting responses

### Validation

The CLI validates configuration before applying:
- Required fields
- Reference integrity (roles exist before assignment)
- Format validation

## Best Practices

1. **Use Version Control**: Store configuration files in Git
2. **Environment-Specific Files**: Use variable substitution for environment differences
3. **Logical File Organization**: Group related resources in same file or directory
4. **Test in Development**: Always test imports in dev before production
5. **Backup Before Full Mode**: Export current config before using `managed: full`
