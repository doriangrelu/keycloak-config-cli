# Example: Full Realm Configuration

This example demonstrates a complete, production-ready realm configuration combining all features.

## Use Case

A SaaS platform with:
- Multiple client applications (web, mobile, API)
- Social login (Google, GitHub)
- Corporate SSO via SAML
- Role-based access control
- Multi-tenancy via groups
- Custom authentication flow with optional 2FA

## File Structure

```
config/
├── 00-realm.yaml           # Realm settings
├── 01-roles.yaml           # Role definitions
├── 02-client-scopes.yaml   # Custom scopes
├── 03-clients.yaml         # Client applications
├── 04-identity-providers.yaml  # Social/SAML providers
├── 05-auth-flows.yaml      # Custom authentication
├── 06-groups.yaml          # Group hierarchy
└── 07-users.yaml           # Initial users
```

## Configuration Files

### `00-realm.yaml`

```yaml
realm: saas-platform
enabled: true
displayName: "SaaS Platform"
displayNameHtml: "<strong>SaaS</strong> Platform"

# Login configuration
loginWithEmailAllowed: true
duplicateEmailsAllowed: false
registrationAllowed: true
registrationEmailAsUsername: true
resetPasswordAllowed: true
rememberMe: true
verifyEmail: true
loginTheme: "keycloak"

# Token settings
accessTokenLifespan: 300
accessTokenLifespanForImplicitFlow: 600
ssoSessionIdleTimeout: 1800
ssoSessionMaxLifespan: 36000
offlineSessionIdleTimeout: 2592000
offlineSessionMaxLifespanEnabled: true
offlineSessionMaxLifespan: 5184000

# Security
passwordPolicy: >-
  length(10) and
  digits(1) and
  lowerCase(1) and
  upperCase(1) and
  specialChars(1) and
  notUsername and
  notEmail and
  passwordHistory(5)

bruteForceProtected: true
permanentLockout: false
maxFailureWaitSeconds: 900
minimumQuickLoginWaitSeconds: 60
waitIncrementSeconds: 60
maxDeltaTimeSeconds: 43200
failureFactor: 5

# Internationalization
internationalizationEnabled: true
supportedLocales:
  - en
  - fr
  - de
  - es
defaultLocale: en

# Email
smtpServer:
  host: "${SMTP_HOST}"
  port: "${SMTP_PORT:587}"
  from: "${SMTP_FROM:no-reply@saas-platform.com}"
  fromDisplayName: "SaaS Platform"
  replyTo: "${SMTP_REPLY_TO:support@saas-platform.com}"
  starttls: "true"
  auth: "true"
  user: "${SMTP_USER}"
  password: "${SMTP_PASSWORD}"

# Events
eventsEnabled: true
eventsExpiration: 2592000
eventsListeners:
  - jboss-logging
enabledEventTypes:
  - LOGIN
  - LOGIN_ERROR
  - LOGOUT
  - REGISTER
  - REGISTER_ERROR
  - CODE_TO_TOKEN
  - CODE_TO_TOKEN_ERROR

adminEventsEnabled: true
adminEventsDetailsEnabled: true
```

### `01-roles.yaml`

```yaml
realm: saas-platform

roles:
  realm:
    # Base roles
    - name: user
      description: "Standard platform user"

    - name: premium
      description: "Premium subscription user"
      composite: true
      composites:
        realm:
          - user

    - name: admin
      description: "Platform administrator"
      composite: true
      composites:
        realm:
          - premium

    # Feature roles
    - name: can-export
      description: "Can export data"

    - name: can-invite
      description: "Can invite team members"

    - name: can-integrate
      description: "Can configure integrations"

    # Subscription tiers
    - name: tier-starter
      description: "Starter tier features"
      composite: true
      composites:
        realm:
          - user

    - name: tier-professional
      description: "Professional tier features"
      composite: true
      composites:
        realm:
          - user
          - can-export
          - can-invite

    - name: tier-enterprise
      description: "Enterprise tier features"
      composite: true
      composites:
        realm:
          - user
          - can-export
          - can-invite
          - can-integrate

defaultRole:
  name: "default-roles-saas-platform"
  composites:
    realm:
      - user
```

### `02-client-scopes.yaml`

```yaml
realm: saas-platform

clientScopes:
  - name: platform.read
    description: "Read platform data"
    protocol: openid-connect
    attributes:
      display.on.consent.screen: "true"
      consent.screen.text: "View your platform data"
    protocolMappers:
      - name: platform-api-audience
        protocol: openid-connect
        protocolMapper: oidc-audience-mapper
        config:
          included.client.audience: "platform-api"
          access.token.claim: "true"

  - name: platform.write
    description: "Modify platform data"
    protocol: openid-connect
    attributes:
      display.on.consent.screen: "true"
      consent.screen.text: "Modify your platform data"

  - name: tenant-info
    description: "Tenant information"
    protocol: openid-connect
    protocolMappers:
      - name: tenant-id
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "tenantId"
          claim.name: "tenant_id"
          access.token.claim: "true"
          id.token.claim: "true"

      - name: tenant-name
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "tenantName"
          claim.name: "tenant_name"
          access.token.claim: "true"

  - name: subscription
    description: "Subscription information"
    protocol: openid-connect
    protocolMappers:
      - name: subscription-tier
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "subscriptionTier"
          claim.name: "subscription_tier"
          access.token.claim: "true"

      - name: subscription-expires
        protocol: openid-connect
        protocolMapper: oidc-usermodel-attribute-mapper
        config:
          user.attribute: "subscriptionExpires"
          claim.name: "subscription_expires"
          access.token.claim: "true"

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
  - platform.read
  - platform.write
  - tenant-info
  - subscription
```

### `03-clients.yaml`

```yaml
realm: saas-platform

clients:
  # Web Application
  - clientId: platform-web
    name: "Platform Web App"
    enabled: true
    publicClient: true
    standardFlowEnabled: true
    rootUrl: "https://app.saas-platform.com"
    redirectUris:
      - "https://app.saas-platform.com/*"
      - "http://localhost:3000/*"
    webOrigins:
      - "https://app.saas-platform.com"
      - "http://localhost:3000"
    attributes:
      pkce.code.challenge.method: "S256"
      post.logout.redirect.uris: "+"
    defaultClientScopes:
      - profile
      - email
      - roles
      - tenant-info
    optionalClientScopes:
      - platform.read
      - platform.write
      - subscription
      - offline_access

  # Mobile App
  - clientId: platform-mobile
    name: "Platform Mobile App"
    enabled: true
    publicClient: true
    standardFlowEnabled: true
    redirectUris:
      - "com.saasplatform.app://oauth/callback"
    attributes:
      pkce.code.challenge.method: "S256"
    defaultClientScopes:
      - profile
      - email
      - roles
      - tenant-info
    optionalClientScopes:
      - platform.read
      - platform.write
      - offline_access

  # API Resource Server
  - clientId: platform-api
    name: "Platform API"
    enabled: true
    bearerOnly: true

  # Backend Services
  - clientId: worker-service
    name: "Background Worker"
    enabled: true
    publicClient: false
    serviceAccountsEnabled: true
    standardFlowEnabled: false
    secret: "${WORKER_SERVICE_SECRET}"

  - clientId: notification-service
    name: "Notification Service"
    enabled: true
    publicClient: false
    serviceAccountsEnabled: true
    standardFlowEnabled: false
    secret: "${NOTIFICATION_SERVICE_SECRET}"

roles:
  client:
    platform-api:
      - name: read
      - name: write
      - name: admin
        composite: true
        composites:
          client:
            platform-api:
              - read
              - write

users:
  - username: service-account-worker-service
    serviceAccountClientId: worker-service
    clientRoles:
      platform-api:
        - admin
      realm-management:
        - view-users

  - username: service-account-notification-service
    serviceAccountClientId: notification-service
    clientRoles:
      realm-management:
        - view-users
```

### `04-identity-providers.yaml`

```yaml
realm: saas-platform

identityProviders:
  # Google
  - alias: google
    displayName: "Continue with Google"
    providerId: oidc
    enabled: true
    trustEmail: true
    firstBrokerLoginFlowAlias: "first broker login"
    config:
      clientId: "${GOOGLE_CLIENT_ID}"
      clientSecret: "${GOOGLE_CLIENT_SECRET}"
      authorizationUrl: "https://accounts.google.com/o/oauth2/v2/auth"
      tokenUrl: "https://oauth2.googleapis.com/token"
      userInfoUrl: "https://openidconnect.googleapis.com/v1/userinfo"
      defaultScope: "openid email profile"
      syncMode: "IMPORT"
      guiOrder: "1"

  # GitHub
  - alias: github
    displayName: "Continue with GitHub"
    providerId: github
    enabled: true
    trustEmail: true
    firstBrokerLoginFlowAlias: "first broker login"
    config:
      clientId: "${GITHUB_CLIENT_ID}"
      clientSecret: "${GITHUB_CLIENT_SECRET}"
      defaultScope: "user:email"
      guiOrder: "2"

  # Corporate SAML (optional)
  - alias: corporate-sso
    displayName: "Enterprise SSO"
    providerId: saml
    enabled: "${CORPORATE_SSO_ENABLED:false}"
    trustEmail: true
    config:
      entityId: "${KEYCLOAK_URL}/realms/saas-platform"
      singleSignOnServiceUrl: "${CORPORATE_SSO_URL}"
      nameIDPolicyFormat: "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"
      signatureAlgorithm: "RSA_SHA256"
      wantAuthnRequestsSigned: "true"
      guiOrder: "0"

identityProviderMappers:
  - name: google-default-role
    identityProviderAlias: google
    identityProviderMapper: hardcoded-role-idp-mapper
    config:
      role: "user"

  - name: github-default-role
    identityProviderAlias: github
    identityProviderMapper: hardcoded-role-idp-mapper
    config:
      role: "user"
```

### `05-auth-flows.yaml`

```yaml
realm: saas-platform

authenticationFlows:
  # Custom browser flow with optional 2FA
  - alias: "platform-browser"
    description: "Platform browser flow with conditional OTP"
    providerId: "basic-flow"
    topLevel: true
    builtIn: false
    authenticationExecutions:
      - authenticator: "auth-cookie"
        requirement: "ALTERNATIVE"
        priority: 10

      - authenticator: "identity-provider-redirector"
        requirement: "ALTERNATIVE"
        priority: 20

      - authenticatorFlow: true
        requirement: "ALTERNATIVE"
        priority: 30
        flowAlias: "platform-browser forms"

  - alias: "platform-browser forms"
    providerId: "basic-flow"
    topLevel: false
    authenticationExecutions:
      - authenticator: "auth-username-password-form"
        requirement: "REQUIRED"
        priority: 10

      - authenticatorFlow: true
        requirement: "CONDITIONAL"
        priority: 20
        flowAlias: "platform-browser conditional otp"

  - alias: "platform-browser conditional otp"
    providerId: "basic-flow"
    topLevel: false
    authenticationExecutions:
      - authenticator: "conditional-user-configured"
        requirement: "REQUIRED"
        priority: 10

      - authenticator: "auth-otp-form"
        requirement: "REQUIRED"
        priority: 20

# Bind custom flow
browserFlow: "platform-browser"
```

### `06-groups.yaml`

```yaml
realm: saas-platform

groups:
  # Tenant structure
  - name: tenants
    subGroups:
      - name: acme-corp
        attributes:
          tenantId: ["tenant-001"]
          tenantName: ["Acme Corporation"]
        subGroups:
          - name: admins
            realmRoles:
              - admin
          - name: members
            realmRoles:
              - user

      - name: globex-inc
        attributes:
          tenantId: ["tenant-002"]
          tenantName: ["Globex Inc"]
        subGroups:
          - name: admins
            realmRoles:
              - admin
          - name: members
            realmRoles:
              - user

  # Subscription tiers
  - name: subscriptions
    subGroups:
      - name: starter
        realmRoles:
          - tier-starter
      - name: professional
        realmRoles:
          - tier-professional
      - name: enterprise
        realmRoles:
          - tier-enterprise

  # Platform administration
  - name: platform-admins
    realmRoles:
      - admin
    clientRoles:
      realm-management:
        - realm-admin
```

### `07-users.yaml`

```yaml
realm: saas-platform

users:
  # Platform super admin
  - username: platform-admin@saas-platform.com
    enabled: true
    email: platform-admin@saas-platform.com
    emailVerified: true
    firstName: Platform
    lastName: Administrator
    groups:
      - /platform-admins
    credentials:
      - type: password
        value: "${PLATFORM_ADMIN_PASSWORD}"
        temporary: true
    requiredActions:
      - CONFIGURE_TOTP
```

## Deployment Script

```bash
#!/bin/bash
set -e

# Load environment
source .env

# Import configuration
java -jar keycloak-config-cli.jar \
  --keycloak.url=${KEYCLOAK_URL} \
  --keycloak.user=${KEYCLOAK_ADMIN} \
  --keycloak.password=${KEYCLOAK_ADMIN_PASSWORD} \
  --import.files.locations=config/ \
  --import.var-substitution.enabled=true \
  --import.managed.group=full \
  --import.managed.realm-role=full \
  --import.managed.client-scope=full

echo "Import completed successfully!"
```

## Environment File (`.env`)

```bash
# Keycloak Admin
KEYCLOAK_URL=https://keycloak.saas-platform.com
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=

# SMTP
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USER=apikey
SMTP_PASSWORD=
SMTP_FROM=no-reply@saas-platform.com

# Identity Providers
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
CORPORATE_SSO_ENABLED=false

# Service Accounts
WORKER_SERVICE_SECRET=
NOTIFICATION_SERVICE_SECRET=

# Initial Admin
PLATFORM_ADMIN_PASSWORD=
```

## See Also

- [Basic Realm Example](basic-realm.md)
- [Groups and Roles Example](groups-and-roles.md)
- [Client Configuration Example](client-configuration.md)
