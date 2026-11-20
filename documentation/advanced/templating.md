# Templating with Mustache

The import files can be rendered as [Mustache](https://mustache.github.io/) templates before they are parsed. This lets the same files be imported in every environment: the values that change (host names, redirect URIs, secrets) are given as variables when keycloak-config-cli runs.

Mustache is the only template engine of keycloak-config-cli. The former `$(...)` variable substitution has been removed: see [Migrating from `$(...)`](#migrating-from--variable-substitution).

## Enabling templating

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `import.mustache.enabled` | `IMPORT_MUSTACHE_ENABLED` | `false` | Render the import files as Mustache templates |
| `import.mustache.variables.<name>` | `IMPORT_MUSTACHE_VARIABLES_<NAME>` | - | Value of the variable `{{name}}` |

Templating is **disabled by default**: existing files may contain `{{`, for example in message bundles or themes, and must not be interpreted unless you ask for it.

## Defining variables

Variables are Spring properties under `import.mustache.variables`. They can be given in three ways.

As command line arguments:

```bash
java -jar keycloak-config-cli.jar \
  --import.mustache.enabled=true \
  --import.mustache.variables.host=sso.example.com \
  --import.mustache.variables.realmName=my-realm \
  --import.files.locations=/config/realm.yaml
```

As environment variables (Spring relaxed binding):

```bash
export IMPORT_MUSTACHE_ENABLED=true
export IMPORT_MUSTACHE_VARIABLES_HOST=sso.example.com
```

Environment variable names are upper-case, so the variable is bound lower-cased: `IMPORT_MUSTACHE_VARIABLES_HOST` defines `{{host}}`. Use single-word, lower-case names with environment variables; when a name contains capitals, dashes or underscores (`realmName`, `realm-name`), give it as a command line argument or in a configuration file.

In a Spring configuration file (`application.yaml`):

```yaml
import:
  mustache:
    enabled: true
    variables:
      host: sso.example.com
      realmName: my-realm
```

A Spring property can itself reference an environment variable, which keeps secrets out of the command line history and lets you choose the variable name:

```bash
java -jar keycloak-config-cli.jar \
  --import.mustache.enabled=true \
  --import.mustache.variables.secret='${CLIENT_SECRET}' \
  --import.files.locations=/config/realm.json
```

> **Note**: In a shell, quote `${CLIENT_SECRET}` with single quotes to let Spring resolve it, or leave it unquoted to let the shell expand it. Both give the same value.

The variables are resolved where keycloak-config-cli runs, not in Keycloak.

## Using variables

### Values and default values

`{{name}}` is replaced by the value of the variable. `{{name:default}}` uses `default` when the variable is not defined:

```yaml
realm: "{{realmName:my-realm}}"
attributes:
  frontendUrl: "{{url:https://sso.example.com:8443}}"
```

The default value is everything after the first colon: it may contain colons, dots and slashes, but not `}}`.

### Undefined variables

A variable that is neither defined nor given a default **fails the import**, with exit code `2` (invalid configuration, see [Exit Codes](../reference/exit-codes.md)). The message names the variable, the file and the line:

```
Undefined variable 'secret' in file 'realm.json' at line 12: set import.mustache.variables.secret, or give it a default value with {{secret:default}}
```

An undefined variable is never replaced by an empty value: an empty secret or redirect URI is worse than an error.

### Escaping

Values are escaped as the content of a double-quoted string, which is the same in JSON and in YAML:

| Character in the value | Written as |
|------------------------|------------|
| `"` | `\"` |
| `\` | `\\` |
| Line break | `\n` |
| Other control characters | `\uXXXX` |

HTML is **not** escaped: `&` and `<` are kept as they are.

So a value containing quotes, backslashes or line breaks neither breaks the file nor changes its meaning, provided the placeholder is inside a double-quoted string:

- in JSON, always put the placeholder inside quotes: `"secret": "{{secret}}"`;
- in YAML, use a double-quoted scalar when the value may contain quotes, backslashes or line breaks: `secret: "{{secret}}"`.

### Numbers and booleans

Digits and `true`/`false` are not changed by escaping, so a placeholder can stand for a number or a boolean outside quotes:

```json
{
  "notBefore": {{notBefore:600}},
  "verifyEmail": {{verifyEmail:false}}
}
```

### Unescaped values

`{{{name}}}` (triple mustache) inserts the value as it is, without escaping. Use it to inject a fragment of JSON or YAML, for example a whole object:

```json
{
  "browserSecurityHeaders": {{{headers}}}
}
```

```bash
--import.mustache.variables.headers='{"xFrameOptions": "DENY"}'
```

The value must then be valid in the file where it is inserted.

### Keycloak placeholders

Keycloak's own placeholders, such as `${role_uma_authorization}` in a description or a display name, are left untouched.

## Full example

`realm.json`:

```json
{
  "realm": "{{realmName:my-realm}}",
  "enabled": true,
  "notBefore": {{notBefore:0}},
  "clients": [
    {
      "clientId": "my-app",
      "secret": "{{clientSecret}}",
      "redirectUris": ["https://{{host}}/*"]
    }
  ]
}
```

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.mustache.enabled=true \
  --import.mustache.variables.host=app.example.com \
  --import.mustache.variables.clientSecret='${CLIENT_SECRET}' \
  --import.files.locations=realm.json
```

## Migrating from `$(...)` variable substitution

The `$(...)` substitution and its properties (`import.var-substitution.enabled`, `.nested`, `.undefined-is-error`, `.prefix`, `.suffix`, and the `IMPORT_VARSUBSTITUTION_*` environment variables) have been removed. A configuration that still sets `import.var-substitution.*` fails at startup with an unknown property error.

| Before | After |
|--------|-------|
| `import.var-substitution.enabled=true` | `import.mustache.enabled=true` |
| `$(env:HOST)` | `{{host}}`, with `--import.mustache.variables.host='${HOST}'` or the environment variable `IMPORT_MUSTACHE_VARIABLES_HOST` |
| `$(sys:my.property)` | `{{myProperty}}`, with `--import.mustache.variables.myProperty=...` |
| `$(file:UTF-8:/run/secrets/secret)` | No direct equivalent, see below |
| `$(property.name)` (Spring property) | `{{name}}`, with `--import.mustache.variables.name='${property.name}'` |
| `$(VAR:-default)` | `{{var:default}}` |

To read a value from a file, either read it in the shell:

```bash
--import.mustache.variables.secret="$(cat /run/secrets/secret)"
```

or use Spring's [configtree](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.configtree), which exposes each file of a directory as a property named after the file:

```bash
java -jar keycloak-config-cli.jar \
  --spring.config.import=configtree:/run/secrets/ \
  --import.mustache.enabled=true \
  --import.mustache.variables.secret='${secret}' \
  --import.files.locations=realm.json
```

Other differences to check while migrating:

- An undefined variable without default is always an error; there is no option to replace it with an empty value.
- Placeholders in JSON must be inside double quotes, unless the value is a number or a boolean, or you use `{{{name}}}`.
- Keycloak placeholders such as `${role_uma_authorization}` no longer need to be escaped.
