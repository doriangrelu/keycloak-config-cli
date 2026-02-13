# Clients Configuration

Clients in Keycloak represent applications that can request authentication. Keycloak Config CLI provides comprehensive client management.

## Basic Client Definition

### Public Client (SPA, Mobile App)

```yaml
clients:
  - clientId: my-spa
    name: "My Single Page Application"
    enabled: true
    publicClient: true
    standardFlowEnabled: true
    directAccessGrantsEnabled: false
    redirectUris:
      - "http://localhost:3000/*"
      - "https://my-app.example.com/*"
    webOrigins:
      - "http://localhost:3000"
      - "https://my-app.example.com"
```

### Confidential Client (Backend Service)

```yaml
clients:
  - clientId: my-backend-service
    name: "Backend Service"
    enabled: true
    publicClient: false
    serviceAccountsEnabled: true
    standardFlowEnabled: false
    directAccessGrantsEnabled: false
    secret: "${CLIENT_SECRET}"  # Use variable substitution
```

### Bearer-Only Client (API)

```yaml
clients:
  - clientId: my-api
    name: "My API"
    enabled: true
    bearerOnly: true
```

## Client Protocols

### OpenID Connect (Default)

```yaml
clients:
  - clientId: oidc-client
    protocol: openid-connect
    # ... OIDC specific config
```

### SAML

```yaml
clients:
  - clientId: saml-client
    protocol: saml
    attributes:
      saml.assertion.signature: "true"
      saml.server.signature: "true"
      saml_force_name_id_format: "true"
      saml_name_id_format: "username"
```

## Authentication Flows

### Standard Flow (Authorization Code)

```yaml
clients:
  - clientId: web-app
    standardFlowEnabled: true
    redirectUris:
      - "https://app.example.com/callback"
```

### Direct Access Grants (Resource Owner Password)

```yaml
clients:
  - clientId: mobile-app
    directAccessGrantsEnabled: true
```

### Service Account (Client Credentials)

```yaml
clients:
  - clientId: backend-service
    serviceAccountsEnabled: true
    publicClient: false
```

### Implicit Flow (Legacy)

```yaml
clients:
  - clientId: legacy-spa
    implicitFlowEnabled: true
```

## Client Scopes

### Default Client Scopes

```yaml
clients:
  - clientId: my-app
    defaultClientScopes:
      - profile
      - email
      - roles
```

### Optional Client Scopes

```yaml
clients:
  - clientId: my-app
    optionalClientScopes:
      - address
      - phone
      - offline_access
```

## Protocol Mappers

Add custom claims to tokens:

```yaml
clients:
  - clientId: my-app
    protocolMappers:
      # Add user attribute to token
      - name: department
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: department
          claim.name: department
          id.token.claim: "true"
          access.token.claim: "true"
          userinfo.token.claim: "true"

      # Add hardcoded claim
      - name: api-version
        protocol: openid-connect
        protocolMapper: oidc-hardcoded-claim-mapper
        config:
          claim.name: api_version
          claim.value: "v2"
          access.token.claim: "true"

      # Add group membership
      - name: groups
        protocol: openid-connect
        protocolMapper: oidc-group-membership-mapper
        config:
          claim.name: groups
          full.path: "false"
          id.token.claim: "true"
          access.token.claim: "true"
```

## Client Roles

Define roles specific to a client:

```yaml
clients:
  - clientId: my-api
    # ... other config

roles:
  client:
    my-api:
      - name: read
        description: "Read access"
      - name: write
        description: "Write access"
      - name: admin
        description: "Admin access"
```

## Complete Client Example

```yaml
clients:
  - clientId: my-web-application
    name: "My Web Application"
    description: "Main web application"
    enabled: true

    # Client type
    publicClient: false
    bearerOnly: false

    # Authentication
    standardFlowEnabled: true
    implicitFlowEnabled: false
    directAccessGrantsEnabled: false
    serviceAccountsEnabled: false

    # URLs
    rootUrl: "https://app.example.com"
    baseUrl: "/"
    redirectUris:
      - "https://app.example.com/*"
      - "http://localhost:3000/*"  # Development
    webOrigins:
      - "https://app.example.com"
      - "http://localhost:3000"
    adminUrl: "https://app.example.com/admin"

    # Security
    secret: "${MY_APP_CLIENT_SECRET}"

    # Token settings
    attributes:
      access.token.lifespan: "300"
      client.session.idle.timeout: "1800"
      client.session.max.lifespan: "36000"
      oauth2.device.authorization.grant.enabled: "false"
      backchannel.logout.session.required: "true"
      backchannel.logout.url: "https://app.example.com/logout/callback"

    # Scopes
    defaultClientScopes:
      - profile
      - email
      - roles
    optionalClientScopes:
      - address
      - phone
      - offline_access

    # Protocol mappers
    protocolMappers:
      - name: audience
        protocol: openid-connect
        protocolMapper: oidc-audience-mapper
        config:
          included.client.audience: "my-api"
          access.token.claim: "true"
      - name: department
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: department
          claim.name: department
          access.token.claim: "true"
```

## Service Account Configuration

For machine-to-machine communication:

```yaml
clients:
  - clientId: batch-processor
    name: "Batch Processing Service"
    enabled: true
    publicClient: false
    serviceAccountsEnabled: true
    standardFlowEnabled: false
    secret: "${BATCH_PROCESSOR_SECRET}"

    # Service account roles
    # Assign via user configuration
```

Then assign roles to the service account:

```yaml
users:
  - username: service-account-batch-processor
    enabled: true
    serviceAccountClientId: batch-processor
    realmRoles:
      - batch-admin
    clientRoles:
      realm-management:
        - view-users
```

## Client Authorization (Fine-Grained)

For advanced authorization scenarios:

```yaml
clients:
  - clientId: resource-server
    authorizationServicesEnabled: true
    authorizationSettings:
      policyEnforcementMode: ENFORCING
      resources:
        - name: Document
          uris:
            - "/documents/*"
          scopes:
            - name: read
            - name: write
            - name: delete
      policies:
        - name: Admin Policy
          type: role
          config:
            roles: '[{"id":"admin","required":true}]'
      permissions:
        - name: Document Admin Permission
          type: resource
          resources:
            - Document
          policies:
            - Admin Policy
```

## Full Management Mode

```yaml
import:
  managed:
    client: full  # Caution: will delete unmanaged clients
```

### Protected Clients

Built-in clients are never deleted:
- `account`
- `account-console`
- `admin-cli`
- `broker`
- `realm-management`
- `security-admin-console`

## Troubleshooting

### Client Creation Failed

Check:
- `clientId` is unique
- Required fields are present
- Secret is provided for confidential clients

### Redirect URI Mismatch

Ensure redirect URIs match exactly or use wildcards:

```yaml
redirectUris:
  - "https://app.example.com/callback"      # Exact
  - "https://app.example.com/*"             # Wildcard
  - "http://localhost:*/*"                  # Port wildcard
```

### Token Issues

Verify protocol mappers and scopes:

```yaml
defaultClientScopes:
  - profile  # For user info
  - email    # For email claim
  - roles    # For role claims
```
