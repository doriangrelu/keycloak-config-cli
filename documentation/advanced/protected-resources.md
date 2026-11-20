# Protected Resources

keycloak-config-cli deletes the resources of a declared type that no import file declares (see [Managed Resources](../concepts/managed-resources.md)). Some resources are never deleted:

- **Users**, whatever the files say
- **Technical resources** Keycloak creates, listed [below](#technical-resources)
- **Resources matched by a `protectedResources` rule** of the import files

Protection only prevents deletion. A protected resource that a file declares is still created and updated.

## The `protectedResources` Rules

An import file can list rules that protect resources from deletion. Each rule has up to three fields:

| Field | Values | Description |
|-------|--------|-------------|
| `rule` | `EQUALS` (default) | `matcher` is compared with the resource name exactly |
| | `PATTERN` | `matcher` is a Java regular expression matched against the whole name |
| `matcher` | string, required | The name, or the regular expression |
| `type` | optional, see [below](#restricting-a-rule-to-one-type) | The only resource type the rule protects |

The name is the identifier of the resource in the import files: the client id of a client, the alias of an authentication flow, authenticator config, required action or identity provider, the client or client scope of a scope mapping, the locale of a message bundle, the name otherwise.

Without `type`, a rule applies to every resource type: `legacy-app` protects a client, a role or a group named `legacy-app` alike.

### JSON

```json
{
  "realm": "my-realm",
  "protectedResources": [
    { "rule": "EQUALS", "matcher": "legacy-app" },
    { "rule": "PATTERN", "matcher": "team-.*" }
  ],
  "clients": [
    { "clientId": "my-app" }
  ]
}
```

### YAML

```yaml
realm: my-realm
protectedResources:
  - rule: EQUALS
    matcher: legacy-app
  - rule: PATTERN
    matcher: "team-.*"
clients:
  - clientId: my-app
```

With these rules and the `clients` key declared:

| Client in Keycloak | Result |
|--------------------|--------|
| `my-app` | Created or updated |
| `legacy-app` | Kept (`EQUALS` rule) |
| `team-billing` | Kept (`PATTERN` rule) |
| `my-team-billing` | Deleted: the pattern must match the whole name |
| `old-app` | Deleted |
| `account`, `admin-cli`, ... | Kept (technical resources) |

### Regular Expressions

`PATTERN` uses the Java regular expression syntax and must match the whole name, as if it started with `^` and ended with `$`:

| Matcher | Matches | Does not match |
|---------|---------|----------------|
| `team-.*` | `team-a`, `team-backend` | `my-team-a` |
| `.*-legacy` | `app-legacy` | `app-legacy-2` |
| `(app1\|app2)` | `app1`, `app2` | `app10` |

In YAML, quote matchers that contain special characters.

An invalid regular expression is a configuration error: the import fails when the files are read, before anything is changed in Keycloak, with the exit code of an invalid configuration (`2`).

### Restricting a Rule to One Type

With `type`, a rule only protects the resources of that type:

```yaml
protectedResources:
  - rule: PATTERN
    matcher: "ext-.*"
    type: CLIENT        # protects the clients ext-*, not the roles or groups ext-*
```

| `type` | Resources |
|--------|-----------|
| `CLIENT` | Clients |
| `REALM_ROLE` | Realm roles |
| `CLIENT_ROLE` | Client roles |
| `GROUP` | Groups and subgroups |
| `CLIENT_SCOPE` | Client scopes |
| `SCOPE_MAPPING` | Scope mappings (matched on the client or client scope) |
| `CLIENT_SCOPE_MAPPING` | Client scope mappings |
| `IDENTITY_PROVIDER` | Identity providers |
| `IDENTITY_PROVIDER_MAPPER` | Identity provider mappers |
| `AUTHENTICATION_FLOW` | Authentication flows |
| `AUTHENTICATOR_CONFIG` | Authenticator configs |
| `REQUIRED_ACTION` | Required actions |
| `COMPONENT` | Components |
| `SUB_COMPONENT` | Sub-components |
| `CLIENT_PROFILE` | Client profiles |
| `CLIENT_POLICY` | Client policies |
| `MESSAGE_BUNDLE` | Message bundles (matched on the locale) |
| `CLIENT_AUTHORIZATION` | Authorization settings of a client |

An unknown `type` fails the import when the file is read.

### Several Files

When several files target the same realm, the rules of all the files apply.

## Technical Resources

The resources Keycloak creates for its own use are never deleted, even when their type is declared and no file declares them. There is no option to change this.

### Clients

| Client ID | Purpose |
|-----------|---------|
| `account` | User account management |
| `account-console` | Account management UI |
| `admin-cli` | CLI administration |
| `broker` | Identity brokering |
| `realm-management` | Realm administration |
| `security-admin-console` | Admin console |
| `admin-permissions` | Fine-grained admin permissions |
| `<realm>-realm` | Administration of each realm, in the `master` realm only |

These clients are recognised by their client id, whatever their display name.

### Roles

| Role | Purpose |
|------|---------|
| `offline_access` | Offline token access |
| `uma_authorization` | UMA authorization |
| `default-roles-<realm>` | Default role assignments |

The client roles of the Keycloak clients above (for example `manage-account` of `account`, `view-users` of `realm-management`) are kept as well.

### Client Scopes

| Scope | Purpose |
|-------|---------|
| `profile` | User profile claims |
| `email` | Email claim |
| `roles` | Role claims |
| `web-origins` | CORS origins |
| `offline_access` | Refresh tokens |
| `basic` | Basic claims |
| `acr` | Authentication context |
| `address` | Address claims |
| `phone` | Phone claim |
| `microprofile-jwt` | MicroProfile JWT |
| `organization` | Organization claims |
| `role_list` | SAML role list |
| `saml_organization` | SAML organization claims |
| `service_account` | Service account claims |

The default and optional client scopes of the realm are kept as well.

### Authentication Flows

The built-in authentication flows (`browser`, `direct grant`, `registration`, `reset credentials`, `clients`, `docker auth`, `first broker login`, ...) and the authenticator configs they use are kept.

### Required Actions

Built-in required actions are kept. To stop using one, declare it with `enabled: false`.

### Scope Mappings

The `offline_access` scope mapping and the `account-console` client scope mapping are kept.

### Components

The components that hold the realm keys (`KeyProvider`), the client registration policies and the user profile are kept.

## Common Questions

### Why can't I delete a built-in client?

Built-in clients are required for Keycloak functionality:
- Removing `account` breaks user self-service
- Removing `admin-cli` breaks CLI administration
- Removing `realm-management` breaks admin console

### Can I disable a built-in client instead?

Yes:

```yaml
clients:
  - clientId: account-console
    enabled: false  # Disabled but not deleted
```

### How do I replace a built-in flow?

Create a custom flow and bind it instead:

```yaml
authenticationFlows:
  - alias: "custom-browser"
    # Your custom flow

# Bind the custom flow
browserFlow: "custom-browser"
# The built-in "browser" flow remains but is unused
```

### How do I keep resources managed outside the files?

Either leave their type key out of all the files of the realm (the type is then not touched at all), or protect them with a `protectedResources` rule.

### What if I really need to delete a technical resource?

You must delete it manually via:
- Keycloak Admin Console
- Keycloak Admin REST API directly
- Keycloak CLI (kcadm.sh)

This is intentional to prevent accidental damage.

## Best Practices

### 1. Run a Dry Run First

Check what an import would delete before applying it:

```bash
java -jar keycloak-config-cli.jar \
  --import.files.locations=realm.yaml \
  --import.dry-run=true
```

See [Dry Run](dry-run.md).

### 2. Use Naming Conventions

Prefixes make `PATTERN` rules simple:

```yaml
protectedResources:
  - rule: PATTERN
    matcher: "ext-.*"   # Resources managed by another tool
```

### 3. Prefer `EQUALS` for Single Resources

An exact rule cannot match more than intended. Keep `PATTERN` for families of resources that share a prefix or suffix.
