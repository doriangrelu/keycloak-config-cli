# Exit Codes

keycloak-config-cli returns a distinct exit code for each kind of failure, so that a pipeline can react accordingly: fix the configuration, or simply retry later.

| Code | Meaning | What to do |
|------|---------|------------|
| `0` | Success | — |
| `1` | Unexpected error | Read the logs; the full stack trace is logged |
| `2` | Invalid configuration: import file, properties, or a reference to a missing resource | Fix the configuration |
| `3` | Keycloak is unreachable or did not answer in time | Retry later |
| `4` | Keycloak refused an operation: missing rights, rejected or conflicting configuration | Check the account rights or the configuration |
| `5` | An already applied import changed while `import.behaviors.checksum-changed=fail` | Decide manually whether to apply it |
| `10` | Dry run only: changes are pending | Review the planned changes |

Exit codes are part of the public interface of the tool: they only change with a major version.

## Example

```bash
java -jar keycloak-config-cli.jar --import.files.locations=realm.yaml
case $? in
  0) echo "Imported" ;;
  3) echo "Keycloak unavailable, retrying later" ;;
  *) echo "Import failed" ; exit 1 ;;
esac
```

## Upgrading from 6.x

Version 6.x returned `1` for every failure. Scripts that only check for a non-zero exit code keep working.
