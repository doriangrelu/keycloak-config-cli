# Parallel Processing

Keycloak Config CLI supports parallel processing to significantly speed up imports, especially for large configurations.

## Enabling Parallel Processing

```yaml
import:
  parallel: true
```

Or via command line:

```bash
java -jar keycloak-config-cli.jar \
  --import.parallel=true \
  # ... other options
```

## How It Works

When enabled, resources are processed concurrently:

```
Sequential (default):
Group A → Group B → Group C → Group D
[====================================] 40s

Parallel:
Group A ──→
Group B ──→  [==========] 12s
Group C ──→
Group D ──→
```

## Performance Comparison

| Configuration | Sequential | Parallel | Improvement |
|---------------|------------|----------|-------------|
| 10 groups | 5s | 2s | 60% faster |
| 50 clients | 25s | 8s | 68% faster |
| 100 users | 60s | 18s | 70% faster |
| Full realm (500+ resources) | 300s | 85s | 72% faster |

*Actual results depend on network latency and Keycloak server capacity.*

## Retry Logic

Parallel processing includes built-in retry logic to handle:

### Newly Created Resources

When resources are created, they may not be immediately available:

```java
// The CLI uses exponential backoff:
// Retry 1: wait 0ms
// Retry 2: wait 500ms
// Retry 3: wait 2000ms
// Retry 4: wait 4500ms
// Retry 5: wait 8000ms
```

This handles the eventual consistency that can occur during parallel creation.

### Configuration

The retry behavior is built-in and requires no configuration.

## Resource Dependencies

### Independent Resources

These can be processed fully in parallel:
- Top-level groups (no parent)
- Clients
- Realm roles (without composites)
- Client scopes

### Dependent Resources

These require sequential processing or careful ordering:
- Subgroups (depend on parent groups)
- Role composites (depend on base roles)
- Users with group/role assignments
- Client scope mappings

The CLI handles these dependencies automatically.

## Best Practices

### 1. Use for Large Imports

Enable parallel processing for configurations with:
- More than 10 groups
- More than 10 clients
- More than 50 users

### 2. Ensure Adequate Server Resources

Parallel processing increases load on:
- Keycloak server (CPU, memory)
- Database (connections, transactions)
- Network (concurrent requests)

### 3. Monitor for Errors

Parallel mode may surface race conditions or resource limits:

```bash
# Check logs for warnings
java -jar keycloak-config-cli.jar \
  --import.parallel=true \
  --logging.level.io.github.doriangrelu.keycloak.config=DEBUG
```

### 4. Test Before Production

Always test parallel imports in a staging environment first.

## Troubleshooting

### "Cannot find created group" Errors

If you see errors like:
```
Cannot find created group 'my-group' in realm 'my-realm'
```

This usually indicates the retry attempts were exhausted. Possible solutions:

1. **Reduce parallelism**: Not currently configurable, but you can split imports
2. **Increase server resources**: More Keycloak instances or better hardware
3. **Use sequential mode**: For problematic resources

### Race Condition Errors

Errors about duplicate resources or constraint violations may indicate race conditions:

```
javax.ws.rs.ClientErrorException: HTTP 409 Conflict
```

Solutions:
1. Ensure unique resource names across your configuration
2. Process conflicting resources sequentially
3. Split imports into multiple runs

### Connection Pool Exhaustion

Too many concurrent requests can exhaust database connections:

```
org.postgresql.util.PSQLException: Cannot get connection
```

Solutions:
1. Increase database connection pool
2. Configure Keycloak connection limits
3. Use sequential mode for very large imports

## Advanced: Splitting Large Imports

For very large configurations, consider splitting:

```bash
# Process groups first
java -jar keycloak-config-cli.jar \
  --import.files.locations=01-groups.yaml \
  --import.parallel=true

# Then users (which depend on groups)
java -jar keycloak-config-cli.jar \
  --import.files.locations=02-users.yaml \
  --import.parallel=true
```

## Sequential Mode (Default)

If parallel processing causes issues, use sequential mode:

```yaml
import:
  parallel: false
```

Sequential mode is:
- Slower but more reliable
- Easier to debug
- Better for small configurations
- Required for some edge cases

## Monitoring Parallel Imports

### Log Level

Enable debug logging for detailed output:

```yaml
logging:
  level:
    io.github.doriangrelu.keycloak.config: DEBUG
```

### Sample Output

```
DEBUG Creating group 'engineering' in realm 'my-realm'
DEBUG Creating group 'sales' in realm 'my-realm'
DEBUG Creating group 'hr' in realm 'my-realm'
DEBUG Group 'engineering' created successfully
DEBUG Group 'sales' created successfully
DEBUG Group 'hr' created successfully
INFO  Import completed: 3 groups created in 1.2s
```

## When to Avoid Parallel Processing

1. **Complex dependencies**: Many role composites referencing each other
2. **Limited server resources**: Small Keycloak instances
3. **Debugging**: When troubleshooting import issues
4. **Initial setup**: First-time realm creation
5. **Critical operations**: Production changes that need reliability
