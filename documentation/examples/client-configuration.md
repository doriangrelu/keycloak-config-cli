# Example: Client Configuration

This example demonstrates various client configurations for different application types.

## Overview

This example covers:
- Single Page Application (SPA)
- Mobile Application
- Backend Service (Machine-to-Machine)
- Traditional Web Application
- API Resource Server

## Configuration

### File: `clients.yaml`

```yaml
realm: my-realm

# ============================================
# CLIENT SCOPES (Reusable)
# ============================================
clientScopes:
  - name: api.read
    description: "Read access to API resources"
    protocol: openid-connect
    attributes:
      display.on.consent.screen: "true"
      consent.screen.text: "Read your data"
    protocolMappers:
      - name: api-audience
        protocol: openid-connect
        protocolMapper: oidc-audience-mapper
        config:
          included.client.audience: "resource-api"
          access.token.claim: "true"

  - name: api.write
    description: "Write access to API resources"
    protocol: openid-connect
    attributes:
      display.on.consent.screen: "true"
      consent.screen.text: "Modify your data"

  - name: api.admin
    description: "Administrative API access"
    protocol: openid-connect

# ============================================
# CLIENTS
# ============================================
clients:
  # ------------------------------------------
  # SINGLE PAGE APPLICATION (React, Vue, Angular)
  # ------------------------------------------
  - clientId: spa-frontend
    name: "Web Frontend"
    description: "React single page application"
    enabled: true

    # Public client - no secret needed
    publicClient: true

    # OAuth flows
    standardFlowEnabled: true          # Authorization code + PKCE
    directAccessGrantsEnabled: false   # No password grant
    implicitFlowEnabled: false         # Don't use implicit

    # URLs
    rootUrl: "https://app.example.com"
    baseUrl: "/"
    redirectUris:
      - "https://app.example.com/*"
      - "http://localhost:3000/*"      # Development
    webOrigins:
      - "https://app.example.com"
      - "http://localhost:3000"

    # Logout
    attributes:
      post.logout.redirect.uris: "https://app.example.com/*##http://localhost:3000/*"
      pkce.code.challenge.method: "S256"

    # Scopes
    defaultClientScopes:
      - profile
      - email
      - roles
    optionalClientScopes:
      - api.read
      - api.write
      - offline_access

  # ------------------------------------------
  # MOBILE APPLICATION (iOS, Android)
  # ------------------------------------------
  - clientId: mobile-app
    name: "Mobile Application"
    description: "Native mobile app for iOS and Android"
    enabled: true

    publicClient: true
    standardFlowEnabled: true
    directAccessGrantsEnabled: false

    # Mobile deep links and custom schemes
    redirectUris:
      - "com.example.app://oauth/callback"
      - "https://app.example.com/mobile/callback"

    attributes:
      pkce.code.challenge.method: "S256"

    defaultClientScopes:
      - profile
      - email
      - roles
    optionalClientScopes:
      - api.read
      - api.write
      - offline_access    # For refresh tokens

  # ------------------------------------------
  # BACKEND SERVICE (Machine-to-Machine)
  # ------------------------------------------
  - clientId: backend-service
    name: "Backend Processing Service"
    description: "Server-side service for background processing"
    enabled: true

    # Confidential client
    publicClient: false
    secret: "${BACKEND_SERVICE_SECRET}"

    # Service account only
    serviceAccountsEnabled: true
    standardFlowEnabled: false
    directAccessGrantsEnabled: false

    # Service account roles
    defaultClientScopes:
      - profile

    attributes:
      access.token.lifespan: "3600"    # 1 hour tokens

  # ------------------------------------------
  # TRADITIONAL WEB APPLICATION (Server-side rendered)
  # ------------------------------------------
  - clientId: web-backend
    name: "Traditional Web App"
    description: "Server-side web application (Spring, Django, etc.)"
    enabled: true

    # Confidential client
    publicClient: false
    secret: "${WEB_BACKEND_SECRET}"

    # Standard OAuth flow
    standardFlowEnabled: true
    directAccessGrantsEnabled: false

    # URLs
    rootUrl: "https://webapp.example.com"
    baseUrl: "/"
    redirectUris:
      - "https://webapp.example.com/oauth/callback"
      - "http://localhost:8080/oauth/callback"
    adminUrl: "https://webapp.example.com"

    # Backchannel logout
    attributes:
      backchannel.logout.session.required: "true"
      backchannel.logout.url: "https://webapp.example.com/logout/callback"

    defaultClientScopes:
      - profile
      - email
      - roles

  # ------------------------------------------
  # API RESOURCE SERVER (Bearer-only)
  # ------------------------------------------
  - clientId: resource-api
    name: "Resource API"
    description: "REST API that validates tokens"
    enabled: true

    # Bearer-only - just validates tokens
    bearerOnly: true

    # Define API-specific roles
    # (roles defined separately below)

    defaultClientScopes:
      - profile

  # ------------------------------------------
  # ADMIN CLI (for scripts and automation)
  # ------------------------------------------
  - clientId: automation-cli
    name: "Automation CLI"
    description: "CLI tool for automated tasks"
    enabled: true

    publicClient: false
    secret: "${AUTOMATION_CLI_SECRET}"

    # Direct access for CLI
    directAccessGrantsEnabled: true
    standardFlowEnabled: false
    serviceAccountsEnabled: true

    attributes:
      access.token.lifespan: "300"     # Short-lived tokens

# ============================================
# CLIENT ROLES
# ============================================
roles:
  client:
    resource-api:
      - name: read
        description: "Read API resources"
      - name: write
        description: "Write API resources"
      - name: delete
        description: "Delete API resources"
      - name: admin
        description: "Full API administration"
        composite: true
        composites:
          client:
            resource-api:
              - read
              - write
              - delete

# ============================================
# SERVICE ACCOUNT CONFIGURATION
# ============================================
users:
  # Backend service account
  - username: service-account-backend-service
    serviceAccountClientId: backend-service
    realmRoles:
      - offline_access
    clientRoles:
      resource-api:
        - read
        - write
      realm-management:
        - view-users

  # Automation CLI service account
  - username: service-account-automation-cli
    serviceAccountClientId: automation-cli
    clientRoles:
      realm-management:
        - manage-users
        - view-clients
```

## Client Type Summary

| Client | Type | Auth Method | Use Case |
|--------|------|-------------|----------|
| `spa-frontend` | Public | PKCE | Browser-based SPA |
| `mobile-app` | Public | PKCE | Native mobile apps |
| `backend-service` | Confidential | Client Credentials | Server-to-server |
| `web-backend` | Confidential | Authorization Code | Traditional web app |
| `resource-api` | Bearer-only | Token Validation | API resource server |
| `automation-cli` | Confidential | Password/Client Creds | Scripts, CLI tools |

## Token Configuration

### Customizing Token Lifespans

```yaml
clients:
  - clientId: my-app
    attributes:
      # Access token lifespan (seconds)
      access.token.lifespan: "300"

      # Client session idle timeout
      client.session.idle.timeout: "1800"

      # Client session max lifespan
      client.session.max.lifespan: "36000"
```

### Adding Custom Claims

```yaml
clients:
  - clientId: my-app
    protocolMappers:
      # User attribute
      - name: department
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "department"
          claim.name: "department"
          access.token.claim: "true"
          id.token.claim: "true"

      # Groups
      - name: groups
        protocol: openid-connect
        protocolMapper: oidc-group-membership-mapper
        config:
          claim.name: "groups"
          full.path: "false"
          access.token.claim: "true"

      # Static value
      - name: app-version
        protocol: openid-connect
        protocolMapper: oidc-hardcoded-claim-mapper
        config:
          claim.name: "app_version"
          claim.value: "v2"
          access.token.claim: "true"
```

## Deployment

```bash
# Set secrets
export BACKEND_SERVICE_SECRET=$(openssl rand -base64 32)
export WEB_BACKEND_SECRET=$(openssl rand -base64 32)
export AUTOMATION_CLI_SECRET=$(openssl rand -base64 32)

# Import
java -jar keycloak-config-cli.jar \
  --keycloak.url=https://keycloak.example.com \
  --keycloak.user=admin \
  --keycloak.password=${KEYCLOAK_PASSWORD} \
  --import.files.locations=clients.yaml
```

## Best Practices

1. **Always use PKCE** for public clients (SPAs, mobile apps)
2. **Never use implicit flow** - it's deprecated
3. **Prefer service accounts** over password grants for M2M
4. **Use short token lifespans** for high-security applications
5. **Configure backchannel logout** for server-side apps
6. **Store secrets securely** - use vault or environment variables

## See Also

- [Clients Configuration](../configuration/clients.md)
- [Client Scopes Configuration](../configuration/client-scopes.md)
- [Basic Realm Example](basic-realm.md)
