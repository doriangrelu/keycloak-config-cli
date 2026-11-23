# Red Hat SSO Compatibility

## Red Hat SSO 7.x

Red Hat SSO 7.x is based on Keycloak 18 and older ([version mapping](https://access.redhat.com/articles/2342881)). keycloak-config-cli 7.0.0 targets Keycloak 26 and Java 25, and is not compatible with it.

## Red Hat build of Keycloak

The Red Hat build of Keycloak follows the versions of Keycloak. Its 26.x versions are the ones keycloak-config-cli targets, but only the community Keycloak 26.5.3 is tested. To check a server, run the integration tests against its Keycloak version:

```bash
./mvnw verify -Dkeycloak.version=<version>
```

See [Compatibility with Keycloak](../../README.md#compatibility-with-keycloak).
