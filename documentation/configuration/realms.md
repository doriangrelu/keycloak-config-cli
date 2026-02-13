# Realms Configuration

Realms are the top-level container in Keycloak. Each realm manages its own set of users, credentials, roles, and groups.

## Basic Realm Definition

```yaml
realm: my-realm
enabled: true
```

## Realm Display Settings

```yaml
realm: my-realm
enabled: true
displayName: "My Application"
displayNameHtml: "<strong>My Application</strong>"
```

## Login Settings

```yaml
realm: my-realm
enabled: true

# Login page behavior
loginWithEmailAllowed: true
duplicateEmailsAllowed: false
registrationAllowed: true
registrationEmailAsUsername: false
editUsernameAllowed: false
resetPasswordAllowed: true
rememberMe: true
verifyEmail: false
loginTheme: "keycloak"
```

## Token Settings

```yaml
realm: my-realm

# Access tokens
accessTokenLifespan: 300              # 5 minutes
accessTokenLifespanForImplicitFlow: 900

# SSO Session
ssoSessionIdleTimeout: 1800           # 30 minutes
ssoSessionMaxLifespan: 36000          # 10 hours

# Offline Session
offlineSessionIdleTimeout: 2592000    # 30 days
offlineSessionMaxLifespanEnabled: true
offlineSessionMaxLifespan: 5184000    # 60 days

# Refresh tokens
refreshTokenMaxReuse: 0

# Client session
clientSessionIdleTimeout: 0           # Use SSO session timeout
clientSessionMaxLifespan: 0           # Use SSO session timeout
```

## Password Policy

```yaml
realm: my-realm
passwordPolicy: >-
  length(8) and
  digits(1) and
  lowerCase(1) and
  upperCase(1) and
  specialChars(1) and
  notUsername and
  notEmail and
  passwordHistory(3)
```

Available policies:
- `length(n)` - Minimum length
- `digits(n)` - Minimum digits
- `lowerCase(n)` - Minimum lowercase
- `upperCase(n)` - Minimum uppercase
- `specialChars(n)` - Minimum special characters
- `notUsername` - Cannot be username
- `notEmail` - Cannot be email
- `passwordHistory(n)` - Cannot reuse last n passwords
- `hashIterations(n)` - PBKDF2 iterations
- `forceExpiredPasswordChange(n)` - Force change after n days
- `maxLength(n)` - Maximum length

## Brute Force Protection

```yaml
realm: my-realm
bruteForceProtected: true
permanentLockout: false
maxFailureWaitSeconds: 900
minimumQuickLoginWaitSeconds: 60
waitIncrementSeconds: 60
quickLoginCheckMilliSeconds: 1000
maxDeltaTimeSeconds: 43200
failureFactor: 30
```

## Email Settings

```yaml
realm: my-realm
smtpServer:
  host: "smtp.example.com"
  port: "587"
  from: "keycloak@example.com"
  fromDisplayName: "Keycloak"
  replyTo: "no-reply@example.com"
  starttls: "true"
  auth: "true"
  user: "${SMTP_USER}"
  password: "${SMTP_PASSWORD}"
```

## Internationalization

```yaml
realm: my-realm
internationalizationEnabled: true
supportedLocales:
  - en
  - fr
  - de
  - es
defaultLocale: "en"
```

## Events Configuration

### Login Events

```yaml
realm: my-realm
eventsEnabled: true
eventsExpiration: 2592000  # 30 days
eventsListeners:
  - jboss-logging
enabledEventTypes:
  - LOGIN
  - LOGIN_ERROR
  - LOGOUT
  - REGISTER
  - REGISTER_ERROR
```

### Admin Events

```yaml
realm: my-realm
adminEventsEnabled: true
adminEventsDetailsEnabled: true
```

## OTP Policy

```yaml
realm: my-realm
otpPolicyType: "totp"
otpPolicyAlgorithm: "HmacSHA1"
otpPolicyDigits: 6
otpPolicyInitialCounter: 0
otpPolicyLookAheadWindow: 1
otpPolicyPeriod: 30
```

## WebAuthn Policy

```yaml
realm: my-realm
webAuthnPolicyRpEntityName: "My Application"
webAuthnPolicySignatureAlgorithms:
  - ES256
  - RS256
webAuthnPolicyRpId: ""
webAuthnPolicyAttestationConveyancePreference: "not specified"
webAuthnPolicyAuthenticatorAttachment: "not specified"
webAuthnPolicyRequireResidentKey: "not specified"
webAuthnPolicyUserVerificationRequirement: "not specified"
webAuthnPolicyCreateTimeout: 0
webAuthnPolicyAvoidSameAuthenticatorRegister: false
```

## Complete Realm Example

```yaml
realm: production
enabled: true
displayName: "Production Environment"

# Login settings
loginWithEmailAllowed: true
duplicateEmailsAllowed: false
registrationAllowed: false
resetPasswordAllowed: true
rememberMe: true
verifyEmail: true
loginTheme: "custom-theme"

# Token lifespans
accessTokenLifespan: 300
ssoSessionIdleTimeout: 1800
ssoSessionMaxLifespan: 36000
offlineSessionIdleTimeout: 2592000
offlineSessionMaxLifespanEnabled: true
offlineSessionMaxLifespan: 5184000

# Security
passwordPolicy: "length(12) and digits(1) and upperCase(1) and lowerCase(1) and specialChars(1) and notUsername"
bruteForceProtected: true
permanentLockout: false
maxFailureWaitSeconds: 900
failureFactor: 5

# SMTP
smtpServer:
  host: "${SMTP_HOST}"
  port: "${SMTP_PORT}"
  from: "no-reply@example.com"
  fromDisplayName: "My Application"
  starttls: "true"
  auth: "true"
  user: "${SMTP_USER}"
  password: "${SMTP_PASSWORD}"

# Internationalization
internationalizationEnabled: true
supportedLocales:
  - en
  - fr
defaultLocale: "en"

# Events
eventsEnabled: true
eventsExpiration: 604800
adminEventsEnabled: true
adminEventsDetailsEnabled: true

# Default roles for new users
defaultRole:
  name: "default-roles-production"
  composites:
    realm:
      - user

# Roles
roles:
  realm:
    - name: user
      description: "Standard user"
    - name: admin
      description: "Administrator"

# Groups
groups:
  - name: users
    realmRoles:
      - user
  - name: administrators
    realmRoles:
      - admin
      - user

# Clients
clients:
  - clientId: web-app
    name: "Web Application"
    enabled: true
    publicClient: true
    standardFlowEnabled: true
    redirectUris:
      - "https://app.example.com/*"
    webOrigins:
      - "https://app.example.com"
```

## Realm Import Best Practices

### 1. Environment-Specific Settings

Use variable substitution:

```yaml
realm: ${REALM_NAME:my-realm}
smtpServer:
  host: "${SMTP_HOST}"
  password: "${SMTP_PASSWORD}"
```

### 2. Split Large Configurations

For complex realms, use multiple files:

```
config/
├── 00-realm.yaml          # Realm settings only
├── 01-roles.yaml          # Role definitions
├── 02-groups.yaml         # Group definitions
├── 03-clients.yaml        # Client definitions
└── 04-users.yaml          # User definitions
```

### 3. Secure Sensitive Data

Never commit secrets:

```yaml
# Good: Use variables
smtpServer:
  password: "${SMTP_PASSWORD}"

clients:
  - clientId: my-app
    secret: "${CLIENT_SECRET}"
```

### 4. Document Your Configuration

Use YAML comments:

```yaml
realm: production

# Security: Enforce strong passwords
passwordPolicy: "length(12) and digits(1) and specialChars(1)"

# Session timeout: 30 minutes of inactivity
ssoSessionIdleTimeout: 1800
```
