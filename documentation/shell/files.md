# Realm Files

The files are the only source of truth: the shell reads and writes them, nothing else. What it writes can be imported as it is with keycloak-config-cli, and you can still edit the files by hand.

## One realm, one folder

Under `shell.root`, each realm has a folder whose name you choose when you create it (`shop/`). A realm is opened by the name of its folder, not by its Keycloak name, which can contain variables.

```text
realms/
└── shop/
    ├── 00-realm.yaml
    ├── 20-clients.yaml
    ├── 30-roles.yaml
    └── 40-groups.yaml
```

| File | Content |
|------|---------|
| `00-realm` | The `realm` key and the settings of the realm |
| `20-clients` | The clients |
| `30-roles` | The realm roles and the roles of the clients |
| `40-groups` | The groups |

Every file of a realm has the same `realm` key (for example `realm: "{{env}}-shop"`), which merges them at import. The numbers follow the order of the dependencies: at import, each file is applied in the order of its name, so a group that gives a role comes after the file that defines the role.

The shell adapts to the organisation you already have. It reads every YAML and JSON file of the folder and remembers where each element comes from. An existing element is modified in its own file; a new element is added to the first file that already declares its type, or else to the standard file above, which the shell creates. The format of the files (YAML or JSON) is chosen by `realm create`; an existing file keeps its own.

## The standard layout

The shell puts new elements in the files below (it creates them when needed), and `realm reorganize` moves an existing realm to them.

| File | Content |
|------|---------|
| `00-realm` | The `realm` key and everything that is not listed below (settings, client scopes, authentication flows, and so on) |
| `20-clients` | `clients` |
| `30-roles` | `roles` |
| `40-groups` | `groups` |
| `50-links` | `users`, `scopeMappings`, `clientScopeMappings`, `defaultGroups`, `identityProviderMappers` and `defaultRole` |

`50-links` comes last for a reason: the files are applied in the order of their name, and the elements it holds cite roles, groups and clients (a user with a role, a default group, a scope mapping on a client, an identity provider mapper that gives a role or a group, the default role of the realm and its composites). A file that cites roles or groups must therefore come after the files that define them. Every file starts with the `realm` key.

A known limit: the `authorizationSettings` of a client (its resources, scopes and policies) stay in the client, in `20-clients`, but their policies may cite roles, groups or users defined in the later files. A realm that uses them may not import in this order: run a dry run after `realm reorganize` to check.

File names are compared without regard to case: `20-Clients.yaml` is the clients file and keeps its name.

## `realm format`

The shell rewrites a file entirely when it changes an element. The first rewrite of a file written by hand can change its look (a comment above a list item moves into the item, a blank line disappears, a JSON list written on one line goes on several lines), and the look is then stable. `realm format` does that rewrite once, for every file of the opened realm, without changing the content, so that you can commit the change of look apart and get readable diffs afterwards.

```text
> realm format
Formatted: 20-clients.yaml
Formatted: 30-roles.yaml
```

Before the first change of a file that is not formatted, the shell stops with `Run 'realm format' first: <file>` (exit code `2` in a script).

After that, a change only touches its own lines: comments, including the ones at the end of a line, the order of the keys, the style of the values, line endings and encoding are kept, and the keys the shell does not manage are left as they are.

## Placeholders

The shell reads and writes the source files, never the rendered ones, so the Mustache placeholders of [Templating with Mustache](../advanced/templating.md) stay as they are written: `{{appUrl}}`, `{{appUrl:https://localhost}}`, or a mix such as `{{env}}-shop`. Any text answer can use them, and the shell checks their syntax. A value containing a placeholder is written between double quotes, so that the file stays valid JSON or YAML before rendering.

`variables list` shows the variables of the realm, with their default value and the files that use them: this is what to give at import with `import.mustache.variables.*`.

A placeholder written without quotes (a number or a boolean such as `enabled: {{{on}}}`) cannot be read before rendering. The whole realm is then read-only for the shell: the message gives the file and the line to fix, and the realm can still be imported.

A client secret is always a variable without a default value, never a value written in a file. Give its value at import.

## The first declaration of a type

The import deletes what the files no longer declare, for each resource type the files declare (see [Managed Resources](../concepts/managed-resources.md)). If a realm declares no group yet and the shell writes the first one, the next import deletes all the other groups of Keycloak. Offline, the shell cannot know what Keycloak contains.

So when a command is about to declare a type for the first time, the shell says it and asks for a confirmation, defaulting to no:

```text
? At the next import, all the groups of Keycloak missing from the files will be deleted. Run a dry run first, then confirm. [y/N]:
```

In a script there is no one to ask: the command fails with exit code `2` unless you add `--declare-type`. The same applies to the clients, to the realm roles, and to the roles of each client (the import cleans the roles of each client separately). The confirmation is asked for a realm created by the shell too, since the shell cannot know whether the realm already exists in Keycloak. For an existing realm, run a [dry run](../advanced/dry-run.md) before the import. Connected to a target, the confirmation lists what the next import would delete there: see [The first declaration on a connected target](commands.md#the-first-declaration-on-a-connected-target).
