# Normalization (Export)

Keycloak Config CLI can export existing Keycloak configurations to normalized YAML or JSON files, enabling you to adopt Configuration as Code for existing deployments.

## What is Normalization?

Normalization exports your current Keycloak realm configuration:
- Removes auto-generated fields (IDs, timestamps)
- Organizes resources consistently
- Produces files ready for version control
- Enables migration to declarative configuration

## Running Normalization

### Basic Export

```bash
java -jar keycloak-config-cli.jar \
  --run.operation=NORMALIZE \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --normalization.files.output-directory=/output
```

### Configuration

```yaml
run:
  operation: NORMALIZE

keycloak:
  url: http://localhost:8080
  user: admin
  password: admin

normalization:
  files:
    output-directory: /output/realms
    output-format: YAML  # or JSON
```

## Output Format

### YAML (Recommended)

```yaml
normalization:
  files:
    output-format: YAML
```

Produces readable, editable files:

```yaml
realm: my-realm
enabled: true
groups:
  - name: administrators
    realmRoles:
      - admin
```

### JSON

```yaml
normalization:
  files:
    output-format: JSON
```

Produces:

```json
{
  "realm": "my-realm",
  "enabled": true,
  "groups": [
    {
      "name": "administrators",
      "realmRoles": ["admin"]
    }
  ]
}
```

## Exported Content

Normalization exports:

| Resource | Exported |
|----------|----------|
| Realm settings | Yes |
| Roles (realm & client) | Yes |
| Groups | Yes |
| Clients | Yes |
| Client Scopes | Yes |
| Identity Providers | Yes |
| Authentication Flows | Yes |
| Users | Partial* |
| Credentials | No (security) |

*Users are exported without sensitive data like passwords.

## Use Cases

### 1. Adopting Config-as-Code

Export existing configuration to start managing it declaratively:

```bash
# Export current state
java -jar keycloak-config-cli.jar \
  --run.operation=NORMALIZE \
  --normalization.files.output-directory=./config

# Review and commit
git add config/
git commit -m "Initial Keycloak configuration export"

# Now manage via import
java -jar keycloak-config-cli.jar \
  --run.operation=IMPORT \
  --import.files.locations=./config
```

### 2. Environment Migration

Copy configuration between environments:

```bash
# Export from production
java -jar keycloak-config-cli.jar \
  --run.operation=NORMALIZE \
  --keycloak.url=https://keycloak.prod.example.com \
  --keycloak.user=admin \
  --keycloak.password=${PROD_PASSWORD} \
  --normalization.files.output-directory=./prod-config

# Import to staging
java -jar keycloak-config-cli.jar \
  --run.operation=IMPORT \
  --keycloak.url=https://keycloak.staging.example.com \
  --keycloak.user=admin \
  --keycloak.password=${STAGING_PASSWORD} \
  --import.files.locations=./prod-config
```

### 3. Backup and Recovery

Create configuration backups:

```bash
# Daily backup script
DATE=$(date +%Y%m%d)
java -jar keycloak-config-cli.jar \
  --run.operation=NORMALIZE \
  --normalization.files.output-directory=./backups/$DATE
```

### 4. Auditing and Comparison

Export configurations for diff/comparison:

```bash
# Export current state
java -jar keycloak-config-cli.jar \
  --run.operation=NORMALIZE \
  --normalization.files.output-directory=./current

# Compare with expected
diff -r ./expected ./current
```

## Post-Export Processing

### 1. Review and Clean

The exported files may need adjustment:

```yaml
# Remove environment-specific values
clients:
  - clientId: my-app
    # Replace with variable
    rootUrl: "${APP_URL}"  # Was: https://prod.example.com
```

### 2. Add Variables

Replace hardcoded values with variables:

```yaml
# Before
smtpServer:
  host: smtp.production.example.com
  password: actual-password-here

# After
smtpServer:
  host: "${SMTP_HOST}"
  password: "${SMTP_PASSWORD}"
```

### 3. Remove Auto-Generated Fields

The export should remove these, but verify:

```yaml
# These should NOT be in exported files:
# - id
# - containerId
# - authenticationFlowBindingOverrides (if empty)
```

### 4. Organize Files

Split large exports into logical files:

```bash
# Single large export
output/my-realm.yaml

# Split into manageable files
config/
├── 00-realm.yaml
├── 01-roles.yaml
├── 02-groups.yaml
├── 03-clients.yaml
└── 04-identity-providers.yaml
```

## Normalization vs. Export

| Aspect | Normalization | Keycloak Export |
|--------|--------------|-----------------|
| Purpose | Config-as-Code | Backup/Migration |
| Format | Clean YAML/JSON | Keycloak JSON |
| IDs | Removed | Included |
| Secrets | Excluded | Included (encrypted) |
| Readability | High | Low |
| Version Control | Yes | Difficult |

## Limitations

### Not Exported

- User passwords and credentials
- Session data
- Event logs
- Keycloak internal state

### Requires Manual Adjustment

- Environment-specific URLs
- Secrets and credentials
- Client secrets (need re-generation)

## Best Practices

### 1. Export Regularly

Set up scheduled exports for audit trails:

```bash
# cron: 0 0 * * * /opt/scripts/export-keycloak.sh
```

### 2. Version Control Exports

Commit exports to track changes over time:

```bash
git add config/
git commit -m "Keycloak config: $(date +%Y-%m-%d)"
```

### 3. Sanitize Before Committing

Remove sensitive data before version control:

```bash
# Use a script to replace secrets with placeholders
./sanitize-config.sh config/
```

### 4. Test Import After Export

Verify exports can be re-imported:

```bash
# Export
java -jar keycloak-config-cli.jar --run.operation=NORMALIZE ...

# Import to test instance
java -jar keycloak-config-cli.jar --run.operation=IMPORT ...

# Verify
curl -s http://test-keycloak:8080/admin/realms/my-realm | jq .
```
