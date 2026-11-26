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

Lists the placeholders of the opened realm with their default value and the files that use them. These are the values to give at import with `import.mustache.variables.*` (see [Templating with Mustache](../advanced/templating.md)).

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
