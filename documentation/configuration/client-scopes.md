# Client Scopes Configuration

Client scopes define reusable sets of protocol mappers and role scope mappings that can be shared across multiple clients.

## Basic Client Scope

```yaml
clientScopes:
  - name: my-custom-scope
    description: "Custom scope for my application"
    protocol: openid-connect
```

## Client Scope with Protocol Mappers

```yaml
clientScopes:
  - name: profile-extended
    description: "Extended profile information"
    protocol: openid-connect
    protocolMappers:
      - name: department
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "department"
          claim.name: "department"
          id.token.claim: "true"
          access.token.claim: "true"
          userinfo.token.claim: "true"

      - name: employee-id
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "employeeId"
          claim.name: "employee_id"
          id.token.claim: "true"
          access.token.claim: "true"
```

## Common Protocol Mappers

### User Attribute Mapper

```yaml
protocolMappers:
  - name: phone-mapper
    protocol: openid-connect
    protocolMapper: oidc-usermodel-attribute-mapper
    config:
      user.attribute: "phone"
      claim.name: "phone_number"
      id.token.claim: "true"
      access.token.claim: "true"
      userinfo.token.claim: "true"
      jsonType.label: "String"
```

### User Property Mapper

```yaml
protocolMappers:
  - name: email-mapper
    protocol: openid-connect
    protocolMapper: oidc-usermodel-property-mapper
    config:
      user.attribute: "email"
      claim.name: "email"
      id.token.claim: "true"
      access.token.claim: "true"
```

### Hardcoded Claim Mapper

```yaml
protocolMappers:
  - name: api-version
    protocol: openid-connect
    protocolMapper: oidc-hardcoded-claim-mapper
    config:
      claim.name: "api_version"
      claim.value: "v2"
      access.token.claim: "true"
      jsonType.label: "String"
```

### Group Membership Mapper

```yaml
protocolMappers:
  - name: groups
    protocol: openid-connect
    protocolMapper: oidc-group-membership-mapper
    config:
      claim.name: "groups"
      full.path: "false"
      id.token.claim: "true"
      access.token.claim: "true"
```

### Audience Mapper

```yaml
protocolMappers:
  - name: audience-my-api
    protocol: openid-connect
    protocolMapper: oidc-audience-mapper
    config:
      included.client.audience: "my-api"
      access.token.claim: "true"
```

### Role Mapper

```yaml
protocolMappers:
  - name: realm-roles
    protocol: openid-connect
    protocolMapper: oidc-usermodel-realm-role-mapper
    config:
      claim.name: "realm_roles"
      multivalued: "true"
      id.token.claim: "true"
      access.token.claim: "true"
```

## Assigning Client Scopes

### Default Client Scopes (Realm Level)

```yaml
clientScopes:
  - name: my-scope
    # ...

# Set as default for all new clients
defaultDefaultClientScopes:
  - profile
  - email
  - my-scope
```

### Optional Client Scopes (Realm Level)

```yaml
defaultOptionalClientScopes:
  - address
  - phone
  - offline_access
```

### Per-Client Assignment

```yaml
clients:
  - clientId: my-app
    defaultClientScopes:
      - profile
      - email
      - my-scope
    optionalClientScopes:
      - address
      - offline_access
```

## Complete Example

```yaml
realm: my-realm

clientScopes:
  # API access scope
  - name: api.read
    description: "Read access to API"
    protocol: openid-connect
    attributes:
      display.on.consent.screen: "true"
      consent.screen.text: "Read your data"
    protocolMappers:
      - name: api-audience
        protocol: openid-connect
        protocolMapper: oidc-audience-mapper
        config:
          included.client.audience: "my-api"
          access.token.claim: "true"

  - name: api.write
    description: "Write access to API"
    protocol: openid-connect
    attributes:
      display.on.consent.screen: "true"
      consent.screen.text: "Modify your data"

  # Organization info scope
  - name: organization
    description: "Organization information"
    protocol: openid-connect
    attributes:
      include.in.token.scope: "true"
    protocolMappers:
      - name: department
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "department"
          claim.name: "department"
          id.token.claim: "true"
          access.token.claim: "true"
          userinfo.token.claim: "true"

      - name: organization-name
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "organization"
          claim.name: "organization"
          id.token.claim: "true"
          access.token.claim: "true"

      - name: employee-id
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "employeeId"
          claim.name: "employee_id"
          id.token.claim: "true"
          access.token.claim: "true"

  # Microservices scope
  - name: microservices
    description: "Access to internal microservices"
    protocol: openid-connect
    protocolMappers:
      - name: microservices-audience
        protocol: openid-connect
        protocolMapper: oidc-audience-mapper
        config:
          included.client.audience: "user-service"
          access.token.claim: "true"

      - name: microservices-audience-2
        protocol: openid-connect
        protocolMapper: oidc-audience-mapper
        config:
          included.client.audience: "order-service"
          access.token.claim: "true"

# Default scopes for new clients
defaultDefaultClientScopes:
  - profile
  - email
  - roles
  - web-origins
  - acr

defaultOptionalClientScopes:
  - address
  - phone
  - offline_access
  - api.read
  - api.write
  - organization

# Client using custom scopes
clients:
  - clientId: my-web-app
    defaultClientScopes:
      - profile
      - email
      - organization
    optionalClientScopes:
      - api.read
      - api.write
      - offline_access
```

## SAML Client Scopes

```yaml
clientScopes:
  - name: saml-attributes
    protocol: saml
    protocolMappers:
      - name: email
        protocol: saml
        protocolMapper: saml-user-property-mapper
        config:
          user.attribute: "email"
          friendly.name: "email"
          attribute.name: "email"
          attribute.nameformat: "Basic"

      - name: role-list
        protocol: saml
        protocolMapper: saml-role-list-mapper
        config:
          attribute.name: "Role"
          attribute.nameformat: "Basic"
          single: "false"
```

## Full Management Mode

```yaml
import:
  managed:
    client-scope: full
```

### Protected Scopes

Built-in scopes are never deleted:
- `profile`
- `email`
- `address`
- `phone`
- `offline_access`
- `roles`
- `web-origins`
- `microprofile-jwt`
- `acr`

## Best Practices

### 1. Create Reusable Scopes

```yaml
# Good: Reusable across clients
clientScopes:
  - name: api.read
  - name: api.write

# Instead of: Duplicating mappers in each client
```

### 2. Use Consent Screens

```yaml
clientScopes:
  - name: sensitive-data
    attributes:
      display.on.consent.screen: "true"
      consent.screen.text: "Access your sensitive data"
```

### 3. Group Related Claims

```yaml
# Good: Logical grouping
clientScopes:
  - name: organization-info
    protocolMappers:
      - name: department
      - name: employee-id
      - name: manager

# Instead of: Separate scopes for each claim
```

### 4. Document Scopes

```yaml
clientScopes:
  - name: api.admin
    description: |
      Administrative access to the API.
      Includes: user management, configuration changes, audit logs.
      Only assign to admin applications.
```

## Troubleshooting

### Claim Not in Token

Check:
- Scope is assigned to client (default or optional)
- If optional, scope is requested in authorization request
- `access.token.claim` is `"true"` in mapper config

### Scope Not Available

Verify:
- Scope exists in realm
- Scope is in `defaultOptionalClientScopes` or assigned to client

### Consent Screen Not Showing

Ensure:
- `display.on.consent.screen: "true"`
- Client has `consentRequired: true`
