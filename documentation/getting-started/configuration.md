# Configuration Properties

This document describes all available configuration properties for Keycloak Config CLI.

## Keycloak Connection Properties

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `keycloak.url` | `KEYCLOAK_URL` | - | Keycloak server URL |
| `keycloak.user` | `KEYCLOAK_USER` | - | Admin username |
| `keycloak.password` | `KEYCLOAK_PASSWORD` | - | Admin password |
| `keycloak.client-id` | `KEYCLOAK_CLIENTID` | `admin-cli` | Client ID for authentication |
| `keycloak.client-secret` | `KEYCLOAK_CLIENTSECRET` | - | Client secret (for confidential clients) |
| `keycloak.login-realm` | `KEYCLOAK_LOGINREALM` | `master` | Realm for admin authentication |
| `keycloak.ssl-verify` | `KEYCLOAK_SSLVERIFY` | `true` | Verify SSL certificates |

### Example

```yaml
keycloak:
  url: https://keycloak.example.com
  user: admin
  password: ${KEYCLOAK_ADMIN_PASSWORD}
  login-realm: master
  ssl-verify: true
```

## Import Properties

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `import.files.locations` | `IMPORT_FILES_LOCATIONS` | - | Comma-separated list of file paths or directories |
| `import.files.include-hidden-files` | `IMPORT_FILES_INCLUDE_HIDDEN_FILES` | `false` | Include hidden files in import |
| `import.var-substitution.enabled` | `IMPORT_VARSUBSTITUTION_ENABLED` | `true` | Enable variable substitution in files |
| `import.var-substitution.prefix` | `IMPORT_VARSUBSTITUTION_PREFIX` | `$(` | Variable prefix |
| `import.var-substitution.suffix` | `IMPORT_VARSUBSTITUTION_SUFFIX` | `)` | Variable suffix |
| `import.parallel` | `IMPORT_PARALLEL` | `false` | Enable parallel processing |
| `import.state` | `IMPORT_STATE` | `true` | Enable state management |
| `import.state-encryption-key` | `IMPORT_STATEENCRYPTIONKEY` | - | Encryption key for state |
| `import.force` | `IMPORT_FORCE` | `false` | Force import even if unchanged |

### Example

```yaml
import:
  files:
    locations:
      - /config/realms/*.yaml
      - /config/clients/*.json
    include-hidden-files: false
  var-substitution:
    enabled: true
    prefix: "${"
    suffix: "}"
  parallel: true
  state: true
  force: false
```

## Managed Resources Properties

These properties control how resources are managed during import. Possible values:
- `no-delete` (default): Create and update only, never delete
- `full`: Full lifecycle management, delete resources not in configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `import.managed.authentication-flow` | `IMPORT_MANAGED_AUTHENTICATIONFLOW` | `no-delete` | Manage authentication flows |
| `import.managed.client` | `IMPORT_MANAGED_CLIENT` | `no-delete` | Manage clients |
| `import.managed.client-scope` | `IMPORT_MANAGED_CLIENTSCOPE` | `no-delete` | Manage client scopes |
| `import.managed.client-scope-mapping` | `IMPORT_MANAGED_CLIENTSCOPEMAPPING` | `no-delete` | Manage client scope mappings |
| `import.managed.component` | `IMPORT_MANAGED_COMPONENT` | `no-delete` | Manage components |
| `import.managed.group` | `IMPORT_MANAGED_GROUP` | `no-delete` | Manage groups |
| `import.managed.identity-provider` | `IMPORT_MANAGED_IDENTITYPROVIDER` | `no-delete` | Manage identity providers |
| `import.managed.identity-provider-mapper` | `IMPORT_MANAGED_IDENTITYPROVIDERMAPPER` | `no-delete` | Manage IdP mappers |
| `import.managed.realm-role` | `IMPORT_MANAGED_REALMROLE` | `no-delete` | Manage realm roles |
| `import.managed.client-role` | `IMPORT_MANAGED_CLIENTROLE` | `no-delete` | Manage client roles |
| `import.managed.required-action` | `IMPORT_MANAGED_REQUIREDACTION` | `no-delete` | Manage required actions |
| `import.managed.scope-mapping` | `IMPORT_MANAGED_SCOPEMAPPING` | `no-delete` | Manage scope mappings |
| `import.managed.user` | `IMPORT_MANAGED_USER` | `no-delete` | Manage users |

### Example

```yaml
import:
  managed:
    group: full
    realm-role: full
    client-role: full
    client: no-delete  # Be careful with client deletion
    user: no-delete    # Never auto-delete users
```

## Run Properties

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `run.operation` | `RUN_OPERATION` | `IMPORT` | Operation mode: `IMPORT` or `NORMALIZE` |

### Example

```yaml
run:
  operation: IMPORT
```

## Normalization Properties

Used when `run.operation=NORMALIZE` for exporting realm configurations.

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `normalization.files.output-directory` | `NORMALIZATION_FILES_OUTPUTDIRECTORY` | - | Output directory for normalized files |
| `normalization.files.output-format` | `NORMALIZATION_FILES_OUTPUTFORMAT` | `YAML` | Output format: `YAML` or `JSON` |

### Example

```yaml
run:
  operation: NORMALIZE

normalization:
  files:
    output-directory: /output/realms
    output-format: YAML
```

## Logging Properties

Standard Spring Boot logging configuration:

```yaml
logging:
  level:
    root: INFO
    io.github.doriangrelu.keycloak.config: DEBUG
```

## Variable Substitution

You can use variables in your configuration files:

```yaml
realm: ${REALM_NAME}
clients:
  - clientId: ${CLIENT_ID:default-client}  # With default value
    secret: ${CLIENT_SECRET}
```

Run with environment variables:

```bash
REALM_NAME=production CLIENT_SECRET=secret123 \
  java -jar keycloak-config-cli.jar ...
```

## Command Line Examples

```bash
# Basic import
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=/config/realm.yaml

# Full management with parallel processing
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=/config/ \
  --import.parallel=true \
  --import.managed.group=full \
  --import.managed.role=full

# Using configuration file
java -jar keycloak-config-cli.jar \
  --spring.config.location=application.yaml
```
