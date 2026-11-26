# Shell Rules

Rules keep what the shell writes safe, for developers who do not know Keycloak well. A rule can set default values when an element is created, and checks an element before every write. The messages are written for the developer: what is wrong and why. Only the author of the rules sees the keys of Keycloak.

The shell ships built-in rules, active for everybody. An organisation adds its own and can switch a rule off with a reason that stays visible.

## What a rule does

- At **creation**, a rule sets default values for the keys the element does not have. A question of the shell on the same key proposes that value.
- **Before each write**, and when a realm is opened, the rules check the element as it will be written.
- A rule of severity `required` refuses a change that **introduces** a failure: nothing is written, the exit code is `2`. A failure that was already in the file, written by hand, is only recalled as `still to fix`: the developer is never stuck on a defect the shell cannot fix, and a change never makes things worse. For a new element, every failure is introduced by the change.
- A rule of severity `advice` warns and the element is written.
- When a realm is opened, the elements that do not meet a rule are listed with their file and line.

## The built-in rules

| Identifier | Element | Severity | What it does |
|------------|---------|----------|--------------|
| `client.spa.pkce` | client | required | A front-end application (`spa`) uses PKCE with `S256`; this is also its default |
| `client.redirect-uris.no-wildcard` | client | required | No redirect URI is `*` |
| `client.unique-identifier` | client | required | The client id is unique in the realm |
| `role.unique-identifier` | role | required | The role name is unique in the realm, or among the roles of its client |
| `group.unique-identifier` | group | required | The group path is unique in the realm |
| `group.defined-roles` | group | required | The roles of a group are defined in the realm files or created by Keycloak |

The first two are written in the file `rules/base-rules.yaml` of the tool, in the format below. The others are written in Java. `rules list` shows them all.

`group.defined-roles` checks the groups written by hand in the files, when a realm is opened. The commands `group create` and `group edit` go further: they only accept a role defined in the realm files or created by Keycloak, and refuse any other with `Role 'x' is neither defined in the realm files nor created by Keycloak`, whether the rule is disabled or not. Disabling the rule only silences its findings on the groups already in the files.

## The format of a rules file

A rules file is YAML. This file shows every field and every operator; the sections below explain them. It uses identifiers of its own, because an identifier of a built-in rule cannot be defined again: copied as it is into `shell.rules`, it is accepted.

```yaml
rules:
  # A default value and a check on the same key, for one application type.
  - id: client.spa.pkce-s256
    element: client
    when:
      application-type: spa
    defaults:
      attributes."pkce.code.challenge.method": S256
    checks:
      - key: attributes."pkce.code.challenge.method"
        equals: S256
    message: "A front-end application must use PKCE with S256: the browser cannot keep a secret."

  # A check on a list: no element of the list may match.
  - id: client.redirect-uris.no-star
    element: client
    checks:
      - key: redirectUris
        none-matches: '^\*$'
    message: "A redirect URI '*' lets any site receive the tokens. List the application's URIs."

  # A rule that only advises: the client is written anyway.
  - id: client.web.short-tokens
    element: client
    when:
      application-type: web-application
    defaults:
      attributes."access.token.lifespan": "300"
    checks:
      - key: attributes."access.token.lifespan"
        matches: '[0-9]{1,3}'
    severity: advice
    message: "Keep the access tokens of a web application below 1000 seconds."

  # present and absent take no value.
  - id: client.has-name
    element: client
    checks:
      - key: name
        present: true
    severity: advice
    message: "Give the client a name: the administrators read it in the console."

  # A rule on a list of values: every redirect URI must match the pattern.
  - id: client.redirect-uris.https-only
    element: client
    when:
      application-type: spa
    checks:
      - key: redirectUris
        matches: '^https://.*'
    message: "A front-end application must be served over HTTPS."

  # A condition on a key of the element: only the roles of a client.
  - id: role.client-role.naming
    element: role
    when:
      checks:
        - key: client
          present: true
    checks:
      - key: name
        matches: '[a-z][a-z0-9-]*'
    message: "A client role is named in lower case, with dashes."

  # A condition with absent: only the roles of the realm.
  - id: role.realm-role.not-admin
    element: role
    when:
      checks:
        - key: client
          absent: true
    checks:
      - key: name
        not-one-of: [admin, root]
    message: "A realm role named admin or root is too easy to confuse with the administration of Keycloak."

  # A check on a group: its name.
  - id: group.top-level.naming
    element: group
    checks:
      - key: name
        none-matches: '.*\s.*'
    message: "A group name has no space."

  # A rule on the realm itself.
  - id: realm.ssl-required
    element: realm
    checks:
      - key: sslRequired
        one-of: [external, all]
    message: "The realm must require HTTPS for every address that is not local."
```

| Field | Role |
|-------|------|
| `id` | Unique, stable identifier: it is used to disable the rule. Prefix it with the element (`client.`, `role.`, `group.`, `realm.`) |
| `element` | `realm`, `client`, `role` or `group` |
| `when` | Optional. When the rule applies: `application-type` (clients only) and `checks`, a list of conditions written like checks. All must be true |
| `defaults` | Optional. Keys and values set when the element is created, only for the keys it does not have |
| `checks` | Optional. Checks made before each write and when the realm is opened. All must be true |
| `severity` | `required` (default) or `advice` |
| `message` | Required. Shown to the developer |

A rule needs `defaults` or `checks`, and a `message`. A rule gives its `id` once: an identifier already used, by the tool or by another rule, is refused.

### Keys

A rule names the values by the keys of the export format of Keycloak, the ones of the definition files: `publicClient`, `redirectUris`, `attributes."pkce.code.challenge.method"`. A key is a dotted path; a segment that contains a dot is written between double quotes. Nested objects are flattened, and a list of simple values (`redirectUris`) stays a list. Lists of objects (`protocolMappers`, `composites`) cannot be addressed.

A rule can use any key of the file, even one the shell does not edit. What each element exposes:

- **client**: the keys of the client in the files, and the effective value of the five flow keys (`publicClient`, `standardFlowEnabled`, `implicitFlowEnabled`, `directAccessGrantsEnabled`, `serviceAccountsEnabled`), with Keycloak's default when the file does not write one.
- **role**: the keys of the role (`name`, `description`, `composite`...) and a synthetic key `client`, the id of the client owning the role, absent for a realm role.
- **group**: `path`, `name`, `realmRoles` (a list) and `clientRoles."<client>"` (a list per client).
- **realm**: the top-level keys of all the files of the realm folder, without the lists of elements (`clients`, `roles`, `groups`...).

### Application type

`application-type` is the one exception to keys. It is the type of the client as the shell understands it, deduced from the five flow keys: `spa`, `web-application`, `service`, `api` or `custom` (flows that match no type). Writing the flows as keys would be tedious and error prone. It is only valid in the `when` of a rule on clients, and a rule with `application-type: spa` never applies to a `custom` client.

### Operators

A check has a `key` and exactly one operator.

| Operator | Single value | List |
|----------|--------------|------|
| `equals: v` | equals `v` | not allowed on a list key such as `redirectUris` |
| `one-of: [a, b]` | is in the set | every item is in the set |
| `not-one-of: [a, b]` | is not in the set | no item is in the set |
| `matches: 'regex'` | matches the pattern | every item matches |
| `none-matches: 'regex'` | does not match | no item matches |
| `present` | the key is present | same |
| `absent` | the key is absent | same |

- Patterns are Java regular expressions applied to the whole value.
- Values are compared as text: `true`, `S256` and `3600`, and the YAML `true` and `"true"` are equal.
- `present` and `absent` take no value; `present: true` is accepted.
- An empty text or an empty list is a present key. `null` is an absent key.

### When the key is absent

| Operator | When the key is absent |
|----------|------------------------|
| `equals`, `one-of`, `matches`, `present` | false |
| `not-one-of`, `none-matches`, `absent` | true |

The same applies to the conditions of `when`. For the five flow keys of a client the value is never absent: it is the effective one, Keycloak's default when the file does not write it. For the other keys Keycloak's default is not known, so write the check with this in mind: a check `equals: S256` on a key the file does not write is false, which is why the rule above also sets the default.

### Values that cannot be verified

A value that contains a placeholder (`{{url}}`, `{{appUrl}}/*`) is not judged: the check that reads it is listed as `not checked (placeholders)`, without a refusal or a warning. The value is only known at import; a [dry run](../advanced/dry-run.md) completes the verification. `present` and `absent` are still judged, since the presence of a key does not depend on its value. A key whose shape does not fit the operator (an object where a value is expected) is also not verifiable.

### Defaults

Defaults apply when an element is created, and when a change of the application type of a client gives it the defaults of the new type for the keys it does not have. When several rules that apply set a different value for the same key, the last one wins: built-in rules, then Java rules, then the organisation's, and the order of writing within a file. Two rules of the same file that set different defaults for the same key, on the same element and the same `when`, are refused. A default that contradicts a check is not refused when the file is read; the check that follows the creation reports it.

## The rules of your organisation

Give the file to the shell with `shell.rules`, a path relative to the working directory:

```bash
java -jar keycloak-config-cli.jar --run.operation=SHELL --shell.root=./realms \
  --shell.rules=./shell-rules.yaml
```

The file is read once at start. It adds rules in the `rules` section, in the format above, and can switch rules off in the `disable` section:

```yaml
disable:
  # Switch a rule off for chosen elements, with the reason.
  - id: client.spa.pkce
    for: [legacy-portal]
    reason: "Legacy portal without PKCE support, replacement planned in 2027."
  # Without for, the rule is off for every element.
  - id: client.redirect-uris.no-wildcard
    reason: "The redirect URIs are checked by the gateway of the organisation."

rules:
  - id: org.client.description
    element: client
    checks:
      - key: description
        present: true
    severity: advice
    message: "Describe what the client is for."
```

| Field of `disable` | Role |
|--------------------|------|
| `id` | The rule to switch off: a built-in rule, a Java rule or a rule of the organisation. An unknown identifier is refused, so that a typo does not leave the rule active |
| `for` | Optional. The elements it concerns: a client id, a role name or a group path, written as in the file (an identifier with a placeholder is written with its placeholder). Without `for`, the rule is off for all elements |
| `reason` | Required. Why the rule is off |

A rule is switched off visibly: `realm open` and `rules list` show the disabled rules with their scope and their reason.

```text
Disabled rules:
  client.spa.pkce (legacy-portal): Legacy portal without PKCE support, replacement planned in 2027.
  client.redirect-uris.no-wildcard (all): The redirect URIs are checked by the gateway of the organisation.
```

The built-in rules are not replaced: they are switched off explicitly, with a reason, or they stay.

## An invalid rules file

The rules are loaded and checked when the shell starts. An invalid file stops the shell before the first command, with exit code `2` and a message that names the file, the rule and the problem, whatever the command:

```text
shell-rules.yaml: rule 'client.has-name': 'severity' must be required or advice, not 'warn'
```

These are refused: an unknown field, operator or `element`; an invalid regular expression; `application-type` on anything but a client; `equals` on a list key; a rule without `defaults` or `checks`, or without `message`; an identifier already used; a disablement without `reason`, with an empty `for` (`for: []`) or of an unknown rule; and conflicting defaults.

## Listing the rules

`rules list` prints the rules in force (identifier, element, severity, message), then the ones the organisation disabled with their reason. It needs no opened realm.
