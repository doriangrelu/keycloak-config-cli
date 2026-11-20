# Normalization

Normalization turns a full realm export of Keycloak into a minimal import file: it keeps only what was configured, and leaves out everything Keycloak creates by itself. Use it to adopt keycloak-config-cli on an existing realm.

## How it works

1. Export the realm from Keycloak, as a file: `kc.sh export --realm my-realm --file my-realm.json`, or the partial export of the admin console.
2. keycloak-config-cli reads the export and compares it with the **reference realm** of its Keycloak version: a realm as Keycloak creates it, that nobody changed. The reference realms are shipped with keycloak-config-cli.
3. It writes one import file per realm, holding what differs from the reference.

Normalization works on files only: it never connects to Keycloak.

## Running it

```bash
java -jar keycloak-config-cli.jar \
  --run.operation=NORMALIZE \
  --normalization.files.input-locations=./exports/*.json \
  --normalization.files.output-directory=./config \
  --normalization.output-format=YAML
```

## Properties

| Property | Environment variable | Default | Description |
|---|---|---|---|
| `run.operation` | `RUN_OPERATION` | `IMPORT` | Set to `NORMALIZE` |
| `normalization.files.input-locations` | `NORMALIZATION_FILES_INPUTLOCATIONS` | - | Realm exports to normalize, with the same patterns as the import files |
| `normalization.files.excludes` | `NORMALIZATION_FILES_EXCLUDES` | - | Files to leave out |
| `normalization.files.include-hidden-files` | `NORMALIZATION_FILES_INCLUDEHIDDENFILES` | `false` | Whether hidden files are read |
| `normalization.files.output-directory` | `NORMALIZATION_FILES_OUTPUTDIRECTORY` | - | Directory of the normalized files, created when missing |
| `normalization.output-format` | `NORMALIZATION_OUTPUTFORMAT` | `YAML` | `YAML` or `JSON` |
| `normalization.fallback-version` | `NORMALIZATION_FALLBACKVERSION` | - | Reference realm to use when there is none for the version of an export |

## What is kept

| Resource | Kept |
|---|---|
| Realm settings | Only the settings that differ from the reference, attributes compared one by one. The realm name and whether it is enabled are always kept |
| Flow bindings of the realm | Only the bindings that differ |
| Clients | New clients, and clients of the reference with a value that differs. A client keeps only its differing values, plus its client id, protocol, public flag, protocol mappers, flow bindings (by alias) and authorization settings. A new client is compared with a client created with default values |
| Roles | New or changed roles, with their composites |
| Groups | A new or changed group, with the whole tree of its top-level group |
| Components (user federation, client registration policies...) | A new or changed component, with all its sub-components |
| Authentication flows | New or changed flows, built-in ones included, with the authenticator configs they use. A changed config of the reference is kept too |
| Client scopes, identity providers and their mappers, required actions, client policies and profiles, scope mappings, default client scopes, default groups | New or changed ones, whole |

Left out:

- Keycloak ids, and the `containerId` of roles.
- Key providers: they hold the keys Keycloak generated for the realm.
- The user profile, users and their credentials.
- Authentication flows that no top-level flow uses, and authenticator configs no flow uses: they are logged.
- SAML attributes that older versions of Keycloak give to OpenID Connect clients.

A resource of the reference missing from the export is logged: an import of the normalized file would not remove it.

```text
Default realm client 'broker' was deleted in exported realm 'my-realm'. It may be reintroduced during import!
```

## Reference realms

keycloak-config-cli ships reference realms for Keycloak `19.0.3`, `20.0.3`, `25.0.5`, `26.0.5` and `26.5.3`. An export is compared with the reference of its version (`keycloakVersion` of the export). When there is none:

1. the reference of `normalization.fallback-version`, when set;
2. else the closest reference: the greatest lower version of the same major version, else the lowest greater version of the same major version, else the greatest lower version.

The closer the reference, the smaller the result: a reference of another version shows the changes Keycloak made between both versions as configuration.

## After normalizing

- Replace the values that depend on the environment, and the secrets, with variables: see [Templating with Mustache](templating.md).
- Split the file by resource type if it helps: keycloak-config-cli imports several files of the same realm.
- Try the import on a test instance, or run a [dry run](dry-run.md) against the realm the export comes from and review the changes it plans.
