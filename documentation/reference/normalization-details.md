# Normalization Technical Reference

Detailed technical documentation for the realm normalization feature.

## Configuration Options

See [Normalization](../advanced/normalization.md#properties).

## Comparison

The export and the reference realm are read into the business objects of the import, without their Keycloak ids, and compared resource by resource, by business key: name, client id, group path, alias... The rules are those of the domain (`ReferenceComparison`, and the `withoutValuesOf` methods of the realm settings, flow bindings and clients); see [What is kept](../advanced/normalization.md#what-is-kept).

- For the realm settings and the clients, a value of the reference that the export lacks is no difference: an import cannot remove it.
- The values of the configuration of a component are compared whatever their order: Keycloak does not keep it.
- Built-in flows are compared like the others, and kept when they changed.

## Current Limitations

- **User profile**: the user profile component is left out.
- **Users**: users are not normalized.
- **Removed defaults**: a resource of the reference missing from the export is only logged.

## Data Cleaning

### SAML Attributes on OIDC Clients

OpenID Connect clients from older Keycloak versions may contain SAML-related attributes. These are automatically filtered out.

### Key Providers

Key providers hold the keys Keycloak generated for the realm: they are left out.

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
Default realm required action 'webauthn-register-passwordless' was deleted in exported realm 'my-realm'. It may be reintroduced during import!
```

This often appears when normalizing exports from older Keycloak versions against newer baselines.
