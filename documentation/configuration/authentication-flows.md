# Authentication Flows Configuration

Authentication flows define the sequence of steps users must complete to authenticate. Keycloak Config CLI allows you to create custom authentication flows.

## Understanding Authentication Flows

An authentication flow consists of:
- **Flow**: The container for authentication steps
- **Executions**: Individual authentication steps (e.g., username form, OTP)
- **Sub-flows**: Nested flows for grouping executions

## Built-in Flows

Keycloak provides several built-in flows:
- `browser` - Standard browser-based login
- `direct grant` - Resource owner password credentials
- `registration` - User self-registration
- `reset credentials` - Password reset
- `clients` - Client authentication

## Creating a Custom Flow

### Basic Custom Flow

```yaml
authenticationFlows:
  - alias: "my-custom-browser"
    description: "Custom browser authentication"
    providerId: "basic-flow"
    topLevel: true
    builtIn: false
    authenticationExecutions:
      - authenticator: "auth-cookie"
        authenticatorFlow: false
        requirement: "ALTERNATIVE"
        priority: 10

      - authenticator: "auth-spnego"
        authenticatorFlow: false
        requirement: "DISABLED"
        priority: 20

      - authenticatorFlow: true
        requirement: "ALTERNATIVE"
        priority: 30
        flowAlias: "my-custom-browser forms"
```

### Sub-flow Definition

```yaml
authenticationFlows:
  - alias: "my-custom-browser forms"
    description: "Username, password, and OTP"
    providerId: "basic-flow"
    topLevel: false
    builtIn: false
    authenticationExecutions:
      - authenticator: "auth-username-password-form"
        authenticatorFlow: false
        requirement: "REQUIRED"
        priority: 10

      - authenticator: "auth-otp-form"
        authenticatorFlow: false
        requirement: "REQUIRED"
        priority: 20
```

## Common Authenticators

### Cookie Authentication
```yaml
- authenticator: "auth-cookie"
  requirement: "ALTERNATIVE"
```

### Username/Password Form
```yaml
- authenticator: "auth-username-password-form"
  requirement: "REQUIRED"
```

### OTP Form
```yaml
- authenticator: "auth-otp-form"
  requirement: "REQUIRED"  # or CONDITIONAL
```

### Identity Provider Redirector
```yaml
- authenticator: "identity-provider-redirector"
  requirement: "ALTERNATIVE"
```

### Conditional OTP
```yaml
- authenticator: "conditional-user-configured"
  authenticatorFlow: false
  requirement: "REQUIRED"
  priority: 10

- authenticator: "auth-otp-form"
  authenticatorFlow: false
  requirement: "REQUIRED"
  priority: 20
```

## Requirement Types

- `REQUIRED` - Must pass this step
- `ALTERNATIVE` - One of the alternatives must pass
- `CONDITIONAL` - Executes based on conditions
- `DISABLED` - Skipped

## Complete Example: Two-Factor Authentication

```yaml
authenticationFlows:
  # Main browser flow
  - alias: "2fa-browser"
    description: "Browser flow with mandatory 2FA"
    providerId: "basic-flow"
    topLevel: true
    builtIn: false
    authenticationExecutions:
      # Try cookie first
      - authenticator: "auth-cookie"
        authenticatorFlow: false
        requirement: "ALTERNATIVE"
        priority: 10

      # Kerberos (disabled)
      - authenticator: "auth-spnego"
        authenticatorFlow: false
        requirement: "DISABLED"
        priority: 20

      # IdP redirector (optional)
      - authenticator: "identity-provider-redirector"
        authenticatorFlow: false
        requirement: "ALTERNATIVE"
        priority: 25

      # Forms sub-flow
      - authenticatorFlow: true
        requirement: "ALTERNATIVE"
        priority: 30
        flowAlias: "2fa-browser forms"

  # Forms sub-flow
  - alias: "2fa-browser forms"
    description: "Username/password + OTP"
    providerId: "basic-flow"
    topLevel: false
    builtIn: false
    authenticationExecutions:
      - authenticator: "auth-username-password-form"
        authenticatorFlow: false
        requirement: "REQUIRED"
        priority: 10

      # 2FA sub-flow
      - authenticatorFlow: true
        requirement: "REQUIRED"
        priority: 20
        flowAlias: "2fa-browser 2FA"

  # 2FA sub-flow
  - alias: "2fa-browser 2FA"
    description: "Second factor authentication"
    providerId: "basic-flow"
    topLevel: false
    builtIn: false
    authenticationExecutions:
      # Conditional: only if user has OTP configured
      - authenticator: "conditional-user-configured"
        authenticatorFlow: false
        requirement: "REQUIRED"
        priority: 10

      - authenticator: "auth-otp-form"
        authenticatorFlow: false
        requirement: "REQUIRED"
        priority: 20
```

## Binding Flows to Realm

After creating custom flows, bind them:

```yaml
realm: my-realm
browserFlow: "2fa-browser"
registrationFlow: "registration"
directGrantFlow: "direct grant"
resetCredentialsFlow: "reset credentials"
clientAuthenticationFlow: "clients"
```

## Binding Flows to Clients

Override realm flows for specific clients:

```yaml
clients:
  - clientId: high-security-app
    authenticationFlowBindingOverrides:
      browser: "2fa-browser"
```

## Authenticator Config

Some authenticators need configuration:

```yaml
authenticationFlows:
  - alias: "my-flow"
    authenticationExecutions:
      - authenticator: "identity-provider-redirector"
        authenticatorFlow: false
        requirement: "ALTERNATIVE"
        priority: 10
        authenticatorConfig: "google-idp-redirector"

authenticatorConfig:
  - alias: "google-idp-redirector"
    config:
      defaultProvider: "google"
```

## Full Management Mode

```yaml
import:
  managed:
    authentication-flow: full
```

### Protected Flows

Built-in flows are never deleted:
- `browser`
- `direct grant`
- `registration`
- `reset credentials`
- `clients`

## Conditional Flows

Create flows that execute based on conditions:

```yaml
authenticationFlows:
  - alias: "conditional-otp-flow"
    description: "OTP only for certain users"
    providerId: "basic-flow"
    topLevel: false
    builtIn: false
    authenticationExecutions:
      # Condition: user has role
      - authenticator: "conditional-user-role"
        authenticatorFlow: false
        requirement: "REQUIRED"
        priority: 10
        authenticatorConfig: "require-otp-role"

      # If condition met, require OTP
      - authenticator: "auth-otp-form"
        authenticatorFlow: false
        requirement: "REQUIRED"
        priority: 20

authenticatorConfig:
  - alias: "require-otp-role"
    config:
      condUserRole: "require-otp"
      negate: "false"
```

## Best Practices

### 1. Test Flows Thoroughly

Before deploying:
- Test all paths through the flow
- Test error conditions
- Test with different user states (new, existing, locked)

### 2. Keep Flows Simple

```yaml
# Good: Simple, clear flow
authenticationExecutions:
  - authenticator: "auth-username-password-form"
    requirement: "REQUIRED"
  - authenticator: "auth-otp-form"
    requirement: "REQUIRED"

# Avoid: Overly complex nested flows
```

### 3. Document Custom Flows

```yaml
authenticationFlows:
  - alias: "corporate-sso"
    description: |
      Corporate SSO flow:
      1. Try SSO cookie
      2. Try Kerberos
      3. Fall back to LDAP username/password
      4. Require OTP for admins
```

### 4. Version Control Flow Changes

Track all flow changes in Git. Authentication flow changes can lock users out.

## Troubleshooting

### Users Can't Log In

Check:
- Flow is correctly bound to realm/client
- All required authenticators are available
- Sub-flow aliases match exactly

### OTP Not Working

Verify:
- OTP authenticator is in flow
- User has OTP configured
- OTP policy matches authenticator expectations

### Flow Not Applied

Ensure:
- Flow `topLevel: true` for main flows
- Flow bound to realm or client
- No conflicting client override
