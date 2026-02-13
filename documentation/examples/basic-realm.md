# Example: Basic Realm Setup

This example demonstrates a complete basic realm configuration suitable for a simple web application.

## Use Case

A company needs to set up authentication for their internal web application with:
- User/password login
- Basic roles (admin, user)
- Groups for department organization
- A single web application client

## Configuration

### File: `realm.yaml`

```yaml
# Realm Configuration
realm: my-company
enabled: true
displayName: "My Company Portal"

# ============================================
# LOGIN SETTINGS
# ============================================
loginWithEmailAllowed: true
duplicateEmailsAllowed: false
registrationAllowed: false           # Admin creates users
resetPasswordAllowed: true
rememberMe: true
verifyEmail: true
loginTheme: "keycloak"

# ============================================
# TOKEN SETTINGS
# ============================================
accessTokenLifespan: 300              # 5 minutes
ssoSessionIdleTimeout: 1800           # 30 minutes
ssoSessionMaxLifespan: 36000          # 10 hours

# ============================================
# PASSWORD POLICY
# ============================================
passwordPolicy: >-
  length(8) and
  digits(1) and
  upperCase(1) and
  specialChars(1) and
  notUsername

# ============================================
# BRUTE FORCE PROTECTION
# ============================================
bruteForceProtected: true
maxFailureWaitSeconds: 900
failureFactor: 5

# ============================================
# EMAIL SETTINGS
# ============================================
smtpServer:
  host: "${SMTP_HOST}"
  port: "${SMTP_PORT:587}"
  from: "no-reply@mycompany.com"
  fromDisplayName: "My Company"
  starttls: "true"
  auth: "true"
  user: "${SMTP_USER}"
  password: "${SMTP_PASSWORD}"

# ============================================
# ROLES
# ============================================
roles:
  realm:
    - name: user
      description: "Standard user with basic access"
    - name: admin
      description: "Administrator with full access"
      composite: true
      composites:
        realm:
          - user

# ============================================
# DEFAULT ROLES FOR NEW USERS
# ============================================
defaultRole:
  name: "default-roles-my-company"
  composites:
    realm:
      - user

# ============================================
# GROUPS
# ============================================
groups:
  - name: employees
    realmRoles:
      - user
    subGroups:
      - name: engineering
        attributes:
          department: ["Engineering"]
      - name: sales
        attributes:
          department: ["Sales"]
      - name: hr
        attributes:
          department: ["Human Resources"]

  - name: administrators
    realmRoles:
      - admin

# ============================================
# CLIENTS
# ============================================
clients:
  - clientId: company-portal
    name: "Company Portal"
    description: "Internal company web application"
    enabled: true
    publicClient: true
    standardFlowEnabled: true
    directAccessGrantsEnabled: false

    rootUrl: "https://portal.mycompany.com"
    baseUrl: "/"
    redirectUris:
      - "https://portal.mycompany.com/*"
      - "http://localhost:3000/*"           # Development
    webOrigins:
      - "https://portal.mycompany.com"
      - "http://localhost:3000"

    defaultClientScopes:
      - profile
      - email
      - roles

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

# ============================================
# INITIAL USERS
# ============================================
users:
  - username: admin
    enabled: true
    email: admin@mycompany.com
    emailVerified: true
    firstName: System
    lastName: Administrator
    groups:
      - /administrators
    credentials:
      - type: password
        value: "${ADMIN_INITIAL_PASSWORD}"
        temporary: true
```

## Deployment

### 1. Set Environment Variables

```bash
export SMTP_HOST="smtp.mycompany.com"
export SMTP_USER="keycloak@mycompany.com"
export SMTP_PASSWORD="smtp-password"
export ADMIN_INITIAL_PASSWORD="ChangeMeNow123!"
```

### 2. Run Import

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=https://keycloak.mycompany.com \
  --keycloak.user=admin \
  --keycloak.password=${KEYCLOAK_ADMIN_PASSWORD} \
  --import.files.locations=realm.yaml
```

### 3. Verify

1. Log into Keycloak Admin Console
2. Check the `my-company` realm was created
3. Verify roles, groups, and client are configured
4. Test login with the admin user

## What This Configuration Does

### Login Experience
- Users can log in with email or username
- "Remember me" option available
- Email verification required for new users
- Password reset via email enabled

### Security
- Passwords must be 8+ characters with complexity
- Account locks after 5 failed attempts
- Sessions expire after 30 minutes of inactivity
- Access tokens valid for 5 minutes

### Organization
- Three department groups under "employees"
- Separate "administrators" group with admin privileges
- Department stored as user attribute

### Application Integration
- Single page application client configured
- Development URLs included for local testing
- Department claim included in tokens

## Next Steps

After initial setup:

1. **Add more users**: Create employees in their respective groups
2. **Customize branding**: Add custom login theme
3. **Add 2FA**: Configure OTP for administrators
4. **Integration**: Connect your application to this realm

## See Also

- [Groups and Roles Example](groups-and-roles.md)
- [Client Configuration Example](client-configuration.md)
- [Full Realm Example](full-realm-example.md)
