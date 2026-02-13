# Quick Start Guide

Get up and running with Keycloak Config CLI in minutes.

## Step 1: Prepare Your Keycloak Instance

Ensure you have a Keycloak instance running. For local development:

```bash
docker run -d --name keycloak \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

## Step 2: Create a Configuration File

Create a file named `my-realm.yaml`:

```yaml
realm: my-first-realm
enabled: true
displayName: "My First Realm"

# Define roles
roles:
  realm:
    - name: user
      description: "Standard user role"
    - name: admin
      description: "Administrator role"

# Define groups with role assignments
groups:
  - name: users
    realmRoles:
      - user
  - name: administrators
    realmRoles:
      - admin
      - user

# Define a client application
clients:
  - clientId: my-web-app
    name: "My Web Application"
    enabled: true
    publicClient: true
    standardFlowEnabled: true
    redirectUris:
      - "http://localhost:3000/*"
    webOrigins:
      - "http://localhost:3000"
```

## Step 3: Run the Import

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=my-realm.yaml
```

## Step 4: Verify the Import

1. Open the Keycloak Admin Console: http://localhost:8080/admin
2. Log in with `admin` / `admin`
3. You should see `my-first-realm` in the realm dropdown
4. Navigate to explore your imported configuration

## Using Environment Variables

You can also use environment variables for configuration:

```bash
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_USER=admin
export KEYCLOAK_PASSWORD=admin
export IMPORT_FILES_LOCATIONS=my-realm.yaml

java -jar keycloak-config-cli.jar
```

## Using a Configuration File

Create an `application.yaml` file:

```yaml
keycloak:
  url: http://localhost:8080
  user: admin
  password: admin

import:
  files:
    locations:
      - my-realm.yaml
```

Then run:

```bash
java -jar keycloak-config-cli.jar --spring.config.location=application.yaml
```

## Updating Configurations

Simply modify your YAML file and run the import again. Keycloak Config CLI will:

- **Create** new resources that don't exist
- **Update** existing resources that have changed
- **Skip** resources that are identical

```yaml
# Updated my-realm.yaml with a new group
groups:
  - name: users
    realmRoles:
      - user
  - name: administrators
    realmRoles:
      - admin
      - user
  - name: moderators  # New group
    realmRoles:
      - user
```

```bash
# Re-run the import
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=my-realm.yaml
```

## Full Resource Management

To automatically delete resources that are no longer in your configuration:

```bash
java -jar keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin \
  --import.files.locations=my-realm.yaml \
  --import.managed.group=full \
  --import.managed.role=full \
  --import.managed.client=full
```

> **Warning**: Use `--import.managed.*=full` carefully in production as it will delete resources not defined in your configuration files.

## Next Steps

- [Configuration Properties](configuration.md) - All available options
- [Groups Configuration](../configuration/groups.md) - Detailed group configuration
- [Examples](../examples/basic-realm.md) - More configuration examples
