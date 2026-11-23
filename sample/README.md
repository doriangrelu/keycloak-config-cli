# Sample: keycloak-config-cli against a real Keycloak

A throwaway Keycloak 26.5.3 in Docker, a few import files, and scripts that run the CLI and check, for each scenario, its exit code and what changed in Keycloak.

## Requirements

- Docker with Compose v2
- Bash (Linux, macOS, or Git Bash on Windows)
- The JAR of keycloak-config-cli, built from the root of the repository:

  ```bash
  ./mvnw clean package -DskipTests
  ```

The CLI runs in a container built from that JAR (`sample/Dockerfile`), so no Java is needed to run the scenarios. Keycloak is available on <http://localhost:18080> with the test account `admin` / `admin`: never reuse it elsewhere.

## Run Everything

```bash
./sample/scripts/run-all.sh          # every scenario, then Keycloak is stopped and removed
./sample/scripts/run-all.sh --keep   # same, Keycloak keeps running to look at the result
```

The run ends with the number of checks passed or failed, and exits with `0` when all of them passed.

## Run One Scenario

Start Keycloak once, then run the scenarios in order: each one starts from the state the previous one left.

```bash
./sample/scripts/00-up.sh
./sample/scripts/01-dry-run-new-realm.sh
./sample/scripts/02-import.sh
...
./sample/scripts/99-down.sh
```

| Script | What it checks |
|--------|----------------|
| `00-up.sh` | Starts Keycloak, builds the CLI image, deletes the `sample` realm of a previous run |
| `01-dry-run-new-realm.sh` | A dry run on a missing realm plans everything as creations, exits with `10` and writes nothing |
| `02-import.sh` | The first import creates clients, roles, a group and a user; template variables are rendered, default values included |
| `03-idempotent.sh` | Importing the same files again: the dry run reports no change (`0`) |
| `04-manual-change-undone.sh` | A change made by hand in Keycloak shows up in the dry run and is undone by the next import |
| `05-cleanup-and-protection.sh` | What the files no longer declare is deleted; users, built-in resources and protected resources are kept; a rule restricted to clients does not protect a role of the same name |
| `06-undefined-variable.sh` | An undefined template variable fails with `2`, names the variable, and changes nothing |
| `07-invalid-protection.sh` | An invalid regular expression in `protectedResources` fails with `2` and changes nothing |
| `08-removed-option.sh` | Options removed in 7.0.0 (`import.parallel`, `import.managed.*`) are refused at startup with `2` |
| `09-keycloak-unreachable.sh` | An unreachable Keycloak gives `3` |
| `99-down.sh` | Stops Keycloak and removes its data |

## Run the CLI by Hand

The import files of the scenarios are in `realms/`, mounted in `/config` in the CLI container. Any option of the CLI can be added after the service name:

```bash
cd sample
docker compose up -d --wait keycloak
docker compose run --rm cli \
  "--import.files.locations=/config/initial/*" \
  --import.mustache.enabled=true \
  --import.mustache.variables.appUrl=https://app.example.org \
  --import.dry-run=true
echo "exit code: $?"
```

On Git Bash, set `MSYS_NO_PATHCONV=1` first, or `/config/...` is turned into a Windows path.

| Directory | Content |
|-----------|---------|
| `realms/initial` | First version of the `sample` realm, with template variables and protection rules |
| `realms/cleanup` | Second version: a client, a role and a user are no longer declared |
| `realms/undefined-variable` | Uses a template variable without value nor default |
| `realms/invalid-protection` | A protection rule with an invalid regular expression |

Mustache renders the whole file, comments included: a placeholder written in a YAML comment must be defined too.
