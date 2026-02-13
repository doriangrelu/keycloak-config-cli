# Identity Providers Configuration

Identity Providers (IdPs) allow users to authenticate using external systems like Google, GitHub, SAML providers, or LDAP.

## OpenID Connect Providers

### Google

```yaml
identityProviders:
  - alias: google
    displayName: "Sign in with Google"
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
```

### GitHub

```yaml
identityProviders:
  - alias: github
    displayName: "Sign in with GitHub"
    providerId: github
    enabled: true
    trustEmail: true
    config:
      clientId: "${GITHUB_CLIENT_ID}"
      clientSecret: "${GITHUB_CLIENT_SECRET}"
      defaultScope: "user:email"
```

### Microsoft / Azure AD

```yaml
identityProviders:
  - alias: microsoft
    displayName: "Sign in with Microsoft"
    providerId: oidc
    enabled: true
    trustEmail: true
    config:
      clientId: "${AZURE_CLIENT_ID}"
      clientSecret: "${AZURE_CLIENT_SECRET}"
      authorizationUrl: "https://login.microsoftonline.com/${AZURE_TENANT_ID}/oauth2/v2.0/authorize"
      tokenUrl: "https://login.microsoftonline.com/${AZURE_TENANT_ID}/oauth2/v2.0/token"
      userInfoUrl: "https://graph.microsoft.com/oidc/userinfo"
      defaultScope: "openid email profile"
```

### Generic OIDC

```yaml
identityProviders:
  - alias: my-oidc-provider
    displayName: "Corporate SSO"
    providerId: oidc
    enabled: true
    trustEmail: true
    config:
      clientId: "${OIDC_CLIENT_ID}"
      clientSecret: "${OIDC_CLIENT_SECRET}"
      authorizationUrl: "https://sso.example.com/authorize"
      tokenUrl: "https://sso.example.com/token"
      userInfoUrl: "https://sso.example.com/userinfo"
      logoutUrl: "https://sso.example.com/logout"
      issuer: "https://sso.example.com"
      defaultScope: "openid email profile"
      validateSignature: "true"
      useJwksUrl: "true"
      jwksUrl: "https://sso.example.com/.well-known/jwks.json"
```

## SAML Providers

### Generic SAML 2.0

```yaml
identityProviders:
  - alias: corporate-saml
    displayName: "Corporate SAML"
    providerId: saml
    enabled: true
    trustEmail: true
    config:
      entityId: "https://keycloak.example.com/realms/my-realm"
      singleSignOnServiceUrl: "https://idp.example.com/sso"
      singleLogoutServiceUrl: "https://idp.example.com/slo"
      nameIDPolicyFormat: "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"
      signatureAlgorithm: "RSA_SHA256"
      wantAuthnRequestsSigned: "true"
      wantAssertionsSigned: "true"
      wantAssertionsEncrypted: "false"
      forceAuthn: "false"
      validateSignature: "true"
      signingCertificate: |
        MIIDXTCCAkWgAwIBAgIJAJC1...
```

### ADFS

```yaml
identityProviders:
  - alias: adfs
    displayName: "Active Directory"
    providerId: saml
    enabled: true
    trustEmail: true
    config:
      entityId: "https://keycloak.example.com/realms/my-realm"
      singleSignOnServiceUrl: "https://adfs.example.com/adfs/ls/"
      nameIDPolicyFormat: "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"
      principalType: "ATTRIBUTE"
      principalAttribute: "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn"
```

## Social Providers

### Facebook

```yaml
identityProviders:
  - alias: facebook
    displayName: "Sign in with Facebook"
    providerId: facebook
    enabled: true
    trustEmail: false
    config:
      clientId: "${FACEBOOK_APP_ID}"
      clientSecret: "${FACEBOOK_APP_SECRET}"
      defaultScope: "email,public_profile"
```

### Twitter/X

```yaml
identityProviders:
  - alias: twitter
    displayName: "Sign in with Twitter"
    providerId: twitter
    enabled: true
    config:
      clientId: "${TWITTER_API_KEY}"
      clientSecret: "${TWITTER_API_SECRET}"
```

### LinkedIn

```yaml
identityProviders:
  - alias: linkedin
    displayName: "Sign in with LinkedIn"
    providerId: linkedin-openid-connect
    enabled: true
    trustEmail: true
    config:
      clientId: "${LINKEDIN_CLIENT_ID}"
      clientSecret: "${LINKEDIN_CLIENT_SECRET}"
```

## Identity Provider Mappers

Map attributes from the IdP to Keycloak user attributes:

```yaml
identityProviderMappers:
  # Map IdP attribute to user attribute
  - name: "department-mapper"
    identityProviderAlias: corporate-saml
    identityProviderMapper: saml-user-attribute-idp-mapper
    config:
      user.attribute: "department"
      attribute.name: "http://schemas.example.com/department"

  # Map to username
  - name: "username-mapper"
    identityProviderAlias: google
    identityProviderMapper: oidc-username-idp-mapper
    config:
      template: "${CLAIM.email}"

  # Hardcoded role assignment
  - name: "default-role-mapper"
    identityProviderAlias: google
    identityProviderMapper: hardcoded-role-idp-mapper
    config:
      role: "external-user"

  # Map IdP groups to Keycloak groups
  - name: "group-mapper"
    identityProviderAlias: corporate-saml
    identityProviderMapper: saml-user-attribute-idp-mapper
    config:
      user.attribute: "groups"
      attribute.name: "http://schemas.example.com/groups"
```

## Broker Login Flows

Control what happens when users log in via an IdP:

```yaml
identityProviders:
  - alias: google
    firstBrokerLoginFlowAlias: "first broker login"
    postBrokerLoginFlowAlias: "post broker login"
    # ...
```

### Custom First Broker Login Flow

```yaml
authenticationFlows:
  - alias: "custom-first-broker-login"
    description: "Custom flow for first-time IdP users"
    providerId: "basic-flow"
    topLevel: true
    builtIn: false
    authenticationExecutions:
      - authenticator: "idp-review-profile"
        requirement: "REQUIRED"
        priority: 10
        authenticatorConfig: "review-profile-config"

      - authenticator: "idp-create-user-if-unique"
        requirement: "ALTERNATIVE"
        priority: 20

      - authenticatorFlow: true
        requirement: "ALTERNATIVE"
        priority: 30
        flowAlias: "Handle Existing Account"

authenticatorConfig:
  - alias: "review-profile-config"
    config:
      update.profile.on.first.login: "on"
```

## Complete Example

```yaml
realm: my-realm

identityProviders:
  # Google for consumers
  - alias: google
    displayName: "Continue with Google"
    providerId: oidc
    enabled: true
    trustEmail: true
    storeToken: false
    linkOnly: false
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

  # Corporate SAML for employees
  - alias: corporate-sso
    displayName: "Employee Login (SSO)"
    providerId: saml
    enabled: true
    trustEmail: true
    storeToken: false
    firstBrokerLoginFlowAlias: "first broker login"
    config:
      entityId: "${KEYCLOAK_URL}/realms/my-realm"
      singleSignOnServiceUrl: "https://sso.corp.example.com/saml/sso"
      singleLogoutServiceUrl: "https://sso.corp.example.com/saml/slo"
      nameIDPolicyFormat: "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"
      signatureAlgorithm: "RSA_SHA256"
      wantAuthnRequestsSigned: "true"
      wantAssertionsSigned: "true"
      validateSignature: "true"
      guiOrder: "0"  # Show first

identityProviderMappers:
  # Google: Set external user role
  - name: google-external-role
    identityProviderAlias: google
    identityProviderMapper: hardcoded-role-idp-mapper
    config:
      role: "external-user"

  # Corporate: Map department
  - name: corporate-department
    identityProviderAlias: corporate-sso
    identityProviderMapper: saml-user-attribute-idp-mapper
    config:
      user.attribute: "department"
      attribute.name: "department"

  # Corporate: Map employee ID
  - name: corporate-employee-id
    identityProviderAlias: corporate-sso
    identityProviderMapper: saml-user-attribute-idp-mapper
    config:
      user.attribute: "employeeId"
      attribute.name: "employeeNumber"

  # Corporate: Assign employee role
  - name: corporate-employee-role
    identityProviderAlias: corporate-sso
    identityProviderMapper: hardcoded-role-idp-mapper
    config:
      role: "employee"
```

## Full Management Mode

```yaml
import:
  managed:
    identity-provider: full
    identity-provider-mapper: full
```

## Best Practices

### 1. Use Environment Variables

```yaml
config:
  clientId: "${IDP_CLIENT_ID}"
  clientSecret: "${IDP_CLIENT_SECRET}"
```

### 2. Set Trust Email Appropriately

```yaml
# Trusted corporate IdP
trustEmail: true

# Untrusted social IdP
trustEmail: false
```

### 3. Configure Display Order

```yaml
config:
  guiOrder: "0"  # Lower numbers appear first
```

### 4. Use Meaningful Aliases

```yaml
alias: google           # Good
alias: idp1             # Bad
```

## Troubleshooting

### Login Loop

Check:
- Redirect URIs are correct
- Token/authorization URLs are correct
- Client ID/secret are correct

### User Not Created

Verify:
- First broker login flow is configured
- Email is provided (if required)
- Username template produces valid username

### Attributes Not Mapped

Ensure:
- Mapper attribute names match IdP claims exactly
- Mapper is associated with correct IdP alias
