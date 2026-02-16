# Keycloak Realm Configuration – Archetype

Template project for managing a Keycloak realm as code using [keycloak-config-cli](https://github.com/doriangrelu/keycloak-config-cli).

## Quick start

```bash
# 1. Configure your environment
cp .env.example .env
# edit .env with your Keycloak URL, credentials, and realm variables

# 2. Download the CLI
./kc-config.sh download

# 3. Import the realm
./kc-config.sh import
```

## Project structure

```
.env.example                            # Environment variables template
kc-config.sh                            # Wrapper script (download + import)
bin/                                    # (gitignored) Downloaded JAR
realm-config/
  00-realm.yaml                         # Realm settings, security, SMTP, events
  01-roles.yaml                         # Realm-level roles
  02-client-scopes.yaml                 # Custom client scopes
  03-groups.yaml                        # Group hierarchy + role mappings
  clients/
    04-my-backend-client.yaml           # Confidential client (service-account)
    05-my-frontend-client.yaml          # Public client (SPA / PKCE)
  identity-providers/
    06-corporate-saml-idp.yaml          # SAML identity provider + mappers
```

Files are imported in alphabetical order. Numeric prefixes (`00-` … `06-`) guarantee the correct sequence: realm before roles, roles before groups, scopes before clients.

Each file is an independent `RealmImport` and must declare its own `realm:` key.

## Mustache templating

YAML files use Mustache placeholders with built-in defaults:

```yaml
realm: "{{environment:dev}}-{{realm_name:my-realm}}"
```

| Variable | Default | Description |
|---|---|---|
| `realm_name` | `my-realm` | Realm name |
| `environment` | `dev` | Environment prefix (dev, staging, prod) |
| `frontend_url` | `http://localhost:3000` | Frontend application root URL |
| `backend_url` | `http://localhost:8081` | Backend service root URL |
| `smtp_host` | `localhost` | SMTP server host |
| `smtp_port` | `25` | SMTP server port |
| `smtp_from` | `no-reply@example.com` | Sender email address |

Variables are passed to the CLI via `--import.mustache.variables.<key>=<value>`. The wrapper script `kc-config.sh` maps environment variables from `.env` automatically.

Since every variable has a default, the configuration works **out-of-the-box** without a `.env` file.

## Customization

### Adding a new client

1. Create `realm-config/clients/07-my-new-client.yaml`
2. Declare `realm: "{{environment:dev}}-{{realm_name:my-realm}}"` at the top
3. Define the `clients:` array with your client configuration
4. The numeric prefix controls import order relative to other clients

### Adding a new Mustache variable

1. Add the variable to `.env.example`
2. Map it in `kc-config.sh` under the `cmd_import` function:
   ```bash
   --import.mustache.variables.my_var="${MY_VAR:-default_value}"
   ```
3. Use `{{my_var:default_value}}` in YAML files

### Removing the identity provider

Delete `realm-config/identity-providers/06-corporate-saml-idp.yaml` — no other file depends on it.

## Extra CLI options

Any argument after `import` is forwarded to the JAR:

```bash
# Skip SSL verification (development only)
./kc-config.sh import --keycloak.ssl-verify=false

# Target a different login realm
./kc-config.sh import --keycloak.login-realm=other-realm

# Dry-run mode
./kc-config.sh import --import.managed.group=NO_DELETE
```

See the full [CLI reference](https://github.com/doriangrelu/keycloak-config-cli#cli-option--environment-variable) for all available options.
