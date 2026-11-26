# Contributing

Thank you for your interest in improving keycloak-config-cli! Issues, pull requests and feedback are welcome.

Please also read the [Code of Conduct](CODE_OF_CONDUCT.md) and the [Security Policy](SECURITY.md). The architecture rules, the repository map and the development workflow are in [AGENTS.md](AGENTS.md): read it before a non-trivial change, whether you write the code yourself or with an AI coding agent.

## Before you start

- Small fixes and documentation improvements: open a pull request directly.
- New features, behaviour changes or larger refactorings: open an [issue](https://github.com/doriangrelu/keycloak-config-cli/issues) first to agree on the approach. Changes of behaviour are framed in an architecture decision record (ADR) in [`documentation/architecture/`](documentation/architecture/), indexed in [ARCHITECTURE.md](ARCHITECTURE.md).
- Look for issues labelled `help wanted` or `good first issue` if you are looking for something to work on.

## Development setup

- JDK 25 (the CI uses Temurin 25).
- Docker, for the integration tests (Testcontainers starts Keycloak) and the [sample](sample/README.md).
- The Maven wrapper is included: use `./mvnw` (or `mvnw.cmd` on Windows) without installing Maven. Set `JAVA_HOME` to the JDK 25.

## Building and testing

```bash
./mvnw -B spotless:apply              # format the code (Palantir Java Format)
./mvnw -B clean package -DskipTests   # build target/keycloak-config-cli.jar
./mvnw -B test -Dtest='SomeTest'      # run some unit tests
./mvnw -B verify                      # format check, unit tests (*Test) and integration tests (*IT)
```

- Integration tests need a working Docker environment. With a Docker Desktop replacement where `host.docker.internal` cannot be resolved, set the LDAP host used by the tests: `./mvnw verify -DJUNIT_LDAP_HOST=<host or IP>`.
- Test another Keycloak version with `-Dkeycloak.version=<version>`.
- `./sample/scripts/run-all.sh` runs end-to-end scenarios against a throwaway Keycloak (Bash and Docker Compose, JAR built first).

## Style and quality

- Keep changes focused. Avoid unrelated refactorings in the same pull request.
- The build fails on unformatted code: run `./mvnw spotless:apply` before committing.
- The layers (`domain`, `application`, `infrastructure`) are checked by `ArchitectureTest`; see [AGENTS.md](AGENTS.md#architecture-rules).
- Every new Java file starts with the license header: copy it from a neighbouring file.
- Write tests first. Unit tests are named `*Test`, integration tests against Keycloak `*IT`.
- Never log or print a password, client secret or token.

## Commit messages

Use the conventional style of the history, in English:

```
feat(shell): connect to a named target

Optional body explaining why and how.

Closes #1234
```

Types in use: `feat`, `fix`, `docs`, `test`, `chore`, `refactor`. Keep one logical change per commit, and use `Closes #<issue>` to link the issue.

## Documentation

Update the documentation with the change: the [table in AGENTS.md](AGENTS.md#documentation-to-keep-in-sync) lists which page goes with which kind of change. User-visible changes get an entry in the `Unreleased` section of the [CHANGELOG](CHANGELOG.md). User documentation is written in English; architecture documents (`ARCHITECTURE.md`, `TASKS.md`, `documentation/architecture/`) are written in French.

## Pull request checklist

- The pull request references an issue, or explains the problem it solves.
- The scope is limited to one change.
- `./mvnw verify` passes locally.
- Tests were added or updated.
- The documentation and the CHANGELOG were updated where needed.
- The branch is rebased on `main`.

## Releasing

Releases are built with the [Maven release plugin](https://maven.apache.org/maven-release/maven-release-plugin/) and signed with GPG: configure your signing key in Git (`git config --global user.signingkey <key id>`) and add it to your GitHub account, then:

```bash
./mvnw -Dresume=false release:prepare release:clean
git push --follow-tags
```

## Security

Do not open a public issue for a vulnerability: follow the [Security Policy](SECURITY.md).
