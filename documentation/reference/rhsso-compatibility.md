# Red Hat SSO Compatibility

keycloak-config-cli is compatible with Red Hat SSO (based on Keycloak).

## Requirements

- Java JDK 8+
- Access to Red Hat Maven repositories

## Building for RH SSO

### 1. Clone Repository

```bash
git clone https://github.com/doriangrelu/keycloak-config-cli.git
cd keycloak-config-cli
```

### 2. Find Correct Version

1. Check RH SSO version mapping: https://access.redhat.com/articles/2342881
2. Find Maven artifact version: https://mvnrepository.com/artifact/org.keycloak/keycloak-core?repo=redhat-ga

Example: For Keycloak 9.0.13, use `9.0.13.redhat-00006`

### 3. Build

```bash
./mvnw clean package -Prh-sso -Dkeycloak.version=9.0.13.redhat-00006
```

### 4. Use

```bash
java -jar target/keycloak-config-cli.jar \
  --keycloak.url=http://localhost:8080 \
  --keycloak.user=admin \
  --keycloak.password=admin123 \
  --import.files.locations=realm.yaml
```

## Notes

- Not officially supported, but community-tested
- Breaking changes in RH SSO may cause compilation errors
- Version-specific compatibility not guaranteed
