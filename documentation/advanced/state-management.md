# State Management

Keycloak Config CLI can track the state of previous imports to optimize performance and detect changes.

## How State Works

The CLI stores checksums of imported configurations:

```
Import 1: config.yaml → checksum: abc123 → Store in Keycloak
Import 2: config.yaml → checksum: abc123 → Skip (unchanged)
Import 3: config.yaml → checksum: def456 → Apply changes
```

## Configuration

### Enable State (Default)

```yaml
import:
  state: true
```

### Disable State

```yaml
import:
  state: false
```

### Force Import

Bypass state checking and apply all configurations:

```yaml
import:
  force: true
```

## State Storage

State is stored in Keycloak itself as realm attributes:

```
Realm Attributes:
  keycloak-config-cli-checksum: "sha256:abc123..."
  keycloak-config-cli-checksum-users: "sha256:def456..."
  ...
```

### Viewing State

In Keycloak Admin Console:
1. Go to Realm Settings
2. Check the Attributes section
3. Look for `keycloak-config-cli-*` attributes

### Clearing State

To force a fresh import:

1. **Via CLI**:
   ```bash
   java -jar keycloak-config-cli.jar \
     --import.force=true \
     # ... other options
   ```

2. **Manually in Keycloak**:
   - Delete `keycloak-config-cli-*` attributes from realm

## State Encryption

For sensitive environments, encrypt state data:

```yaml
import:
  state: true
  state-encryption-key: "${STATE_ENCRYPTION_KEY}"
```

The key should be:
- At least 16 characters
- Stored securely (vault, env var)
- Consistent across all imports

## When State Helps

### Large Configurations

State prevents unnecessary API calls:

```
Without state: 500 groups → 500+ API calls every run
With state: 500 groups → Check checksum → 0 API calls if unchanged
```

### CI/CD Pipelines

Faster pipeline execution when configuration hasn't changed:

```yaml
# .gitlab-ci.yml
deploy:
  script:
    - java -jar keycloak-config-cli.jar
      --import.state=true
      --import.files.locations=config/
  # Fast if nothing changed
```

### Detecting External Changes

State helps identify when Keycloak was modified outside of the CLI:

```
CLI checksum: abc123
Current Keycloak state: Different

Warning: Configuration drift detected
```

## When to Disable State

### Initial Migrations

When migrating from another system:

```yaml
import:
  state: false  # Or force: true
```

### After Manual Changes

If you made intentional changes in Keycloak Admin Console:

```bash
java -jar keycloak-config-cli.jar \
  --import.force=true  # Re-apply config
```

### Debugging

When troubleshooting import issues:

```yaml
import:
  state: false
logging:
  level:
    io.github.doriangrelu.keycloak.config: DEBUG
```

## State and Multiple Imports

### Same Configuration Multiple Times

```bash
# First run: applies all changes
./import.sh

# Second run: skips (state matches)
./import.sh

# Third run with changes: applies only changes
# After modifying config.yaml
./import.sh
```

### Different Configurations

Each configuration file has independent state:

```bash
# realms.yaml and users.yaml have separate checksums
./import.sh --import.files.locations=realms.yaml,users.yaml
```

## State with Full Management Mode

State works with `managed: full`:

```yaml
import:
  state: true
  managed:
    group: full
```

The CLI:
1. Checks if configuration changed
2. If changed, applies updates AND deletes orphaned resources
3. Updates state checksum

## Best Practices

### 1. Always Enable in Production

```yaml
import:
  state: true
```

Reduces load on Keycloak and speeds up deployments.

### 2. Use Force Sparingly

Only use `--import.force=true` when needed:
- After manual Keycloak changes
- When state seems corrupted
- During initial setup

### 3. Encrypt State in Sensitive Environments

```yaml
import:
  state-encryption-key: "${ENCRYPTED_STATE_KEY}"
```

### 4. Monitor State

Include state checking in your monitoring:

```bash
# Check if import would make changes
java -jar keycloak-config-cli.jar \
  --import.state=true \
  --logging.level.root=INFO
```

Look for "No changes detected" or "Applying changes" in logs.

## Troubleshooting

### "State checksum mismatch"

The configuration changed since last import. This is normal when you update your config files.

### Import Doesn't Apply Changes

Check if state is preventing updates:

1. Run with `--import.force=true` to bypass state
2. Check if the right files are being imported
3. Verify state attributes in Keycloak

### State Corruption

If state seems corrupted:

1. Clear state attributes manually in Keycloak
2. Run with `--import.force=true`
3. Future imports will rebuild state

### Multiple CLI Instances

If multiple CLI instances import to the same realm:

- They may overwrite each other's state
- Use locking mechanisms in your CI/CD
- Consider separate realms or careful orchestration
