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
| `import.dry-run` | `IMPORT_DRYRUN` | `false` | Plan the changes without applying them, see [Dry Run](../advanced/dry-run.md) |
| `import.dry-run-format` | `IMPORT_DRYRUNFORMAT` | `text` | Format of the dry run report: `text` or `json` |
| `import.dry-run-report-file` | `IMPORT_DRYRUNREPORTFILE` | - | File the dry run report is also written to |

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
```

## Cleanup

There is no property to choose what gets deleted. For each realm, a resource type declared by at least one import file (key present, even with an empty list) is cleaned: the resources of that type that no file declares are deleted. Users, technical resources created by Keycloak and resources matched by `protectedResources` rules are never deleted.

The former per-type properties under `import` (`managed.group`, `managed.client`, ...) no longer exist; a configuration that still sets one fails at startup. See [Managed Resources](../concepts/managed-resources.md), and run a [dry run](../advanced/dry-run.md) before applying.

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
| `normalization.files.input-locations` | `NORMALIZATION_FILES_INPUTLOCATIONS` | - | Realm exports to normalize |
| `normalization.files.output-directory` | `NORMALIZATION_FILES_OUTPUTDIRECTORY` | - | Output directory for normalized files |
| `normalization.output-format` | `NORMALIZATION_OUTPUTFORMAT` | `YAML` | Output format: `YAML` or `JSON` |
| `normalization.fallback-version` | `NORMALIZATION_FALLBACKVERSION` | - | Reference realm used when there is none for the version of an export |

See [Normalization](../advanced/normalization.md).

### Example

```yaml
run:
  operation: NORMALIZE

normalization:
  files:
    input-locations: /exports/*.json
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

## Mustache Templating

Mustache templating allows you to use `{{variable}}` placeholders in your import files, resolved at import time from CLI-provided variables.

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `import.mustache.enabled` | `IMPORT_MUSTACHE_ENABLED` | `false` | Enable Mustache template processing |
| `import.mustache.variables.*` | `IMPORT_MUSTACHE_VARIABLES_*` | - | Key-value pairs used as template context |

### Example

Pass variables via CLI arguments:

```bash
java -jar keycloak-config-cli.jar \
  --import.mustache.enabled=true \
  --import.mustache.variables.realm-name=my-realm \
  --import.mustache.variables.client-id=my-app \
  --import.mustache.variables.admin-email=admin@example.com \
  --import.files.locations=/config/realm.yaml
```

Or via environment variables:

```bash
export IMPORT_MUSTACHE_ENABLED=true
export IMPORT_MUSTACHE_VARIABLES_REALM_NAME=my-realm
export IMPORT_MUSTACHE_VARIABLES_CLIENT_ID=my-app
```

Or in `application.yaml`:

```yaml
import:
  mustache:
    enabled: true
    variables:
      realm-name: my-realm
      client-id: my-app
      admin-email: admin@example.com
```

Then use the variables in your import files:

```yaml
realm: "{{realm-name}}"
clients:
  - clientId: "{{client-id}}"
    attributes:
      admin.email: "{{admin-email}}"
```

### Default Values

Undefined variables are resolved to an empty string by default. You can also specify an explicit default value using the `{{key:default_value}}` syntax:

```yaml
realm: "{{realm-name:my-default-realm}}"
clients:
  - clientId: "{{client-id:default-client}}"
    protocol: "{{protocol:openid-connect}}"
```

In this example:
- If `realm-name` is not provided, it resolves to `my-default-realm`
- If `client-id` is not provided, it resolves to `default-client`
- If `protocol` is not provided, it resolves to `openid-connect`
- Any variable without a `:default` suffix resolves to an empty string if undefined

> **Note**: Mustache templating and variable substitution (`import.var-substitution`) can be used together. Variable substitution runs first, then Mustache templating is applied.

## Command Line Examples

```bash
# Basic import
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=/config/realm.yaml

# Using configuration file
java -jar keycloak-config-cli.jar \
  --spring.config.location=application.yaml
```
