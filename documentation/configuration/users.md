# Users Configuration

Users represent individuals who can authenticate with Keycloak. Keycloak Config CLI supports user creation and management.

## Basic User Definition

```yaml
users:
  - username: john.doe
    enabled: true
    email: john.doe@example.com
    firstName: John
    lastName: Doe
```

## User with Password

```yaml
users:
  - username: john.doe
    enabled: true
    email: john.doe@example.com
    credentials:
      - type: password
        value: "${INITIAL_PASSWORD}"
        temporary: true  # Force password change on first login
```

## User with Roles

### Realm Roles

```yaml
users:
  - username: admin.user
    enabled: true
    email: admin@example.com
    realmRoles:
      - admin
      - user
```

### Client Roles

```yaml
users:
  - username: api.user
    enabled: true
    email: api.user@example.com
    clientRoles:
      my-api:
        - read
        - write
      another-client:
        - viewer
```

## User with Groups

```yaml
users:
  - username: john.doe
    enabled: true
    groups:
      - /employees
      - /engineering/backend
```

## User Attributes

```yaml
users:
  - username: john.doe
    enabled: true
    attributes:
      department:
        - "Engineering"
      employee-id:
        - "EMP001"
      phone:
        - "+1-555-0100"
      location:
        - "New York"
```

## Required Actions

Force users to perform actions on next login:

```yaml
users:
  - username: new.user
    enabled: true
    requiredActions:
      - UPDATE_PASSWORD
      - VERIFY_EMAIL
      - UPDATE_PROFILE
      - CONFIGURE_TOTP
```

Available actions:
- `UPDATE_PASSWORD` - Change password
- `VERIFY_EMAIL` - Verify email address
- `UPDATE_PROFILE` - Update profile information
- `CONFIGURE_TOTP` - Set up two-factor authentication
- `terms_and_conditions` - Accept terms

## Email Verification

```yaml
users:
  - username: verified.user
    enabled: true
    email: verified@example.com
    emailVerified: true

  - username: unverified.user
    enabled: true
    email: unverified@example.com
    emailVerified: false
    requiredActions:
      - VERIFY_EMAIL
```

## Complete User Example

```yaml
users:
  - username: john.doe
    enabled: true
    email: john.doe@example.com
    emailVerified: true
    firstName: John
    lastName: Doe

    # Authentication
    credentials:
      - type: password
        value: "${JOHN_INITIAL_PASSWORD}"
        temporary: true

    # Attributes
    attributes:
      department:
        - "Engineering"
      employee-id:
        - "EMP001"
      manager:
        - "jane.smith"
      hire-date:
        - "2023-01-15"

    # Authorization
    realmRoles:
      - user
      - developer
    clientRoles:
      my-api:
        - read
        - write
      jira-integration:
        - user

    # Group membership
    groups:
      - /employees
      - /engineering/backend
      - /projects/alpha

    # Required actions
    requiredActions:
      - UPDATE_PASSWORD
```

## Service Account Users

Service accounts are created automatically for confidential clients with `serviceAccountsEnabled`:

```yaml
# In clients configuration
clients:
  - clientId: my-service
    serviceAccountsEnabled: true
    publicClient: false

# Configure service account user
users:
  - username: service-account-my-service
    serviceAccountClientId: my-service
    realmRoles:
      - service-role
    clientRoles:
      realm-management:
        - view-users
        - view-clients
```

## Federated Identity

Link users to external identity providers:

```yaml
users:
  - username: google.user
    enabled: true
    email: user@gmail.com
    federatedIdentities:
      - identityProvider: google
        userId: "google-user-id-123"
        userName: "user@gmail.com"
```

## Multiple Users Example

```yaml
users:
  # Admin user
  - username: admin
    enabled: true
    email: admin@example.com
    emailVerified: true
    firstName: System
    lastName: Administrator
    realmRoles:
      - admin
    groups:
      - /administrators
    credentials:
      - type: password
        value: "${ADMIN_PASSWORD}"
        temporary: false

  # Regular users
  - username: alice
    enabled: true
    email: alice@example.com
    firstName: Alice
    lastName: Smith
    realmRoles:
      - user
    groups:
      - /employees
      - /engineering
    credentials:
      - type: password
        value: "${DEFAULT_PASSWORD}"
        temporary: true

  - username: bob
    enabled: true
    email: bob@example.com
    firstName: Bob
    lastName: Johnson
    realmRoles:
      - user
    groups:
      - /employees
      - /sales
    credentials:
      - type: password
        value: "${DEFAULT_PASSWORD}"
        temporary: true

  # Disabled user
  - username: former.employee
    enabled: false
    email: former@example.com
```

## Full Management Mode

> **Warning**: User deletion can have serious consequences. Use with extreme caution.

```yaml
import:
  managed:
    user: full  # Will delete users not in configuration!
```

This should almost never be used in production. Prefer `no-delete` for users.

## User Import Best Practices

### 1. Never Commit Real Passwords

```yaml
# Good: Use variables
credentials:
  - type: password
    value: "${USER_PASSWORD}"

# Bad: Hardcoded password
credentials:
  - type: password
    value: "MyP@ssw0rd!"  # Never do this!
```

### 2. Use Temporary Passwords

```yaml
credentials:
  - type: password
    value: "${INITIAL_PASSWORD}"
    temporary: true  # Force change on first login
```

### 3. Prefer Groups Over Direct Roles

```yaml
# Good: Assign via groups
groups:
  - /administrators  # Group has admin role

# Less preferred: Direct role assignment
realmRoles:
  - admin
```

### 4. Keep User Management Separate

Consider managing users separately from other configuration:

```
config/
├── realm/
│   ├── 00-realm.yaml
│   ├── 01-roles.yaml
│   └── 02-clients.yaml
└── users/
    └── users.yaml  # Managed separately or via other means
```

### 5. Consider User Federation

For production systems, prefer:
- LDAP/Active Directory integration
- Identity provider federation
- Self-registration

Over importing users directly.

## Troubleshooting

### User Not Created

Check:
- Username is unique
- Email is unique (if `duplicateEmailsAllowed: false`)
- Required roles exist
- Required groups exist

### Group Assignment Failed

Ensure groups exist and paths are correct:

```yaml
groups:
  - /employees              # Root group
  - /engineering/backend    # Nested group with full path
```

### Password Not Set

Verify credential format:

```yaml
credentials:
  - type: password         # Must be "password"
    value: "..."           # Non-empty value
    temporary: true/false  # Boolean, not string
```
