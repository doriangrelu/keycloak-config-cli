# Migrating to 7.0.0

Version 7.0.0 makes the import files the only source of truth, like `kubectl apply` with manifests. It removes the options that made an import depend on something else than the files (managed modes, remote state, checksum cache), the parallel import and the `$(...)` substitution. This page lists what to change and how to upgrade safely.

## In Short

1. Remove the options that no longer exist from your configuration: the tool refuses to start with them (exit code `2`).
2. Replace `$(...)` placeholders with Mustache variables.
3. Protect what must survive the clean phase with `protectedResources`.
4. Run a [dry run](../advanced/dry-run.md) and read the planned deletions before the first real import with 7.0.0.

## Removed Options

Unknown `import.*` properties are rejected at startup, so a configuration that still sets a removed option fails with exit code `2` and names the property.

| Removed option | Replacement |
|----------------|-------------|
| `import.managed.<type>` (`full` / `no-delete`) | None: a single [cleanup rule](#the-cleanup-rule) applies to every type. Keep resources with `protectedResources`, or leave their type out of the files |
| `import.remote-state.enabled`, `import.remote-state.encryption-key`, `import.remote-state.encryption-salt` | None: deletions only follow the files. See [what the remote state used to spare](#what-the-remote-state-used-to-spare) |
| `import.cache.enabled`, `import.cache.key` | None: every file is compared with Keycloak on every run |
| `import.behaviors.checksum-with-cache-key`, `import.behaviors.checksum-changed` | None, see above |
| `import.parallel` | None: changes are applied one at a time, in the order of their dependencies |
| `import.var-substitution.enabled`, `.nested`, `.undefined-is-error`, `.prefix`, `.suffix` | Mustache templating: `import.mustache.enabled` and `import.mustache.variables.*`. See [below](#from-substitution-to-mustache) |

The environment variables of these options (`IMPORT_MANAGED_*`, `IMPORT_REMOTESTATE_*`, `IMPORT_CACHE_*`, `IMPORT_PARALLEL`, `IMPORT_VARSUBSTITUTION_*`...) are rejected the same way.

Exit code `5` (checksum changed) no longer exists. The other [exit codes](../reference/exit-codes.md) are unchanged.

## The Cleanup Rule

For each realm, a resource type that at least one import file declares (its key is present, even with an empty list) is reconciled: what the files declare is created or updated, and every resource of that type that no file of the realm declares is deleted. A type that no file declares is not touched.

Inside a declared resource, what it omits is removed too: the role mappings of a group, the roles of a scope mapping, the texts of a message bundle locale.

Three guarantees hold whatever the files say, with no option to change them:

- **Users** are never deleted.
- **Technical resources** created by Keycloak are never deleted: built-in clients, default roles, built-in client scopes, flows and required actions, key providers... See [Protected Resources](../advanced/protected-resources.md#technical-resources).
- **Protected resources**, matched by a `protectedResources` rule of the files, are never deleted.

With 6.x, the default managed mode of some types was `no-delete`, and the remote state spared the resources keycloak-config-cli had not created. Resources created by hand or by another tool, of a type your files declare, are now deleted unless a rule protects them:

```yaml
protectedResources:
  - rule: PATTERN
    matcher: "ext-.*"      # resources managed by another tool
  - rule: EQUALS
    matcher: legacy-app
    type: CLIENT           # optional: only the client named legacy-app
```

See [Protected Resources](../advanced/protected-resources.md) for the rules, their optional `type` and the names they compare.

### What the Remote State Used to Spare

Two cases are now covered by fixed rules instead of the remote state:

- **Sub-components**: a component that declares at least one sub-component has exactly those; one that declares none keeps its own, such as the mappers Keycloak creates with an LDAP user storage.
- **Authorization resources created at runtime**: when a client allows remote resource management (`allowRemoteResourceManagement`, the Keycloak default), the authorization resources the files do not declare are kept, except the `Default Resource`, and so are the scopes they use. Set `allowRemoteResourceManagement: false` to make the files the only source of truth for the resources of that client.

### Leftovers in the Realms

Former versions stored their remote state and checksums in realm attributes named `io.github.doriangrelu.keycloak.config.*`. The next import removes them; there is nothing to do.

## Every Run Compares Every File

There is no cache any more: every import compares every file with Keycloak. A change made by hand in Keycloak, on something the files declare, is undone by the next import, and a dry run always shows it.

All the files are read and parsed before anything is changed: an unreadable file, an invalid template or an invalid protection rule fails the import with exit code `2`, and Keycloak is left as it was.

## From Substitution to Mustache

The `$(...)` substitution and its lookups (`env:`, `sys:`, `file:`...) are removed; Mustache is the only template engine. Enable it, give the values as variables, and write `{{name}}` in the files:

| 6.x | 7.0.0 |
|-----|-------|
| `--import.var-substitution.enabled=true` | `--import.mustache.enabled=true` |
| `"$(env:CLIENT_SECRET)"` | `"{{clientSecret}}"` with `--import.mustache.variables.clientSecret=${CLIENT_SECRET}` |
| `$(sys:realm.name)` | `{{realmName}}` with `--import.mustache.variables.realmName=...` |
| `$(env:HOST:-localhost)` | `{{host:localhost}}` |
| `$(file:UTF-8:/run/secrets/secret)` | `--import.mustache.variables.secret="$(cat /run/secrets/secret)"`, or Spring's `configtree:` |

Three behaviors differ from 6.x:

- A variable that is neither defined nor given a default fails the import (exit code `2`), with the name of the variable, the file and the line. It no longer renders an empty value.
- Values are escaped as the content of a double-quoted JSON or YAML string: keep the placeholder between double quotes when the value may hold quotes, backslashes or line breaks. HTML is no longer escaped (`&` and `<` stay as they are).
- `{{{name}}}` inserts a value as it is, for a number, a boolean or a whole JSON object.

See [Templating with Mustache](../advanced/templating.md).

## Other Changes

- **Duplicated definitions**: when several files of a realm define the same resource (role, group, client...) with different content, the import is rejected (exit code `2`) instead of the last file winning.
- **Error messages**: the messages of the errors are unchanged; failures caused by the configuration exit with code `2`.
- **Excludes on Windows**: `import.files.excludes` and `normalization.files.excludes` now apply on Windows.
- **Logging groups**: the `keycloak-config-cli` and `kcc` logging groups now cover the import use case, the command line and the Keycloak client.
- **Normalization**: the normalized files are smaller and may differ from those of 6.x. See [Normalization](../advanced/normalization.md).

## Upgrade Checklist

1. Remove the [removed options](#removed-options) from your configuration files, command lines and environment variables.
2. Convert `$(...)` placeholders to Mustache, and enable `import.mustache.enabled` if you use variables.
3. For each type your files declare, list the resources of Keycloak the files do not declare. Protect those that must stay, or declare them.
4. Run a dry run against each environment, starting with the closest to production, and read the deletions:

   ```bash
   java -jar keycloak-config-cli.jar \
     --import.files.locations='/config/*' \
     --import.dry-run=true
   ```

   Exit code `10` means changes are pending; `0` means the files match Keycloak.
5. Apply the import once the planned changes are the expected ones.
