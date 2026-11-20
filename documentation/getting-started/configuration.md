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
| `import.mustache.enabled` | `IMPORT_MUSTACHE_ENABLED` | `false` | Render the import files as Mustache templates, see [Mustache Templating](#mustache-templating) |
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
  mustache:
    enabled: true
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

## Mustache Templating

Mustache templating allows you to use `{{variable}}` placeholders in your import files, resolved at import time from the variables given to keycloak-config-cli. It is the only template engine: the former `$(...)` variable substitution (`import.var-substitution.*`) has been removed, and a configuration that still sets it fails at startup.

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `import.mustache.enabled` | `IMPORT_MUSTACHE_ENABLED` | `false` | Enable Mustache template processing |
| `import.mustache.variables.*` | `IMPORT_MUSTACHE_VARIABLES_*` | - | Key-value pairs used as template context |

### Example

Pass variables via CLI arguments:

```bash
java -jar keycloak-config-cli.jar \
  --import.mustache.enabled=true \
  --import.mustache.variables.realmName=my-realm \
  --import.mustache.variables.clientId=my-app \
  --import.mustache.variables.clientSecret='${CLIENT_SECRET}' \
  --import.files.locations=/config/realm.yaml
```

Or via environment variables (the name is bound lower-cased, so prefer single-word names):

```bash
export IMPORT_MUSTACHE_ENABLED=true
export IMPORT_MUSTACHE_VARIABLES_HOST=sso.example.com
```

Or in `application.yaml`:

```yaml
import:
  mustache:
    enabled: true
    variables:
      realmName: my-realm
      clientId: my-app
      clientSecret: ${CLIENT_SECRET}
```

Then use the variables in your import files:

```yaml
realm: "{{realmName}}"
clients:
  - clientId: "{{clientId}}"
    secret: "{{clientSecret}}"
    rootUrl: "https://{{host}}"
```

### Default Values

Use `{{key:default_value}}` to give a default value, used when the variable is not defined:

```yaml
realm: "{{realmName:my-default-realm}}"
clients:
  - clientId: "{{clientId:default-client}}"
    protocol: "{{protocol:openid-connect}}"
```

A variable that is neither defined nor given a default **fails the import** with exit code `2` (invalid configuration); the error names the variable, the file and the line.

Values are escaped as the content of a double-quoted string, so keep placeholders inside double quotes. See [Templating with Mustache](../advanced/templating.md) for escaping, numbers and booleans, unescaped values (`{{{name}}}`) and the migration from `$(...)`.

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
