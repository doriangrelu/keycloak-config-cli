# Interactive Shell

The shell is a guided editor for the realm definition files. It asks questions, explains the choices, and writes the clients, roles and groups of a realm in the files you later import with keycloak-config-cli. You do not need to know the export format of Keycloak: the shell translates your answers into the right keys, and applies [rules](rules.md) that keep the result safe.

The shell edits files. It connects to Keycloak only when you ask it to, with [`connect`](#connecting-to-keycloak); connected, it shows what importing the opened realm would change and imports it, see [Dry run and import](#dry-run-and-import).

## Pages

- [Commands](commands.md): every command, its questions and options, with examples of output.
- [Files](files.md): how a realm is stored, `realm format`, comments, placeholders and the first declaration of a type.
- [Rules](rules.md): the rules that set defaults and check each change, and how an organisation adds or disables rules.

## Starting the shell

```bash
java -jar keycloak-config-cli.jar --run.operation=SHELL --shell.root=./realms
```

`shell.root` is the folder that holds one folder per realm. It defaults to the working directory.

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `run.operation` | `RUN_OPERATION` | `IMPORT` | Set to `SHELL` to start the shell |
| `shell.root` | `SHELL_ROOT` | `.` | Folder holding one folder per realm |
| `shell.realm` | `SHELL_REALM` | - | Folder of a realm to open before the first command |
| `shell.target` | `SHELL_TARGET` | - | Target of `kcc-targets.yaml` to connect to before the first command, see [Connecting to Keycloak](#connecting-to-keycloak) |
| `shell.command` | `SHELL_COMMAND` | - | One command to run, then exit |
| `shell.script` | `SHELL_SCRIPT` | - | File of commands to run in one session, then exit |
| `shell.rules` | `SHELL_RULES` | - | File of the rules of your organisation, see [Rules](rules.md) |

Without `shell.command` and `shell.script`, an interactive session starts. Questions are asked on the console and an invalid answer is asked again.

## Connecting to Keycloak

The shell can connect to a Keycloak server, for the commands that work on the server. A server is a named target of the file `kcc-targets.yaml`, in `shell.root`. The file holds no secret.

```yaml
targets:
  local:
    url: http://localhost:8080
    user: admin
  dev:
    url: https://keycloak.dev.example.org
    login-realm: kcc
    client-id: kcc-dev
    grant-type: client_credentials
    ssl-verify: true
    connect-timeout: 15s
```

| Field | Default | Description |
|-------|---------|-------------|
| `url` | required | Base URL of Keycloak |
| `login-realm` | `master` | Realm the user or the client logs in to |
| `client-id` | `admin-cli` | Client used to log in |
| `grant-type` | `password` | `password` (a user and a password) or `client_credentials` (a client and its secret) |
| `user` | required for `password` | User to log in with |
| `ssl-verify` | `true` | Whether the certificate of the server is verified |
| `connect-timeout` | `10s` | How long to wait for the server to accept the connection; a unit is required (`500ms`, `10s`, `1m`). A server that accepts it but does not answer is waited for 10 seconds, or `connect-timeout` when larger |

A target name holds letters, digits, `-` and `_`. The file is read on every command, so an edit is seen without restarting the shell. An unknown field, a missing `url`, a missing `user` for the `password` grant, an invalid value, or a secret written in the file (`password`, `client-secret`, `secret`) is refused with exit code `2`, and the message names the target and the field. The file is optional: without it there is no target. A target does not use `keycloak.http-proxy`: `connect` reaches the server directly.

### The password or the secret

The password (grant `password`) or the secret of the client (grant `client_credentials`) is never read from the file:

| Environment variable | Used for |
|----------------------|----------|
| `KCC_<TARGET>_PASSWORD` | The password of the `user`, for the `password` grant |
| `KCC_<TARGET>_SECRET` | The secret of the client, for the `client_credentials` grant |

`<TARGET>` is the name of the target in upper case, with `-` replaced by `_`: the target `my-dev` reads `KCC_MY_DEV_PASSWORD`. Two names that give the same variable (`my-dev` and `my_dev`) are refused. When the variable is not set, or empty, the shell asks for the secret without echo. In [script mode](#script-mode) nothing can be asked: a missing variable fails with exit code `2`.

### Connecting

In a session, [`connect <target>`](commands.md#connect-target) opens the connection, [`disconnect`](commands.md#disconnect) closes it, and [`target list`](commands.md#target-list) shows the targets. The prompt shows the opened realm and the connected target: `kcc>`, `kcc:shop>`, `kcc@dev>`, `kcc:shop@dev>`.

For a script, `shell.target` connects before the first command. The secret comes from the environment:

```bash
KCC_DEV_SECRET=... java -jar keycloak-config-cli.jar --run.operation=SHELL --shell.root=./realms \
  --shell.target=dev --shell.command="target list"
```

A target that cannot be connected stops the launch with the exit code of the cause (`2`, `3` or `4`). In an interactive session, the secret of `shell.target` is asked on the console. The connection is closed when the shell exits.

### Dry run and import

Once connected, the shell imports the opened realm: connect, look at the changes, then import them.

```text
kcc> realm open shop
Realm 'shop' opened (2 clients, 3 roles, 1 groups)
kcc:shop> connect dev
Connected to dev (https://keycloak.dev.example.org, Keycloak 26.5.3)
kcc:shop@dev> realm dry-run
Dry run of 'shop' on dev (https://keycloak.dev.example.org)
? Value of 'shopSecret':
Realm 'shop': 1 change(s)
  + Client 'shop-web'
Dry run: 1 change(s) pending.
kcc:shop@dev> realm import
Import of 'shop' on dev (https://keycloak.dev.example.org)
Realm 'shop': 1 change(s)
  + Client 'shop-web'
? Apply these 1 change(s) to dev? [y/N]: y
Realm 'shop' imported on dev: 1 change(s) applied
kcc:shop@dev> realm dry-run
Dry run of 'shop' on dev (https://keycloak.dev.example.org)
Realm 'shop': 0 change(s)
Dry run: no change pending.
```

The values of the variables come from `--var <name>=<value>`, the session, `KCC_VAR_<name>`, the default value of the placeholder, or a question; the session remembers them until the realm or the target changes. The files imported, the options and the deletions are the ones of the command-line import. See [`realm dry-run`](commands.md#realm-dry-run) and [`realm import`](commands.md#realm-import).

In a script, give the values with `--var` or `KCC_VAR_<name>`, and `--apply` to import:

```bash
KCC_DEV_SECRET=... KCC_VAR_shopSecret=... java -jar keycloak-config-cli.jar --run.operation=SHELL \
  --shell.root=./realms --shell.realm=shop --shell.target=dev --shell.command="realm import --apply"
```

`realm dry-run` ends with exit code `10` when changes are pending, as the command-line [dry run](../advanced/dry-run.md) does.

## A session

A session works on one realm at a time. `realm open <folder>` or `realm create` opens a realm; the other commands (`client`, `role`, `group`, `variables`, `realm show`, `realm format`, `realm reorganize`) work on it. `realm list` and `rules list` need no open realm.

| Command | Purpose |
|---------|---------|
| `realm format` | Rewrite the files in the layout of the shell, without changing their content |
| `realm reorganize` | Move the elements of a realm to the standard files (`00-realm`, `20-clients`, `30-roles`, `40-groups`, `50-links`) |

A raw export of Keycloak must go through `realm format` first; see [`realm reorganize`](commands.md#realm-reorganize).

```text
> realm list
shop
> realm open shop
Realm 'shop' opened (2 clients, 3 roles, 1 groups)
> client list
shop-api  api  20-clients.yaml:2
shop-web  spa  20-clients.yaml:9
```

Answering a question:

- type the answer and press Enter;
- an answer in square brackets, such as `[shop]`, is the proposal: press Enter to take it;
- for an optional answer, Enter keeps the proposal, or gives nothing when there is none, and `-` gives nothing;
- in a numbered list, answer with the number (several numbers separated by commas when the question allows it).

Every text answer accepts a value, a Mustache placeholder (`{{appUrl}}`) or a mix (`{{env}}-shop`). See [Files](files.md).

## Script mode

For a script or a CI pipeline the shell never waits for an answer. Give the answers as options of the command: a question that is not answered takes its proposal, and fails with exit code `2` when it has none.

Open a realm and run one command:

```bash
java -jar keycloak-config-cli.jar --run.operation=SHELL --shell.root=./realms \
  --shell.realm=shop \
  --shell.command="client create --id=shop-web --type=spa --url=https://shop.example.com --declare-type"
```

Run a file of commands in one session, one command per line:

```bash
java -jar keycloak-config-cli.jar --run.operation=SHELL --shell.root=./realms \
  --shell.script=create-shop.kcc
```

The path of `shell.script` is relative to the working directory, not to `shell.root`.

```text
# create-shop.kcc
realm create --folder=shop --realm=shop --format=yaml
role create --name=customer --declare-type
client create --id=shop-web --type=spa --url=https://shop.example.com --declare-type
group create --name=customers --realm-roles=customer --declare-type
```

Empty lines and lines starting with `#` are ignored. Each command is printed after `> ` before its result. The first command that fails stops the script with its exit code and the line, for example `Stopped at line 3: client create ...`; a `realm dry-run` that finds changes (code `10`) is not a failure and does not stop it. The values of `--var` options are printed as `***`. The commands run in one session, so `realm create` opens the realm for the next ones, and `shell.realm` can open an existing one first. A line that has run stays written when a later line fails: nothing is rolled back.

`shell.command` and `shell.script` cannot be used together. A `shell.script` file that does not exist fails with exit code `2` before any command runs; a file with no command prints `No command in <file>` and succeeds.

Use `--declare-type` the first time a script writes an element of a type: see [Files](files.md#the-first-declaration-of-a-type).

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | Success, including a command that only gave advice |
| `2` | Invalid use or configuration: unknown command, option, realm, client, role, group or target; a missing answer, secret or variable value in script mode; no opened realm or no connection for an import; an invalid import file; a change refused by the [rules](rules.md); a read-only realm; an invalid rules file or `kcc-targets.yaml`; `shell.command` with `shell.script` |
| `3` | Keycloak cannot be reached or did not answer in time (`connect`, `shell.target`, `realm dry-run`, `realm import`) |
| `4` | Keycloak refused the login of the target, or an operation of an import (`connect`, `shell.target`, `realm dry-run`, `realm import`) |
| `10` | `realm dry-run` found pending changes; in a `shell.script`, the script goes on and ends with `10` when no command failed |

Other codes of [Exit Codes](../reference/exit-codes.md) apply to the cause of an unexpected failure. An invalid rules file stops the shell before the first command, whatever the command.
