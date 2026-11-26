# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 7.0.x   | ✅ Supported |
| < 7.0   | ❌ Not supported |

## Reporting a Vulnerability

Do not open a public issue for a vulnerability. Report it privately through [GitHub Security Advisories](https://github.com/doriangrelu/keycloak-config-cli/security/advisories/new), with:

- the affected version,
- the steps to reproduce, with a minimal configuration (without real secrets),
- the impact you observed or expect.

You will get an acknowledgment as soon as possible, then updates until the issue is fixed and disclosed.

## Using keycloak-config-cli Securely

keycloak-config-cli holds administrator credentials of Keycloak and changes its configuration, deletions included. To reduce the risk:

- **Least privilege.** Prefer the `client_credentials` grant with a dedicated client whose service account has only the admin roles the import needs, over the `admin` account.
- **Secrets outside the files.** Keep passwords and client secrets out of the import files and out of Git: give them as [Mustache variables](documentation/advanced/templating.md), from environment variables or from files read with `SPRING_CONFIG_IMPORT=configtree:...` (see [Configuration](documentation/getting-started/configuration.md#secrets-read-from-files)).
- **Encrypted connection.** Use an `https://` `keycloak.url` and keep `keycloak.ssl-verify=true` (the default). `keycloak.ssl-verify=false` is meant for local development only.
- **Logs.** Secrets are not logged at the default levels. `logging.level.realm-config=trace` logs the realm configuration, sensitive values included: never enable it in a shared environment.
- **Check before applying.** Run a [dry run](documentation/advanced/dry-run.md) to see the changes, deletions included, before an import; the report never shows field values. Protect what must never be deleted with [protected resources](documentation/advanced/protected-resources.md).
- **Interactive shell.** The shell edits files offline; its connected mode is meant for local, development or test Keycloak instances, never production.

## Dependencies

Dependencies are monitored with Dependabot, and pull requests go through the GitHub dependency review, which fails on new dependencies with known high-severity vulnerabilities.
