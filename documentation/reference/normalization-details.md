# Normalization Technical Reference

Detailed technical documentation for the realm normalization feature.

## Configuration Options

| Option | Description | Example |
|--------|-------------|---------|
| `run.operation` | Set to NORMALIZE | `NORMALIZE` |
| `normalization.files.input-locations` | Input realm files | See import patterns |
| `normalization.files.output-directory` | Output directory | `./exports/out` |
| `normalization.output-format` | Output format | `YAML` or `JSON` |
| `normalization.fallback-version` | Baseline version fallback | `19.0.3` |

## Current Limitations

### Not Yet Implemented

- **Components**: The `components` section is not processed (LDAP federation, Key providers)
- **Users**: User normalization is not currently supported

## Data Cleaning

### SAML Attributes on OIDC Clients

OpenID Connect clients from older Keycloak versions may contain SAML-related attributes. These are automatically filtered out.

### Unused Authentication Flows

Non-top-level authentication flows that are not referenced by any top-level flow are detected and excluded.

SQL query to find unreferenced flows:

```sql
SELECT flow.alias
FROM authentication_flow flow
JOIN realm r ON flow.realm_id = r.id
LEFT JOIN authentication_execution execution ON flow.id = execution.auth_flow_id
WHERE r.name = 'mytest'
  AND execution.id IS NULL
  AND NOT flow.top_level;
```

### Unused Authenticator Configs

Configs not referenced by any execution are detected and excluded.

SQL to find unused configs:

```sql
SELECT ac.alias, ac.id
FROM authenticator_config ac
LEFT JOIN authentication_execution ae ON ac.id = ae.auth_config
LEFT JOIN authentication_flow af ON ae.flow_id = af.id
JOIN realm r ON ac.realm_id = r.id
WHERE r.name = 'master' AND af.alias IS NULL
ORDER BY ac.alias;
```

SQL to find duplicate configs:

```sql
SELECT alias, count(alias), r.name AS realm_name
FROM authenticator_config
JOIN realm r ON realm_id = r.id
GROUP BY alias, r.name
HAVING count(alias) > 1;
```

### Invalid Authentication Executions

Executions with invalid subflow references (non-form-flow with authenticator set) are detected and logged as errors.

SQL to find invalid executions:

```sql
SELECT parent.alias, subflow.alias, execution.alias
FROM authentication_execution execution
JOIN realm r ON execution.realm_id = r.id
JOIN authentication_flow parent ON execution.flow_id = parent.id
JOIN authentication_flow subflow ON execution.auth_flow_id = subflow.id
WHERE execution.auth_flow_id IS NOT NULL
  AND execution.authenticator IS NOT NULL
  AND subflow.provider_id <> 'form-flow'
  AND r.name = 'REALMNAME';
```

## Warning Messages

### Missing Default Components

```
Default realm requiredAction 'webauthn-register-passwordless' was deleted in exported realm.
It may be reintroduced during import
```

This often appears when normalizing exports from older Keycloak versions against newer baselines.
