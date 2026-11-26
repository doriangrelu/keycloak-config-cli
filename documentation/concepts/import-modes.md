# Operations and Import Process

keycloak-config-cli has three operations, chosen with `run.operation`. An import has no mode or strategy to choose: every run follows the same process.

## Operation Modes

### IMPORT Mode (Default)

Imports configuration from files into Keycloak:

```bash
java -jar keycloak-config-cli.jar \
  --run.operation=IMPORT \
  --import.files.locations='/config/*'
```

### NORMALIZE Mode

Turns realm exports of Keycloak into minimal import files (see [Normalization](../advanced/normalization.md)):

```bash
java -jar keycloak-config-cli.jar \
  --run.operation=NORMALIZE \
  --normalization.files.input-locations=/exports/*.json \
  --normalization.files.output-directory=/output/ \
  --normalization.output-format=YAML
```

### SHELL Mode

Starts the guided, offline editor of realm files (see [Interactive Shell](../shell/README.md)):

```bash
java -jar keycloak-config-cli.jar --run.operation=SHELL --shell.root=./realms
```

## How an Import Works

An import has no strategy to choose: every run does the same thing, like `kubectl apply`.

1. **Read**: all the import files are read, rendered (when [Mustache templating](../advanced/templating.md) is enabled) and parsed before anything is changed in Keycloak. An invalid file (unreadable, unparsable, undefined template variable, invalid `protectedResources` regular expression) fails the import with exit code `2`, and Keycloak is left unchanged.
2. **Merge**: the files that target the same realm are merged into one declaration of the realm.
3. **Compare**: the declaration is compared with the current content of Keycloak. There is no checksum and no state stored between runs: every file is compared with Keycloak on every run.
4. **Apply**: what the files declare is created or updated, one change at a time, in the order of their dependencies.
5. **Clean**: for each resource type the files of the realm declare (key present, even as an empty list), the resources of that type that no file declares are deleted. Types no file declares are not touched. Users, technical resources created by Keycloak and [protected resources](../advanced/protected-resources.md) are never deleted. See [Managed Resources](managed-resources.md).

Because Keycloak is compared on every run, a change made by hand in Keycloak to a declared resource is undone by the next import. Run a [dry run](../advanced/dry-run.md) (`--import.dry-run=true`) to see the changes an import would make, deletions included, without applying them.

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

Import all files of a directory with a pattern (a directory name alone matches no file):

```bash
--import.files.locations='/config/*'
```

The files of a location are read in alphabetical order by filename.

### Glob Pattern Import

Use patterns to select files:

```bash
--import.files.locations=/config/*.yaml
--import.files.locations=/config/**/*.yaml  # Recursive
```

All the forms (URLs, excludes, hidden files) are in [Import File Patterns](../reference/import-patterns.md).

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

## Templating

Values that differ between environments are [Mustache variables](../advanced/templating.md): `{{name}}` or `{{name:default}}` in the files, `--import.mustache.enabled=true` and `--import.mustache.variables.<name>=...` on the command line.

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
--import.files.locations='/config/realm-a/*,/config/realm-b/*'
```

### File Ordering

The files of a realm are merged before anything is applied, so their order does not matter: keycloak-config-cli applies the changes in the order of their dependencies (roles before the groups and users that use them, client scopes before the clients that use them, and so on). Numeric prefixes can still help readers find their way:

```
00-realm.yaml
01-roles.yaml
02-groups.yaml
03-clients.yaml
```

A resource defined twice with different content in the files of a realm is rejected (exit code `2`).

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

A type declared in one file of the realm is cleaned over all the files of the realm: a group declared in another file of the same realm is not deleted.

## Error Handling

The import stops on the first error, and the exit code tells what kind of error it was (invalid configuration, Keycloak unavailable, Keycloak refused): see [Exit Codes](../reference/exit-codes.md).

Since every file is read and parsed first, an invalid file changes nothing in Keycloak. A reference to a missing resource (a role, client, group or client scope that neither the files nor Keycloak have) fails the import with exit code `2`.

With `keycloak.availability-check.enabled=true`, keycloak-config-cli waits for Keycloak to be available before importing (up to `keycloak.availability-check.timeout`).

## Best Practices

1. **Use Version Control**: Store configuration files in Git
2. **Environment-Specific Files**: Use [Mustache templating](../advanced/templating.md) for environment differences
3. **Logical File Organization**: Group related resources in same file or directory
4. **Test in Development**: Always test imports in dev before production
5. **Dry Run Before Cleanup**: Export the current config and run a [dry run](../advanced/dry-run.md) before declaring a new resource type, since missing resources of a declared type are deleted
