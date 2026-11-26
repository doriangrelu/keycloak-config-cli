# Configuration Properties

This document describes all available configuration properties for Keycloak Config CLI.

## Keycloak Connection Properties

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `keycloak.url` | `KEYCLOAK_URL` | - | Keycloak server URL, including the web context |
| `keycloak.user` | `KEYCLOAK_USER` | `admin` | Admin username |
| `keycloak.password` | `KEYCLOAK_PASSWORD` | - | Admin password |
| `keycloak.client-id` | `KEYCLOAK_CLIENTID` | `admin-cli` | Client ID for authentication |
| `keycloak.client-secret` | `KEYCLOAK_CLIENTSECRET` | - | Client secret (for confidential clients) |
| `keycloak.grant-type` | `KEYCLOAK_GRANTTYPE` | `password` | Grant type for authentication: `password` or `client_credentials` |
| `keycloak.login-realm` | `KEYCLOAK_LOGINREALM` | `master` | Realm for admin authentication |
| `keycloak.ssl-verify` | `KEYCLOAK_SSLVERIFY` | `true` | Verify SSL certificates |
| `keycloak.http-proxy` | `KEYCLOAK_HTTPPROXY` | - | HTTP proxy to reach Keycloak, `scheme://hostname:port` |
| `keycloak.connect-timeout` | `KEYCLOAK_CONNECTTIMEOUT` | `10s` | Connection timeout |
| `keycloak.read-timeout` | `KEYCLOAK_READTIMEOUT` | `10s` | Read timeout |
| `keycloak.availability-check.enabled` | `KEYCLOAK_AVAILABILITYCHECK_ENABLED` | `false` | Wait until Keycloak is available before importing |
| `keycloak.availability-check.timeout` | `KEYCLOAK_AVAILABILITYCHECK_TIMEOUT` | `120s` | How long to wait for Keycloak |
| `keycloak.availability-check.retry-delay` | `KEYCLOAK_AVAILABILITYCHECK_RETRYDELAY` | `2s` | Delay between two availability checks |
| `keycloak.version` | `KEYCLOAK_VERSION` | version keycloak-config-cli was built against | Keycloak version assumed when `keycloak.skip-server-info=true` |
| `keycloak.skip-server-info` | `KEYCLOAK_SKIPSERVERINFO` | `false` | Do not fetch the server info, see [Skip Server Info](../reference/skip-server-info.md) |

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
| `import.files.locations` | `IMPORT_FILES_LOCATIONS` | - | Comma-separated list of files, Ant-style patterns or URLs, see [Import File Patterns](../reference/import-patterns.md) |
| `import.files.excludes` | `IMPORT_FILES_EXCLUDES` | - | Comma-separated Ant-style patterns of files to skip |
| `import.files.include-hidden-files` | `IMPORT_FILES_INCLUDEHIDDENFILES` | `false` | Include hidden files in import |
| `import.validate` | `IMPORT_VALIDATE` | `true` | Validate the import files before importing (for example, a client with authorization settings must be confidential) |
| `import.mustache.enabled` | `IMPORT_MUSTACHE_ENABLED` | `false` | Render the import files as Mustache templates, see [Mustache Templating](#mustache-templating) |
| `import.mustache.variables.*` | `IMPORT_MUSTACHE_VARIABLES_*` | - | Variables of the Mustache templates |
| `import.dry-run` | `IMPORT_DRYRUN` | `false` | Plan the changes without applying them, see [Dry Run](../advanced/dry-run.md) |
| `import.dry-run-format` | `IMPORT_DRYRUNFORMAT` | `text` | Format of the dry run report: `text` or `json` |
| `import.dry-run-report-file` | `IMPORT_DRYRUNREPORTFILE` | - | File the dry run report is also written to |
| `import.behaviors.sync-user-federation` | `IMPORT_BEHAVIORS_SYNCUSERFEDERATION` | `false` | Synchronize the users of the user storages the files declare after the import (not in a dry run) |
| `import.behaviors.remove-default-role-from-user` | `IMPORT_BEHAVIORS_REMOVEDEFAULTROLEFROMUSER` | `false` | Allow removing the `default-roles-<realm>` role from a user that does not declare it |
| `import.behaviors.skip-attributes-for-federated-user` | `IMPORT_BEHAVIORS_SKIPATTRIBUTESFORFEDERATEDUSER` | `false` | Do not send the attributes of federated users, to avoid read-only conflicts |

Unknown `import.*` and `keycloak.*` properties are rejected: a configuration that still sets an option removed in 7.0.0 (`import.managed.*`, `import.remote-state.*`, `import.cache.*`, `import.parallel`, `import.var-substitution.*`, `import.behaviors.checksum-*`) fails at startup with exit code `2`. See [Migrating to 7.0.0](migration-7.md).

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
| `run.operation` | `RUN_OPERATION` | `IMPORT` | Operation mode: `IMPORT`, `NORMALIZE` or `SHELL` (the offline [shell](../shell/README.md)) |

The `shell.*` properties (`shell.root`, `shell.realm`, `shell.command`, `shell.script`, `shell.rules`) are described in [Interactive Shell](../shell/README.md).

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
| `normalization.files.excludes` | `NORMALIZATION_FILES_EXCLUDES` | - | Ant-style patterns of exports to skip |
| `normalization.files.include-hidden-files` | `NORMALIZATION_FILES_INCLUDEHIDDENFILES` | `false` | Include hidden files |
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

Standard Spring Boot logging configuration, with log groups for the parts of keycloak-config-cli:

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `logging.level.root` | `LOGGING_LEVEL_ROOT` | `info` | Root log level |
| `logging.level.keycloak-config-cli` | `LOGGING_LEVEL_KEYCLOAKCONFIGCLI` | value of `logging.level.root` | Log level of the import and of the command line |
| `logging.level.http` | `LOGGING_LEVEL_HTTP` | value of `logging.level.root` | Log level of the HTTP requests between keycloak-config-cli and Keycloak |
| `logging.level.realm-config` | `LOGGING_LEVEL_REALMCONFIG` | value of `logging.level.root` | At `trace`, the realm configuration is logged, **sensitive values included** |

```yaml
logging:
  level:
    root: INFO
    keycloak-config-cli: DEBUG
```

Logs are written as JSON with the `json-log` profile: `SPRING_PROFILES_ACTIVE=json-log`.

## Spring Boot Properties

| Property | Environment Variable | Description |
|----------|---------------------|-------------|
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | Comma-separated Spring profiles, for example `json-log` |
| `spring.config.location` | `SPRING_CONFIG_LOCATION` | Configuration file(s) to read the properties from |
| `spring.config.import` | `SPRING_CONFIG_IMPORT` | Additional configuration, for example secrets read from files (below) |

Environment variable names follow Spring Boot [relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables): upper case, dots replaced by underscores, hyphens removed (`keycloak.ssl-verify` → `KEYCLOAK_SSLVERIFY`).

### Secrets Read from Files

With `SPRING_CONFIG_IMPORT=configtree:/run/secrets/`, each file of the folder sets the property of the same name: the file `/run/secrets/keycloak.password` sets `keycloak.password`. This matches Docker and Kubernetes secrets mounted as files. See [configuration trees](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.configtree) in the Spring Boot documentation.

## Mustache Templating

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `import.mustache.enabled` | `IMPORT_MUSTACHE_ENABLED` | `false` | Render the import files as Mustache templates |
| `import.mustache.variables.*` | `IMPORT_MUSTACHE_VARIABLES_*` | - | Variables of the templates: `--import.mustache.variables.host=sso.example.com` sets `{{host}}` |

`{{name}}` is replaced by the value and `{{name:default}}` falls back to a default. A variable that is neither defined nor given a default fails the import with exit code `2`. Mustache is the only template engine: the former `$(...)` substitution (`import.var-substitution.*`) has been removed. See [Templating with Mustache](../advanced/templating.md) for escaping, numbers and booleans, environment variables and the migration from `$(...)`.

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
