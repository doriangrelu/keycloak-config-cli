# Interactive Shell

The shell is a guided editor for the realm definition files. It asks questions, explains the choices, and writes the clients, roles and groups of a realm in the files you later import with keycloak-config-cli. You do not need to know the export format of Keycloak: the shell translates your answers into the right keys, and applies [rules](rules.md) that keep the result safe.

The shell works on files only. It never connects to Keycloak: use a [dry run](../advanced/dry-run.md) to see what an import would change.

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
| `shell.command` | `SHELL_COMMAND` | - | One command to run, then exit |
| `shell.script` | `SHELL_SCRIPT` | - | File of commands to run in one session, then exit |
| `shell.rules` | `SHELL_RULES` | - | File of the rules of your organisation, see [Rules](rules.md) |

Without `shell.command` and `shell.script`, an interactive session starts. Questions are asked on the console and an invalid answer is asked again.

## A session

A session works on one realm at a time. `realm open <folder>` or `realm create` opens a realm; the other commands (`client`, `role`, `group`, `variables`, `realm show`, `realm format`, `realm reorganize`) work on it. `realm list` and `rules list` need no open realm.

| Command | Purpose |
|---------|---------|
| `realm format` | Rewrite the files in the layout of the shell, without changing their content |
| `realm reorganize` | Move the elements of a realm to the standard files (`00-realm`, `20-clients`, `30-roles`, `40-groups`, `50-links`) |

A raw export of Keycloak must go through `realm format` before `realm reorganize`. When several files give the same key (for example `clients`), `realm reorganize` merges them: the lists are concatenated in the order of the file names, and a merged key loses its comments. See [Commands](commands.md#realm-reorganize).

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

Empty lines and lines starting with `#` are ignored. Each command is printed after `> ` before its result. The first command that fails stops the script with its exit code and the line, for example `Stopped at line 3: client create ...`. The commands run in one session, so `realm create` opens the realm for the next ones, and `shell.realm` can open an existing one first. A line that has run stays written when a later line fails: nothing is rolled back.

`shell.command` and `shell.script` cannot be used together. A `shell.script` file that does not exist fails with exit code `2` before any command runs; a file with no command prints `No command in <file>` and succeeds.

Use `--declare-type` the first time a script writes an element of a type: see [Files](files.md#the-first-declaration-of-a-type).

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | Success, including a command that only gave advice |
| `2` | Invalid use or configuration: unknown command, option, realm, client, role or group; a missing answer in script mode; a change refused by the [rules](rules.md); a read-only realm; an invalid rules file; `shell.command` with `shell.script` |

Other codes of [Exit Codes](../reference/exit-codes.md) apply to the cause of an unexpected failure. An invalid rules file stops the shell before the first command, whatever the command.
