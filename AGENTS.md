# AGENTS.md

Guide for AI coding agents (and humans) working on keycloak-config-cli. Read it before touching the code. Claude Code loads it through [`CLAUDE.md`](CLAUDE.md).

## The project in five lines

- A Java 25 / Spring Boot 4 command-line tool that applies realm configuration files (JSON or YAML, Keycloak export format) to Keycloak through its admin API, like `kubectl apply`.
- Version `7.0.0-SNAPSHOT`: a rewrite of the original keycloak-config-cli around a **reconciliation engine** (desired state from the files, current state from Keycloak, planned changes ordered by dependencies, dry run).
- Three operations, chosen with `run.operation`: `IMPORT` (default), `NORMALIZE` (clean a realm export), `SHELL` (interactive guided editor of realm files, Spring Shell).
- The import files are the only source of truth: for every resource type a realm's files declare, what they do not declare is deleted, except users, resources Keycloak owns and protected resources.
- Open source, Apache 2.0, hosted at <https://github.com/doriangrelu/keycloak-config-cli>.

## Commands

The Maven wrapper is included. On Windows, use `mvnw.cmd` or run `./mvnw` from Git Bash.

| Goal | Command |
|---|---|
| Format the code (required before every commit touching Java) | `./mvnw -B -q spotless:apply` |
| Build the JAR without tests | `./mvnw -B clean package -DskipTests` |
| Run some unit tests | `./mvnw -B -q test -Dtest='ClassTest,OtherTest'` |
| Run some integration tests (Docker required) | `./mvnw -B -q verify -Dtest=NoUnit -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='SomeIT' -Dfailsafe.failIfNoSpecifiedTests=false` |
| Check the layers | `./mvnw -B -q test -Dtest=ArchitectureTest` |
| Full build: format check, unit and integration tests | `./mvnw -B verify` |
| End-to-end scenarios against a real Keycloak | `./sample/scripts/run-all.sh` (Bash, Docker Compose, JAR built first) |

Notes:

- Integration tests (`*IT`) start Keycloak 26.5.3 with Testcontainers: Docker must be running (`docker info`). Another Keycloak version: `-Dkeycloak.version=<version>`.
- `spotless:check` runs during `verify` (Palantir Java Format): an unformatted file fails the build.
- The full `verify` takes long. During development, run the focused tests; run the full build once at the end of a story.
- On Windows, about 20 MockServer-based tests are known to fail locally; they pass in CI. Do not "fix" them as part of unrelated work.
- Memory is tight on developer machines: never run two Maven builds at the same time.

## Repository map

| Path | What it holds |
|---|---|
| `src/main/java/io/github/doriangrelu/keycloak/config/domain/` | Business objects (pure Java), one package per resource type, plus `reconcile` (changes, resources, deletion policy), `definition` (shell rules and realm definitions), `normalization` |
| `src/main/java/.../config/application/` | Use cases: one `*Reconciler` per resource type, the `ReconciliationEngine` (`reconcile/`), the import use case (`importation/`), ports (`*/port/`) implemented by the infrastructure |
| `src/main/java/.../config/infrastructure/keycloak/` | Keycloak admin client adapters: `*KeycloakGateway` + MapStruct `*KeycloakMapper` per type, `sdk/` (client factory, error translation) |
| `src/main/java/.../config/infrastructure/source/` | File adapters: import files, Mustache rendering, shell definitions and rules |
| `src/main/java/.../config/infrastructure/cli/` | Entry points: import and normalization runners, exit codes, Spring `@ConfigurationProperties` (`properties/`), the shell (`shell/`) |
| `src/main/resources/` | `application*.properties`, normalization baselines (`baseline/`), default shell rules (`rules/`) |
| `src/test/java/...` | Unit tests mirror the main packages; `service/*IT` are the import integration tests (base class `AbstractImportIT`); `architecture/ArchitectureTest` checks the layers |
| `src/test/resources/import-files/` | Realm files used by the integration tests, one folder per feature |
| `documentation/` | User documentation (English), index in [`documentation/README.md`](documentation/README.md) |
| `documentation/architecture/` | ADRs, EPICs and implementation plans (French) |
| `sample/` | Docker Compose Keycloak and scripted end-to-end scenarios |
| `archetype/` | Template project for a team managing its realms as code |
| `ARCHITECTURE.md`, `TASKS.md` | Index of the ADRs, index of the EPICs and their status (French) |

## Architecture rules

Dependencies point inwards: `infrastructure → application → domain`. [`ArchitectureTest`](src/test/java/io/github/doriangrelu/keycloak/config/architecture/ArchitectureTest.java) enforces it, so a violation fails the build:

- `domain` depends only on `java..` (no Spring, no Keycloak, no Jackson).
- `application` depends only on `domain`, `java..` and `org.springframework.stereotype`.
- `org.keycloak..` is used only in `infrastructure.keycloak`.
- `infrastructure.keycloak` and `infrastructure.source` do not see each other.
- `infrastructure.cli.shell` sees neither `infrastructure.source` nor `infrastructure.keycloak`: it goes through application ports.
- Only `infrastructure.cli` sees `infrastructure.cli`. No package cycles.

The reasons are in the ADRs ([`ARCHITECTURE.md`](ARCHITECTURE.md)). The ones to know before changing behaviour:

- [ADR-004](documentation/architecture/ADR-004-moteur-de-reconciliation.md): engine, plan, dependency order, one context per realm, dry run.
- [ADR-005](documentation/architecture/ADR-005-erreurs-et-codes-de-sortie.md): errors and exit codes (`0`, `1`, `2` invalid configuration, `3` Keycloak unavailable, `4` Keycloak refused, `10` dry run with pending changes).
- [ADR-007](documentation/architecture/ADR-007-fichiers-seule-verite.md): files as the only truth, single cleanup rule, Mustache as the only template engine, removed options.
- [ADR-008](documentation/architecture/ADR-008-shell-edition-des-definitions.md), [ADR-009](documentation/architecture/ADR-009-regles-de-configuration.md), [ADR-010](documentation/architecture/ADR-010-shell-mode-connecte.md): the shell, its rules, its connected mode.

### Where code goes

Adding or changing a resource type follows the existing types (`role` is the simplest reference):

1. `domain/<type>/model/`: immutable business objects, with `UnmodeledAttributes` for the Keycloak fields that are not modeled.
2. `application/<type>/`: the `*Reconciler` (what to create, update, delete, in which order) and its port (`port/*Gateway`).
3. `infrastructure/keycloak/<type>/`: the `*KeycloakGateway` implementing the port and the MapStruct `*KeycloakMapper` (ADR-006). Keycloak errors are translated by the `sdk` layer, never caught ad hoc.
4. Tests: unit tests for the reconciler and the mapper, an `*IT` in `service/` with its files in `src/test/resources/import-files/<feature>/`.
5. User documentation in `documentation/configuration/` and the [features table](documentation/reference/features.md).

## Conventions

- **Language.** Code, comments, log and error messages, user documentation, commit messages: English. Architecture documents (`ARCHITECTURE.md`, `TASKS.md`, `documentation/architecture/**`): French.
- **License header.** Every new Java file starts with the license header: copy it from a neighbouring file.
- **Formatting.** Palantir Java Format through Spotless, LF line endings everywhere (`.gitattributes`), UTF-8, 4 spaces (2 for JSON and YAML).
- **Tests first.** Write the failing test, then the code. Unit tests are `*Test` (Surefire), integration tests against Keycloak are `*IT` (Failsafe).
- **Keep behaviour.** A change that must not alter behaviour keeps the existing tests unmodified and green. The command-line import and the normalization are covered by many integration tests: do not weaken them to make a change pass.
- **Secrets.** A password, client secret or token never appears in output, logs, exception messages or written files.
- **Configuration.** Unknown `keycloak.*` and `import.*` properties are rejected at startup. A new property needs its `@ConfigurationProperties` field, its default in `application.properties`, and a line in [configuration.md](documentation/getting-started/configuration.md).
- **Commits.** Conventional style, as in the history: `feat(shell): …`, `fix(import): …`, `docs(architecture): …`, `test: …`, `chore: …`. One logical change per commit.
- **Open source.** Never mention a company, a customer or an internal project in code, documentation or commit messages. Shell targets and examples use `example.org` / `example.com` hosts, and the shell is never meant to connect to a production Keycloak.

## Development workflow: SuperPowers

Development on this project is driven by the **SuperPowers** skills (plugin `superpowers@claude-plugins-official`, enabled in [`.claude/settings.json`](.claude/settings.json)). Use them systematically; each step leaves a trace in the repository:

| Step | Skill | Output |
|---|---|---|
| Frame a feature or a behaviour change | `superpowers:brainstorming` | An ADR in `documentation/architecture/ADR-NNN-*.md` and/or an EPIC with its user stories in `documentation/architecture/epics/`, indexed in `ARCHITECTURE.md` and `TASKS.md` (French) |
| Plan a user story | `superpowers:writing-plans` | `documentation/architecture/plans/YYYY-MM-DD-epic-NNN-<topic>.md` (French): global constraints, review points, tasks with files, tests and commit messages |
| Isolate the work | `superpowers:using-git-worktrees` | One branch per story: `feature/US-NNN-NN-<topic>` |
| Implement | `superpowers:subagent-driven-development` (or `superpowers:executing-plans`) | One commit per task; briefs and reports in `.superpowers/sdd/<date>-<topic>/` (git-ignored) |
| Write code | `superpowers:test-driven-development` | Failing test first |
| Investigate a failure | `superpowers:systematic-debugging` | Root cause before any fix |
| Review | `superpowers:requesting-code-review`, `superpowers:receiving-code-review` | Findings checked against the plan's review points |
| Claim it is done | `superpowers:verification-before-completion` | Commands run and their output, not assertions |
| Finish the story | `superpowers:finishing-a-development-branch` | `./mvnw -B verify` green, sample green when user-visible, merged into `main`, a « Réalisé » section in the EPIC, status updated in `TASKS.md` |

Small documentation fixes and one-line corrections do not need the full chain, but still go through `verification-before-completion`.

## Documentation to keep in sync

| When you change… | Update |
|---|---|
| A property, its default or an environment variable | [configuration.md](documentation/getting-started/configuration.md), and `application.properties` |
| An exit code or an error | [exit-codes.md](documentation/reference/exit-codes.md), ADR-005 if the rule changes |
| Support for a Keycloak resource or field | `documentation/configuration/<type>.md`, [features.md](documentation/reference/features.md) |
| What gets deleted or protected | [managed-resources.md](documentation/concepts/managed-resources.md), [protected-resources.md](documentation/advanced/protected-resources.md) |
| A shell command, a rule or a file layout | [`documentation/shell/`](documentation/shell/README.md), and the shell scenarios of `sample/` |
| A user-visible behaviour | [CHANGELOG.md](CHANGELOG.md) (`Unreleased`), and [migration-7.md](documentation/getting-started/migration-7.md) if it breaks 6.x usage |
| An architecture decision | A new or amended ADR, `ARCHITECTURE.md`; the EPIC and `TASKS.md` for progress |
