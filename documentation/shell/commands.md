# Shell Commands

Every command is interactive by default: it asks what its options do not give, with numbered choices that explain themselves. The same options make a command usable in [script mode](README.md#script-mode). In a script, a question without an answer takes its proposal, and fails with exit code `2` (`No answer to "...": give it as an option of the command`) when it has none.

Prompts follow the same conventions everywhere: Enter keeps the proposal shown in square brackets, `-` gives no value to an optional answer or a list, and values or numbers are separated by commas. A yes or no question accepts `y` or `yes`, `n` or `no`; the capital letter of `[Y/n]` or `[y/N]` is the default.

After an element is written, the shell says where, then what the rules did:

```text
Client 'shop-web' written to shop/20-clients.yaml
  set by the rules: attributes."pkce.code.challenge.method" = S256
  advice client.example: ...
  still to fix client.redirect-uris.no-wildcard: A redirect URI '*' lets any site receive the tokens. ...
  not checked (placeholders): client.example
```

- `set by the rules`: a default value that a [rule](rules.md) wrote.
- `advice`: a rule of severity `advice` is not met; the element is written anyway.
- `still to fix`: a required rule was already not met before your change; your change did not introduce it, so it does not block.
- `not checked (placeholders)`: the rules that could not judge a value containing a placeholder.

A required rule that your change would break refuses it: nothing is written and the exit code is `2`.

```text
The rules refuse this change:
- client.redirect-uris.no-wildcard: A redirect URI '*' lets any site receive the tokens. List the application's URIs.
```

## Realms

### `realm list`

Lists the realm folders of the root.

### `realm create`

Creates the folder of a realm with its `00-realm` file, and opens it.

| Option | Description |
|--------|-------------|
| `--folder` | The folder of the realm: letters, digits, `.`, `_` or `-`, starting with a letter or a digit and not ending with `.`. It must not exist yet |
| `--realm` | The name of the realm, placeholders allowed. Proposed: the folder name |
| `--format` | `yaml` or `json` |

```text
> realm create
? Folder of the realm: shop
? Name of the realm [shop]: {{env}}-shop
? Format of the files
  1. YAML — readable, accepts comments
  2. JSON — no comment
  Number: 1
Realm 'shop' created: shop/00-realm.yaml
```

### `realm open <folder>`

Opens a realm and reports how it conforms to the rules: the disabled rules, then the elements that do not meet a rule, by file and line.

```text
> realm open shop
Realm 'shop' opened (2 clients, 3 roles, 1 groups)
Disabled rules:
  client.spa.pkce (legacy-portal): Legacy portal without PKCE support, replacement planned in 2027.
Rule findings:
  20-clients.yaml:9 shop-web
    [required] client.redirect-uris.no-wildcard: A redirect URI '*' lets any site receive the tokens. ...
```

A realm with a file that cannot be read before rendering (an unquoted placeholder) is opened read-only: the message gives the file, the line and the reason. See [Files](files.md#placeholders).

### `realm show`

Shows the clients (with their application type), the roles (realm or client) and the groups of the opened realm, each with its file and line.

### `realm format`

Writes the files of the opened realm in the layout of the shell, without changing their content. See [Files](files.md#realm-format). It prints `Formatted: <file>` for each file it changed, or `Already formatted`.

### `realm reorganize`

Moves the elements of the opened realm to the standard files (see [Files](files.md#the-standard-layout)): a monolithic export or a realm split another way ends up in `00-realm`, `20-clients`, `30-roles`, `40-groups` and `50-links`. The realm must be formatted first, otherwise the command stops with `Run 'realm format' first: <file>`: a raw Keycloak export goes through `realm format`, then `realm reorganize`.

When a key is present in several files (for example `clients` in two files), the values are merged: lists are concatenated in the order of the file names, an item already present (the same client written in two files) is kept once, and a merged key loses its comments. A merged or moved value keeps the meaning the import gives it (`yes` stays true, `017` stays 15). The known limit of the layout (`authorizationSettings` that cite later elements) is described in [Files](files.md#the-standard-layout).

| Option | Meaning |
|--------|---------|
| `--apply` | Write the moves without asking |

The command shows the plan, then asks `Apply these moves?` (default: no). In a script, where nothing answers, the question takes its default: nothing is written and the command ends with `Nothing written: run again with --apply` and the exit code `0`.

```text
> realm reorganize
Moves planned:
  clients (2)  demo.yaml -> 20-clients.yaml
  roles  demo.yaml -> 30-roles.yaml
  groups (2)  demo.yaml -> 40-groups.yaml
  users (2)  demo.yaml -> 50-links.yaml
  demo.yaml -> 00-realm.yaml (what is left)
Comments around moved elements may be lost. Commit your files first, then run a dry run after.
? Apply these moves? [y/N]: y
Realm 'demo' reorganized: 5 files written, 0 removed
```

A realm already in the standard layout prints `Nothing to move`.

What the command never does:

- It makes no backup: commit your files first, so that the change can be reviewed and undone.
- It does not guarantee the comments: the comments around a moved element may be lost, which is why you run it without `--apply` first.
- It does not merge what cannot be merged: when two files give a different value to the same key, a file writes the same top-level key twice, or a file cannot be read, the command refuses and writes nothing.

If writing a file fails (a file locked by an editor, for example), the command stops with the exit code `2` and names the files already written: restore the folder with Git (`git restore` and `git clean` on it) before running it again. A file just written is never removed.

## Clients

### `client create`

| Option | Description |
|--------|-------------|
| `--id` | The client id, placeholders allowed |
| `--name` | The name |
| `--description` | The description |
| `--type` | `spa`, `web-application`, `service` or `api` |
| `--url` | The URL of the application (`spa` and `web-application`) |
| `--redirect-uris` | The redirect URIs, separated by commas, or `none` |
| `--web-origins` | The web origins, separated by commas, or `none` |
| `--secret` | The secret as a variable such as `{{shopSecret}}`, or `none` |
| `--declare-type` | Confirm writing the first client of the realm |

The application type is the choice that matters: it sets the flows of the client, and the [rules](rules.md) can apply to it.

| Type | What it is |
|------|------------|
| `spa` | Front-end application: runs in the browser, cannot keep a secret |
| `web-application` | Web application with a server: the secret stays on the server |
| `service` | Service to service: calls other services with its own rights, no user |
| `api` | API: receives tokens, logs nobody in |

```text
> client create
? Client id: shop-web
? Name (Enter for none): Shop
? Description (Enter for none): -
? Application type
  1. Front-end application (SPA) — runs in the browser, cannot keep a secret
  2. Web application with a server — the secret stays on the server
  3. Service to service — calls other services with its own rights, no user
  4. API — receives tokens, logs nobody in
  Number: 1
? Application URL: https://shop.example.com
? Redirect URIs [https://shop.example.com/*] (Enter keeps them, - for none):
? Web origins [https://shop.example.com] (Enter keeps them, - for none):
Client 'shop-web' written to shop/20-clients.yaml
  set by the rules: attributes."pkce.code.challenge.method" = S256
```

A front-end application has no secret: `--secret` with a `spa` is refused (`A front-end application cannot keep a secret`). For the other types the secret is asked: a variable (recommended, the value is given at import) or none, in which case Keycloak generates it. A secret is always a variable without a default value: a value written in the file is refused. In a script, a client that is not a `spa` needs `--secret`.

The first client of a realm asks for a confirmation (`--declare-type` in a script): see [Files](files.md#the-first-declaration-of-a-type).

### `client edit <clientId>`

Edits a client: every question proposes the current value. The options are those of `client create`, except `--id`, `--url` and `--declare-type`. Changing the type rewrites the flows of the client, and the defaults of the rules for the new type are applied to the keys the client does not write. A client whose flows match no type (`custom`) only has its name, description and URIs edited: `--type` and `--secret` are refused for it.

### `client list`

Lists the clients with their type and place: `shop-web  spa  20-clients.yaml:9`.

## Roles

### `role create`

Creates a realm role or a client role.

| Option | Description |
|--------|-------------|
| `--client` | The id of the client owning the role; not given for a realm role |
| `--name` | The name, placeholders allowed |
| `--description` | The description |
| `--declare-type` | Confirm writing the first role of its place (the realm, or one client) |

When the realm has clients, the question `Role of` offers the realm or each client. The roles of the clients that Keycloak creates itself (`realm-management`, `account`...) are not edited by the shell.

```text
> role create
? Role of
  1. The realm — a role every client can give (current)
  2. Client 'shop-web' — a role of this client
  Number:
? Name: customer
? Description (Enter for none): A person who buys on the shop
Role 'customer' written to shop/30-roles.yaml
```

### `role edit <name>`

Edits the description of a role; the current one is proposed. Give `--client` for a client role. A role is not renamed (`--name` is refused): create a new role and update the groups.

### `role list`

Lists the realm roles first, then the roles of each client: `customer  realm  30-roles.yaml:3`.

## Groups

### `group create`

| Option | Description |
|--------|-------------|
| `--name` | The name, placeholders allowed; it cannot contain `/` |
| `--parent` | The path of the parent group, such as `/team`; not given for a top-level group |
| `--realm-roles` | The realm roles to give, separated by commas; `none` for no role |
| `--client-roles` | The client roles to give as `client/role`, separated by commas; `none` for no role |
| `--declare-type` | Confirm writing the first group of the realm |

When groups exist, the question `Parent group` offers `None` (a top-level group) or each group by its path. Roles are chosen in a numbered list, with the numbers separated by commas. The list shows the roles defined in the realm files first, then the ones Keycloak creates, marked `(Keycloak)`. The shell only gives a role it knows: a role that exists nowhere (neither in the files nor in Keycloak) is refused with `Role 'x' is neither defined in the realm files nor created by Keycloak`, before any rule runs. The rule `group.defined-roles` checks the groups written by hand, when the realm is opened; disabling it does not allow `group create` to give an unknown role.

```text
> group create
? Name: customers
? Parent group
  1. None — a top-level group (current)
  2. /team — a subgroup of this group
  Number:
? Roles
  1. customer — realm
  2. offline_access — realm (Keycloak)
  Numbers (Enter for none): 1
Group '/customers' written to shop/40-groups.yaml
```

### `group edit <path>`

Edits the roles of a group, given by its path such as `/team/admins`; the current roles are proposed, and `--realm-roles` and `--client-roles` replace the current ones. A group is not renamed or moved (`--name` and `--parent` are refused), because its members would lose it at the next import.

### `group list`

Lists the groups as a tree, with their roles and place:

```text
team  [realm: customer]  40-groups.yaml:2
  admins  [shop-web: manager]  40-groups.yaml:6
```

## Variables

### `variables list`

Lists the placeholders of the opened realm with their default value and the files that use them. These are the values to give at import with `import.mustache.variables.*` (see [Templating with Mustache](../advanced/templating.md)), or to [`realm dry-run` and `realm import`](#the-values-of-the-variables) in the shell.

```text
> variables list
env  default: dev  00-realm.yaml
shopSecret  no default  20-clients.yaml
```

`defaults differ: a, b` shows a variable that is given different default values in different places.

## Rules

### `rules list`

Lists the rules in force, one per line (identifier, element, severity, message), then the rules the organisation switched off, with their reason. It needs no opened realm. See [Rules](rules.md).

```text
> rules list
client.spa.pkce  client  required  A front-end application must use PKCE with S256: the browser cannot keep a secret.
client.redirect-uris.no-wildcard  client  required  A redirect URI '*' lets any site receive the tokens. List the application's URIs.
client.unique-identifier  client  required  Another client of the realm already has this clientId.
role.unique-identifier  role  required  Another role of the realm (or of the same client) already has this name.
group.unique-identifier  group  required  Another group of the realm already has this path.
group.defined-roles  group  required  A group gives a role that is defined neither in the realm files nor by Keycloak.
```

The last four rules are built into the tool and written in Java: they keep identifiers unique and the roles of a group defined. They can be disabled like any other rule.

## Connecting to Keycloak

These commands link the shell to a Keycloak server declared in `kcc-targets.yaml`. They need no opened realm. See [Connecting to Keycloak](README.md#connecting-to-keycloak) for the file and the environment variables.

### `connect <target>`

Opens a connection to the named target and checks it with a first call, which reads the version of Keycloak. The password of the user (or the secret of the client, for the `client_credentials` grant) is read from `KCC_<TARGET>_PASSWORD` (or `KCC_<TARGET>_SECRET`) when that variable is set, and asked without echo otherwise. The name of the target is written in upper case and `-` becomes `_`: the target `my-dev` reads `KCC_MY_DEV_PASSWORD`.

```text
kcc> connect dev
? Secret of client 'kcc-dev' (or KCC_DEV_SECRET):
Connected to dev (https://keycloak.dev.example.org, Keycloak 26.5.3)
kcc@dev>
```

The shell holds one connection at a time. Connecting to another target opens the new connection first and closes the previous one only when the new one is open: if the new connection fails, the previous one stays. The connection is closed when the shell exits. The secret is never printed, logged or written.

| Failure | Exit code |
|---------|-----------|
| Unknown target, invalid `kcc-targets.yaml`, or a secret missing in [script mode](README.md#script-mode) | `2` |
| Keycloak cannot be reached (no connection within `connect-timeout`) or does not answer | `3` |
| Keycloak refuses the login (wrong password or secret, disabled account or client) | `4` |

### `disconnect`

Closes the connection to the current target.

```text
kcc@dev> disconnect
Disconnected from dev
kcc> disconnect
Not connected: use 'connect <target>'
```

### `target list`

Lists the targets of `kcc-targets.yaml` in the order of the file: the name, the URL and the grant type, then `(connected)` for the target the shell is connected to.

```text
kcc> target list
local  http://localhost:8080  password
dev  https://keycloak.dev.example.org  client_credentials  (connected)
```

Without a file, it says `No target in kcc-targets.yaml`.

## Importing the opened realm

These commands import the opened realm on the connected target: they need an opened realm (`No realm opened: use 'realm open <folder>'`) and a connection (`Not connected: use 'connect <target>'`), both with exit code `2`. The files imported are the ones the command-line import would read with `import.files.locations=<folder of the realm>`: hidden files are skipped, the files are read in name order, and the import runs with the default import options (`import.validate`, `import.behaviors.*`), which the shell does not let change. As with the command-line import, what the files of the realm no longer declare is deleted from Keycloak.

| Option | Meaning |
|--------|---------|
| `--var <name>=<value>` | The value of a variable of the realm; repeat the option for each variable. The value may contain `=`. A name that is empty or has no `=` fails with `Invalid --var '<name>': expected name=value` and exit code `2` |

### The values of the variables

Each [placeholder](../advanced/templating.md) of the realm takes its value from, in this order:

1. the `--var <name>=<value>` option of the command;
2. the value the session remembers (see below);
3. the environment variable `KCC_VAR_<name>`, with the name as written in the placeholder (`{{shopSecret}}` reads `KCC_VAR_shopSecret`);
4. the default value written in the placeholder, `{{name:default}}`;
5. otherwise the question `Value of '<name>'`, asked without echo when the name contains `secret` or `password` (any case).

In [script mode](README.md#script-mode) nothing can be asked: a variable without a value fails with `Variable '<name>' has no value: give it with --var <name>=… or KCC_VAR_<name>` and exit code `2`, before anything is sent to Keycloak.

The session remembers the values given with `--var` and the answers to the questions, in memory only, so that `realm import` after `realm dry-run` does not ask again. They are forgotten by `realm open`, `realm create`, `connect`, `disconnect` and when the shell exits. A value is never printed, logged or written: the plan names the changed fields, never their values, and a script prints `--var <name>=***` when it echoes its commands.

### `realm dry-run`

Shows what importing the opened realm would change on the connected target, without writing anything: a realm that does not exist yet is not created. The report is the one of the command-line [dry run](../advanced/dry-run.md): `+` for a creation, `~` for an update with its changed fields, `-` for a deletion.

```text
kcc:shop@dev> realm dry-run --var shopSecret=...
Dry run of 'shop' on dev (https://keycloak.dev.example.org)
Realm 'shop': 2 change(s)
  + Client 'shop-web'
  ~ Client 'shop-api' (redirectUris)
Dry run: 2 change(s) pending.
```

In script mode the command ends with exit code `10` when changes are pending, `0` otherwise. In a `shell.script`, the code `10` does not stop the script: the script goes on, and ends with `10` when no command failed and a dry run found changes. In an interactive session, pending changes are not an error.

### `realm import`

Imports the opened realm on the connected target. The command first computes the changes, as `realm dry-run` does, and shows them. With no change, it prints `Nothing to import` and writes nothing. Otherwise it asks `Apply these <n> change(s) to <target>?` (default: no), then applies the same rendered files.

| Option | Meaning |
|--------|---------|
| `--var <name>=<value>` | As for `realm dry-run` |
| `--apply` | Apply the changes without asking |

```text
kcc:shop@dev> realm import
Import of 'shop' on dev (https://keycloak.dev.example.org)
? Value of 'shopSecret':
Realm 'shop': 1 change(s)
  + Client 'shop-web'
? Apply these 1 change(s) to dev? [y/N]: y
Realm 'shop' imported on dev: 1 change(s) applied
```

Answering no prints `Nothing written`. In a script, where nothing answers, the question takes its default: nothing is written, the command ends with `Nothing written: run again with --apply` and exit code `0`.

| Failure | Exit code |
|---------|-----------|
| No opened realm, no connection, an invalid `--var`, a variable without value in script mode, or a file that is not a valid import file | `2` |
| Keycloak cannot be reached or does not answer in time | `3` |
| Keycloak refuses the account of the target, or an operation of the import (missing rights) | `4` |
