# Dry Run

A dry run shows what an import would change in Keycloak, without changing anything. Use it to review a change of the import files before applying it, or to check in a pipeline that Keycloak matches the files.

## Usage

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations='/config/*' \
  --import.dry-run=true
```

The import files are read and compared with Keycloak as in a real import, including the clean phase: resources the import would delete are listed too. Nothing is written to Keycloak.

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

## Secrets Keycloak hides

Keycloak never gives back some secrets: it answers `**********` instead of the SMTP password of a realm (`smtpServer.password`), the secret of an identity provider (`config.clientSecret`, and any other entry of its `config` Keycloak hides) or the bind credential of an LDAP user storage (`config.bindCredential`, and any other entry of a component `config` Keycloak hides). The import cannot compare these values with the files, so:

- such a value alone is never a change: importing the same files twice changes nothing;
- when its element is updated for another reason, the value of the files is sent with the update, and is not listed among the changed fields;
- the report notes each such value the files declare, on its own line and without its value, and it does not count as a pending change (exit code `0` when nothing else changes):

```text
Realm 'my-realm': 0 change(s)
  (not compared: smtpServer.password of Realm settings 'my-realm', Keycloak hides it)
  (not compared: config.clientSecret of Identity provider 'google', Keycloak hides it)
Dry run: no change pending.
```

In the JSON report, the realm lists them under `notCompared`, absent when there is none:

```json
"notCompared" : [ {
  "type" : "IDENTITY_PROVIDER",
  "label" : "Identity provider",
  "key" : "google",
  "field" : "config.clientSecret"
} ]
```

To change such a secret alone, change it in Keycloak, or change another value of its element in the same import.

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
  --import.files.locations='/config/*' \
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
- **Hidden secrets:** a secret Keycloak hides is not compared (see above): a change of that secret alone is neither shown nor applied.
- **Client authorization:** it is always listed as an update. Its resources and policies name Keycloak objects through placeholders (`client.resource.$my-client`), resolved only when they are applied.
