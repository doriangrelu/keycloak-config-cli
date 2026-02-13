# Skip Server Info

In Keycloak 26.4.0+, the `/admin/serverinfo` endpoint is restricted to users with administrative roles in the `master` realm.

## Problem

When authenticating against a non-master realm (using `--keycloak.login-realm`), the authenticated user typically doesn't have permissions to access server info, causing keycloak-config-cli to fail.

## Solution

Enable `keycloak.skip-server-info=true`:

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.login-realm=my-realm \
  --keycloak.user=my-user \
  --keycloak.password=my-password \
  --keycloak.skip-server-info=true \
  --import.files.locations=config/
```

## Consequences

When `keycloak.skip-server-info` is enabled:

1. **Version Fetching Skipped**: No call to `/admin/serverinfo`
2. **Version Fallback**: Defaults to `unknown` unless `--keycloak.version` is provided
3. **Alternative Health Check**: Uses authentication against login realm instead
4. **Compatibility Checks**: Some version-specific checks may be skipped

## Recommendation

Provide explicit version when using skip-server-info:

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.login-realm=my-realm \
  --keycloak.skip-server-info=true \
  --keycloak.version=26.4.0 \
  --import.files.locations=config/
```
