# Dry Run

A dry run shows what an import would change in Keycloak, without changing anything. Use it to review a change of the import files before applying it, or to check in a pipeline that Keycloak matches the files.

## Usage

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=/config/ \
  --import.dry-run=true
```

The import files are read and compared with Keycloak as in a real import, including the clean phase: resources the import would delete are listed too. Nothing is written to Keycloak: no resource, no remote state, no checksum.

## Properties

| Property | Environment variable | Default | Description |
|---|---|---|---|
| `import.dry-run` | `IMPORT_DRYRUN` | `false` | Plan the changes without applying them |
| `import.dry-run-format` | `IMPORT_DRYRUNFORMAT` | `text` | Format of the report: `text` or `json` |
| `import.dry-run-report-file` | `IMPORT_DRYRUNREPORTFILE` | - | File the report is also written to |

## Report

The report lists, realm by realm, the resources to create (`+`), update (`~`) and delete (`-`). An update lists the fields it changes.

```text
Realm 'my-realm': 4 change(s)
  ~ Realm settings 'my-realm' (displayName)
  ~ Client 'app' (redirectUris)
  + Realm role 'writer'
  - Group '/team'
Dry run: 4 change(s) pending.
```

The same report as JSON, with `--import.dry-run-format=json`:

```json
{
  "pendingChanges" : 4,
  "realms" : [ {
    "realm" : "my-realm",
    "changes" : [ {
      "type" : "REALM_SETTINGS",
      "label" : "Realm settings",
      "key" : "my-realm",
      "change" : "update",
      "fields" : [ "displayName" ]
    }, {
      "type" : "CLIENT",
      "label" : "Client",
      "key" : "app",
      "change" : "update",
      "fields" : [ "redirectUris" ]
    }, {
      "type" : "REALM_ROLE",
      "label" : "Realm role",
      "key" : "writer",
      "change" : "create",
      "fields" : [ ]
    }, {
      "type" : "GROUP",
      "label" : "Group",
      "key" : "/team",
      "change" : "delete",
      "fields" : [ ]
    } ]
  } ]
}
```

The report never shows field values: a changed secret, password or credential is listed by its name only.

## Exit code

| Code | Meaning |
|---|---|
| `0` | No change is pending: Keycloak matches the import files |
| `10` | Changes are pending |
| other | The dry run failed, as a real import would: see [Exit Codes](../reference/exit-codes.md) |

## In a pipeline

Run the dry run on a merge request to show the changes it brings, and fail when Keycloak drifted from the files:

```bash
set +e
java -jar keycloak-config-cli.jar \
  --import.files.locations=/config/ \
  --import.dry-run=true \
  --import.dry-run-format=json \
  --import.dry-run-report-file=dry-run.json
status=$?
set -e

case $status in
  0) echo "Keycloak matches the import files" ;;
  10) echo "Changes pending:"; cat dry-run.json; exit 1 ;;
  *) echo "Dry run failed with exit code $status"; exit $status ;;
esac
```

The report file holds the report alone, without the logs of the run: it is the one to parse.

## Limits

- **Realm to create:** when the realm does not exist yet, everything the import declares is listed as a creation. Nothing can be read from a realm that does not exist.
- **Changes that depend on earlier ones:** a type planned after another one does not see the earlier changes, since they are not applied.
- **Client authorization:** it is always listed as an update. Its resources and policies name Keycloak objects through placeholders (`client.resource.$my-client`), resolved only when they are applied.
- **Unchanged files:** a file whose checksum did not change is skipped, as in a real import, so it plans no change. Set `import.cache.enabled=false` to compare every file with Keycloak.
