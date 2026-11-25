# EPIC-007 — Shell interactif : édition guidée des définitions de realm

**Statut :** En cours
**ADR :** [ADR-008](../ADR-008-shell-edition-des-definitions.md), [ADR-001](../ADR-001-architecture-en-couches.md), [ADR-007](../ADR-007-fichiers-seule-verite.md)

Donner aux développeurs qui ne connaissent pas bien Keycloak un shell, sur le modèle de la console Doctrine / Symfony, pour créer et modifier les éléments d'un realm par des questions guidées. Le résultat est écrit dans les fichiers de définition, en JSON ou en YAML, sans abîmer ce qui est écrit à la main, avec des variables Mustache, et en respectant des règles de configuration minimale.

Première version **hors ligne** (fichiers seulement), pour les **clients**, les **rôles** et les **groupes**.

## Exemple de session visé

```text
$ java -jar keycloak-config-cli.jar --run.operation=SHELL --shell.root=./realms
kcc> realm create
? Nom du dossier : mon-realm
? Nom du realm (valeur, {{variable}} ou valeur composée) : {{env}}-mon-realm
? Format des fichiers :
  > YAML
    JSON
Realm 'mon-realm' créé : mon-realm/00-realm.yaml
kcc:mon-realm> client create
? Identifiant du client : frontend
? Quel type d'application ?
  > Application front-end (SPA) — tourne dans le navigateur, ne peut pas garder de secret
    Application web avec serveur — le secret reste côté serveur
    Service à service — appelle d'autres services avec ses propres droits, sans utilisateur
    API — reçoit des jetons, ne connecte personne
? URL de l'application : {{appUrl}}
Client 'frontend' ajouté à mon-realm/20-clients.yaml (PKCE S256, URIs de redirection {{appUrl}}/*)
kcc:mon-realm> variables list
appUrl   sans défaut   20-clients.yaml
env      sans défaut   00-realm.yaml, 20-clients.yaml
```

Les libellés et les questions exactes se fixent dans chaque story.

## Stories

### US-007-01 — Socle Spring Shell

**Statut :** Terminé
Spring Shell 4.0.x dans `infrastructure.cli.shell`. `run.operation=SHELL`, dossier racine `shell.root` (répertoire courant par défaut). Session interactive et exécution d'une commande sans session, avec les codes de sortie de l'ADR-005. Port `Questions` (saisie, choix commenté, confirmation) dans `application.definition` et son implémentation Spring Shell ; en mode non interactif, une question sans réponse fournie en option est une erreur de configuration (code `2`). Aide des commandes. Règle ArchUnit : `infrastructure.cli.shell` ne dépend ni de `infrastructure.source` ni de `infrastructure.keycloak`. Les lanceurs existants (`ImportRunner`, `NormalizationRunner`) ne changent pas.

### US-007-02 — Lecture d'un dossier de realm

**Statut :** Terminé
Modèle de définition dans `domain.definition` (`ClientDefinition`, `RoleDefinition`, `GroupDefinition`, `TemplatedValue`) ; port `RealmDefinitions` et son adapter dans `infrastructure.source.definition`. Chargement de tous les fichiers d'un dossier (JSON et YAML, sans rendu Mustache), avec l'origine de chaque élément (fichier, position). Traduction des clés du format d'export vers le modèle ; type d'application déduit des options, « personnalisé » sinon. Type d'application déduit strictement selon le tableau de l'ADR-008. Un fichier non analysable avant rendu (placeholder sans guillemets) met tout le realm en lecture seule, avec le fichier et la ligne à corriger. Rôles et clients créés par Keycloak connus par le realm de référence de la version (`baseline/<version>`). Commandes `realm list` (les dossiers de la racine), `realm open <dossier>`, `realm show` (les éléments par type, avec leur fichier).

### US-007-03 — Écriture des fichiers et `realm format`

**Statut :** Terminé
Réécriture d'un fichier depuis son arbre : SnakeYAML avec commentaires pour le YAML, Jackson indenté pour le JSON. Modification d'un élément dans son fichier, en ne touchant que les clés gérées par le modèle ; ajout d'un élément dans le fichier qui contient déjà son type, ou création du fichier numéroté standard (`20-clients`, `30-roles`, `40-groups`) avec la clé `realm` du dossier. `realm format` : réécrit tous les fichiers d'un realm jusqu'à stabilité, sans changer le fond (vérifié par comparaison des contenus analysés avant et après). Détection d'un fichier pas encore au format avant sa première modification, avec proposition de `realm format` (refus avec le code `2` en mode non interactif). Première déclaration d'un type dans un realm : avertissement sur les suppressions qu'en fera l'import, confirmation, et refus en mode non interactif sans `--declare-type`. Tests sur des fichiers commentés, avec placeholders, dans les deux formats : après `realm format`, une modification ne produit que ses propres lignes de diff.

### US-007-04 — Création d'un realm

**Statut :** Terminé
`realm create` : nom du dossier, nom du realm (valeur, variable ou valeur composée), format. Écrit `00-realm` avec la clé `realm`, `enabled: true` et les valeurs par défaut des règles du realm, s'il y en a. Refuse un dossier déjà existant. Ouvre le realm créé.

### US-007-05 — Variables

**Statut :** Terminé
`TemplatedValue` : reconnaissance des placeholders dans une saisie (`{{nom}}`, `{{nom:défaut}}`, valeurs composées), validation de la syntaxe (accolades, nom valide, pas de triple moustache), écriture en chaîne entre guillemets. Utilisée par toutes les questions. `variables list` : nom, valeur par défaut, fichiers où chaque variable apparaît ; signale une même variable avec deux valeurs par défaut différentes.

### US-007-06 — Règles de configuration

**Statut :** Terminé
Formalisme et moteur décidés par l'[ADR-009](../ADR-009-regles-de-configuration.md) : règles YAML sur les clés du format d'export (plus le type d'application d'un client), opérateurs `equals`, `one-of`, `not-one-of`, `matches`, `none-matches`, `present`, `absent`, gravités `required` et `advice`, valeurs par défaut sur toute clé. Moteur dans `domain.definition.rules`, évalué sur la vue des clés fournie par le port `RealmDefinitions` ; seule une non-conformité introduite par une modification bloque l'écriture. Règles de base embarquées (`rules/base-rules.yaml`, avec les deux règles d'exemple actives : PKCE S256 pour un front-end, pas d'URI de redirection `*` seule) ; fichier de l'organisation donné par `shell.rules`, qui ajoute des règles et en désactive par identifiant avec une raison. Règles Java sur la même interface, dont la cohérence du realm (identifiant unique, rôles et groupes référencés définis ou créés par Keycloak, syntaxe des placeholders). Non-conformités et désactivations affichées à l'ouverture du realm ; commande `rules list`. Les règles de durcissement réelles seront fournies plus tard (dont le retrait de `offline_access` et `uma_authorization` des rôles par défaut du realm, en Java).

### US-007-07 — Clients

**Statut :** Terminé
`client create` et `client edit <id>` guidés : identifiant, nom, description, type d'application (choix commentés), URL de l'application, URIs de redirection et origines web proposées à partir de l'URL. Les options Keycloak découlent du type et des règles (client public ou confidentiel, flows, compte de service, PKCE) ; le secret d'un client confidentiel est une variable, jamais une valeur en dur proposée par le shell. En modification, les valeurs actuelles sont proposées par défaut, et un client « personnalisé » n'est modifiable que dans ses champs communs. `client list`.

Décisions de conception (25/11/2026, validées avec l'utilisateur) :

- **Questions de `client create`**, dans l'ordre : identifiant, nom (vide pour aucun), description (vide pour aucune), type d'application (quatre choix commentés), puis selon le type :
  - SPA : URL de l'application, URIs de redirection proposées `<url>/*`, origines web proposées `<url>` ;
  - application web avec serveur : URL, URIs de redirection proposées `<url>/*`, pas d'origine web proposée ;
  - service, API : ni URL ni URIs.
  L'URL ne sert qu'à proposer les URIs : elle n'est pas écrite.
- **Secret** (application web, service, API) : le shell demande « une variable (recommandé) » ou « aucun, Keycloak le génère ». La variable proposée est `{{<identifiant en camelCase>Secret}}` (`my-api` → `{{myApiSecret}}`) ; un secret écrit en dur est refusé. Sans secret écrit, l'import garde celui que Keycloak a généré.
- **Après l'écriture**, le shell affiche le fichier écrit, les valeurs posées par les règles, les avertissements (`advice`), les non-conformités déjà présentes qui restent à corriger, et une ligne pour les valeurs non vérifiables. Une règle qui refuse la modification : messages affichés, rien n'est écrit, la commande s'arrête (code 2 en mode script) ; la question n'est pas reposée.
- **Première déclaration des clients** dans un realm : avertissement et confirmation en session ; en mode script, refus sauf `--declare-type`.
- **`client edit <id>`** : les valeurs actuelles sont proposées ; l'URL n'est pas redemandée (les URIs le sont directement). Changer le type réécrit les flows et applique les valeurs par défaut des règles du nouveau type. Un client « personnalisé » ne se voit proposer que le nom, la description, les URIs et les origines web ; `--type` y est refusé.
- **Mode script** : `--id`, `--name`, `--description`, `--type` (`spa`, `web-application`, `service`, `api`), `--url`, `--redirect-uris` et `--web-origins` (séparées par des virgules), `--secret` (une variable ou `none`), `--declare-type`.
- **`client list`** : une ligne par client, `clientId`, type, `fichier:ligne`.
- Les questions sont posées par les cas d'usage `CreateClient` et `EditClient` à travers le port `Questions` (ADR-008) ; les commandes du shell restent minces.

### US-007-08 — Rôles

**Statut :** À faire
`role create` et `role edit` : rôle de realm ou rôle d'un client défini dans le dossier (choisi dans une liste), nom, description. `role list`.

### US-007-09 — Groupes

**Statut :** À faire
`group create` et `group edit` : nom, groupe parent (choisi parmi les groupes du dossier), rôles de realm et de clients affectés, choisis parmi ceux définis dans le dossier. `group list` (arborescence).

### US-007-10 — Documentation et sample

**Statut :** À faire
Documentation utilisateur (en anglais) : lancement du shell, commandes, variables, `realm format`, règles. Scénarios du sample (`sample/`) : créer un realm et ses éléments avec le shell en mode non interactif, puis importer le résultat dans Keycloak et vérifier.

### US-007-11 — Réorganisation d'un realm

**Statut :** À faire (optionnelle, peut passer dans une EPIC suivante)
`realm reorganize` : redécoupe un dossier selon la numérotation standard (pour un export Keycloak monolithique), montre d'abord ce qui sera déplacé, ne garantit pas les commentaires.

## Hors périmètre

- Mode connecté : import, dry-run et complétion depuis un Keycloak, depuis le shell, et extraction d'un realm existant en fichiers JSON ou YAML découpés par type. À cadrer dans une EPIC suivante.
- Autres types d'éléments, dans l'ordre de priorité donné : client scopes, policies, flows, fournisseurs d'identité, protocol mappers, attributs utilisateur personnalisés, thèmes, politique de brute force, options de connexion, required actions.
- Suppression d'éléments par le shell.
- Nombres, booléens et fragments variables (placeholders sans guillemets) dans les fichiers modifiés par le shell.

## Points de vigilance

- **Les fichiers restent la seule vérité** (ADR-007) : le shell n'écrit que des fichiers importables tels quels, et une retouche à la main reste possible.
- **Aucune logique dans l'adapter** : l'enchaînement des questions est dans les cas d'usage, à travers le port `Questions` ; c'est ce qui les rend testables sans terminal.
- **Ne jamais perdre une clé écrite à la main** : un élément modifié garde les clés que le modèle ne gère pas.
- **Diffs lisibles** : en dehors de `realm format`, une modification ne change que ses propres lignes.
- **Suppressions à l'import** : déclarer un type pour la première fois dans un realm fera supprimer, à l'import, tout ce que Keycloak contient de ce type hors des fichiers ; le shell doit le dire avant de l'écrire.
- **Plusieurs clients, plusieurs types** : le type d'application est propre à chaque client, jamais au realm.

## Réalisé

### US-007-01

- Spring Shell 4.0.4, cœur seul (sans JLine). `run.operation=SHELL` ; `shell.root` (répertoire courant par défaut) et `shell.command` (`ShellProperties`).
- `ShellLauncher`, déclaré sous le nom `springShellApplicationRunner` : avec `shell.command`, exécute cette commande une fois (`CommandParser`, `CommandExecutor`) et rend son code de sortie ; sans, lance la session interactive de Spring Shell. Une commande inconnue ou en échec rend `2`, ou le code de sa cause (ADR-005).
- Hors du mode SHELL, `CommandLineConfiguration` déclare un `springShellApplicationRunner` qui ne fait rien : sans lui, l'auto-configuration de Spring Shell lancerait un shell interactif après un import ou une normalisation, et une CI attendrait une commande sur l'entrée standard. `ShellLauncherIT` le vérifie pour les trois opérations.
- Port `Questions` (`application.definition.port`) : réponse libre, choix numéroté avec son explication (`Choice`), oui ou non. `ConsoleQuestions` les pose sur la console à travers l'`InputReader` de Spring Shell, redemande une réponse invalide, et lève `MissingAnswerException` (code `2`) quand il n'y a aucune réponse, comme en mode script. Les choix sont numérotés : la sélection aux flèches demanderait JLine, et pourra venir plus tard.
- Règle ArchUnit `shellIgnoresTheOtherAdapters` : `infrastructure.cli.shell` ne dépend ni de `infrastructure.source` ni de `infrastructure.keycloak`.

### US-007-02

- Modèle de définition dans `domain.definition` : `ClientDefinition` (type d'application dans `applicationType`, déduit strictement de ses flows par `ApplicationType.of(ClientFlows)` selon le tableau de l'ADR-008, `CUSTOM` sinon ; une clé absente vaut la valeur par défaut de Keycloak, `ClientFlows.KEYCLOAK_DEFAULTS`), `RoleDefinition`, `GroupDefinition` (par chemin), `ElementDefinition` scellée, `RealmDefinition` (origine de chaque élément, types déclarés, fichiers illisibles). `TemplatedValue` garde la valeur telle qu'écrite et vérifie ses placeholders (`{{nom}}`, `{{nom:défaut}}`, valeurs composées ; `{{{…}}}` refusé).
- Lecture avant rendu (`infrastructure.source.definition`) : `DocumentTrees` lit un fichier en arbre, SnakeYAML avec commentaires ou Jackson avec la ligne de chaque objet, derrière une même interface `TreeObject`. Un placeholder hors guillemets rend le fichier illisible, avec sa ligne : il est cherché ligne par ligne hors chaînes et commentaires, car en YAML `{{{x}}}` s'analyse sans erreur comme des dictionnaires imbriqués.
- `RealmDefinitionFiles` implémente le port `RealmDefinitions` : un dossier par realm sous `shell.root`, ses fichiers YAML et JSON lus par ordre de nom ; un fichier illisible met le realm en lecture seule sans empêcher la lecture des autres. Un client donné par son seul nom est connu par ce nom.
- Commandes `realm list`, `realm open <dossier>`, `realm show` sur le cas d'usage `ReadRealms` ; `ShellSession` garde le realm ouvert. Les adapters sont assemblés dans `infrastructure.cli` (`ShellAdaptersConfiguration`) : le shell ne voit que l'application.
- Report : les rôles et clients créés par Keycloak (realm de référence `baseline/<version>`) viendront avec la cohérence du realm (US-007-06) et les groupes (US-007-09), leurs premiers utilisateurs.

### US-007-03

- Réécriture depuis l'arbre (`DocumentTree.render`) : SnakeYAML avec ses commentaires, Jackson indenté avec une valeur de tableau par ligne ; le séparateur de lignes du fichier (CRLF ou LF) et l'UTF-8 sont conservés.
- `realm format` (cas d'usage `FormatRealm`) réécrit chaque fichier jusqu'à stabilité (quatre passes au plus), vérifie que le contenu n'a pas changé (`PlainContent`) et liste les fichiers modifiés ; il refuse un realm en lecture seule. Le port `RealmDefinitions` dit aussi quels fichiers ne sont pas encore au format.
- Enregistrement d'un élément (`RealmDefinitions.save`) : dans son fichier, à sa position (ligne et colonne, pour distinguer deux éléments d'une liste écrite sur une ligne, et pour permettre un renommage), sinon dans le premier fichier qui déclare son type (`fileOf`), sinon dans le fichier numéroté standard, créé avec la clé `realm` et le format du realm. Seules les clés du modèle sont écrites ; une valeur dont le texte ne change pas n'est jamais réécrite, quel que soit son type, et une valeur réécrite garde son commentaire de fin de ligne ; un placeholder est écrit entre guillemets doubles. Un flow n'est écrit que si sa valeur effective (écrite, ou par défaut dans Keycloak) diffère de celle du type ; `bearerOnly` n'est jamais écrit. Un flow écrit avec un placeholder (ou autrement qu'en booléen) rend le client « personnalisé », et n'est jamais réécrit.
- Cas d'usage `SaveElement` : refuse un realm en lecture seule, un fichier cible pas encore au format (« Run 'realm format' first »), et la première déclaration sans confirmation. La première déclaration suit le grain de l'import : les clients, les groupes, les rôles du realm, et les rôles de chaque client à part (`RealmDefinition.declares`), car l'import nettoie les rôles de chaque client séparément.
- Revue de la branche par un relecteur indépendant : cinq points importants corrigés, chacun par un test d'abord en échec (première déclaration des rôles, éléments sur une même ligne, flow variable, `run.operation=shell` sensible à la casse, nombres et booléens réécrits en chaînes). Points mineurs reportés : `realm format` non atomique sur plusieurs fichiers, certains refus sortant en code `1` au lieu de `2`, BOM UTF-8 en JSON, précision des décimaux JSON, suppression d'un attribut ou déplacement d'un élément (latents tant qu'aucune commande ne le fait).

### US-007-05

- Réalisée avant US-007-04, qui en a besoin pour demander le nom du realm.
- `Questions.askValue` : une valeur en dur, une variable ou une valeur composée ; un placeholder invalide est expliqué et redemandé en session, et fait échouer la commande en mode script.
- `variables list` (cas d'usage `ListVariables`, port `RealmDefinitions.sources`) : chaque variable du realm ouvert, sa valeur par défaut et ses fichiers, triée par nom (`Variables.of`, `VariableUse`). Le texte des fichiers est lu tel qu'écrit, commentaires compris, puisque Mustache rend aussi les commentaires à l'import. Une même variable avec deux valeurs par défaut différentes est signalée (`defaults differ`) ; avec et sans valeur par défaut, elle ne l'est pas. Un placeholder invalide est ignoré : `realm open` signale déjà le fichier.
- Exécutée par des sous-agents (implémenteur et relecteur de tâche sur un modèle standard). Points mineurs reportés : double lecture des fichiers par `ListVariables`, tests absents pour les formats de sortie `default:` et `defaults differ:` et pour un placeholder invalide ignoré, valeur par défaut contenant `{` ou `}` non reconnue.

### US-007-04

- `realm create [--folder] [--realm] [--format yaml|json]` (cas d'usage `CreateRealm`, port `RealmDefinitions.createRealm`) : un nouveau dossier de realm sous la racine, avec son fichier `00-realm` au format choisi (YAML proposé en premier), puis le realm est ouvert.
- Le nom du realm est demandé avec `askValue` : une valeur en dur, une variable ou une valeur composée (`{{env}}-mon-realm`).
- Le dossier est vérifié dès qu'il est connu, avant les autres questions (`CreateRealm.checkFolder`) : nom de dossier portable, pas de point final, et aucun dossier existant, même avec une autre casse. En session, un dossier refusé est expliqué et redemandé ; passé avec `--folder`, il fait échouer la commande (code 2) sans rien écrire. L'adaptateur crée le dossier de façon atomique (`Files.createDirectory`) et ne touche jamais un dossier ou un fichier existant.
- Un format autre que `yaml` ou `json` (casse ignorée) fait échouer la commande (code 2).
- Exécutée par des sous-agents ; la revue finale de la branche (US-007-04 et 05) a relevé un point important, la vérification tardive du dossier, corrigé avant la fusion. Points mineurs reportés : `variables list` affiche la valeur par défaut d'une variable qu'un autre fichier utilise sans valeur par défaut, placeholders rendus par l'import mais ignorés par le shell, cas limites de l'affichage (`default: ` vide, valeurs contenant `, `), tests de l'affichage de `variables list` et du code de sortie des échecs de `realm create`, dossier vide laissé si l'écriture du fichier du realm échoue, noms réservés de Windows (`con`, `nul`…) refusés par le système avec le code 1, double lecture et double résolution de chemin.

### US-007-06

- Moteur de règles pur dans `domain.definition.rules` (ADR-009) : chemins de clés (`KeyPath`, segments à points entre guillemets), vue à plat (`ElementKeys`, valeurs `Scalar`, `Listed`, `Shapeless`), vérifications à un opérateur (`Check`), règles déclaratives (`DeclarativeRule`) et règles Java sur la même interface (`Rule`), ensemble des règles avec désactivations (`RuleSet`, `Disablement`), moteur (`RuleEngine`) : valeurs par défaut, verdict d'une écriture, rapport d'un élément.
- Seule une non-conformité `required` introduite par la modification bloque l'écriture ; une non-conformité déjà présente est rappelée. Les valeurs avec placeholder sont « non vérifiables ». Un objet à la place d'une valeur est présent mais non vérifiable.
- Un seul calcul du type d'application pour les règles et le modèle d'édition : un flow qui n'est pas un booléen littéral rend le client `CUSTOM` ; `True` vaut `true`.
- Fichiers de règles lus par `infrastructure.source.rules` : `rules/base-rules.yaml` embarqué (`client.spa.pkce`, `client.redirect-uris.no-wildcard`, actives pour tous) et fichier de l'organisation (`shell.rules`), lus sans construction d'objets (arbre YAML), clés en double refusées, valeurs gardées telles qu'écrites. Les conditions de `when` sur des clés s'écrivent dans une liste `checks`. Une désactivation sans raison, d'un identifiant inconnu ou avec un `for` vide est refusée.
- Le port `RealmDefinitions` fournit la vue des clés (`preview`, `elementKeys`, `realmKeys`) avec la même traduction que l'écriture, et écrit les clés posées par les règles (booléens et entiers sans guillemets, jamais d'écrasement).
- Cohérence du realm en règles Java : `client.unique-identifier`, `role.unique-identifier`, `group.unique-identifier`, `group.defined-roles` (rôles et clients créés par Keycloak lus dans `baseline/<version>`, `default-roles-<realm>` compris ; non vérifiable si le nom du realm est variable).
- `SaveElement` applique les valeurs par défaut à la création et au changement de type d'un client, refuse (`RuleViolationException`, code 2) ou écrit ; `CreateRealm` vérifie la vue du nouveau realm ; `ReadRealms.check` donne les non-conformités et les désactivations.
- Shell : règles chargées au démarrage (fichier invalide → code 2), désactivations et non-conformités affichées à `realm open`, commande `rules list`.
- Exécutée par des sous-agents (implémenteur et relecteur par tâche, revue finale de la branche). La revue finale a relevé trois points importants, corrigés avant la fusion : clé objet vue comme absente, type d'application calculé différemment par les règles et par le modèle, `for: []` qui désactivait la règle pour tous. Points mineurs reportés : pas de test d'écriture de bout en bout avec l'adaptateur réel, erreurs de `RuleSet` sans nom de fichier, règles Java en liste fixe plutôt que beans collectés, `rules list` qui range une règle désactivée pour tous parmi les actives, client écrit avec `name` seul invisible des règles d'identifiant, session ouverte avant la vérification à `realm open`, flow écrit comme une liste ou un objet lu différemment par le modèle et par les règles, double lecture du dossier à l'ouverture, cache de motifs non borné, cycle d'alias dans un fichier de règles, clés de fusion YAML `<<` non gérées, texte d'un entier à zéros en tête réécrit, quelques tests manquants. L'affichage du verdict d'une écriture et la proposition des valeurs par défaut dans les questions viendront avec les commandes d'écriture (US-007-07 à 09).

### US-007-07

- `client create`, `client edit <id>` et `client list` (`ClientCommands`), sur les cas d'usage `CreateClient` et `EditClient` qui posent les questions par le port `Questions` (valeurs facultatives, listes séparées par des virgules, `tell` pour expliquer une nouvelle demande) et écrivent par `SaveElement`, donc avec les règles de l'ADR-009.
- Secret d'un client confidentiel (`ClientDefinition.secret`, clé `secret`) : une variable seule, sans valeur par défaut (`{{shopSecret}}`), proposée à partir de l'identifiant, ou aucun secret ; une valeur écrite en dur ou `{{x:defaut}}` est refusée. Passer un client en SPA retire son secret ; un secret donné pour une SPA ou un client personnalisé est refusé.
- Un client personnalisé ne se voit demander que nom, description, URIs et origines web ; `--type custom` est refusé.
- Après l'écriture : fichier écrit, valeurs posées par les règles, avertissements, non-conformités restant à corriger, valeurs non vérifiables.
- Mode script (décision de l'utilisateur) : une question sans réponse prend la valeur proposée, et n'échoue (code 2) que s'il n'y en a pas ; `realm create` sans `--realm` prend donc le nom du dossier. `none` ou `-` vident une liste ; le premier client d'un realm demande `--declare-type`. Les invites disent comment garder (Entrée) ou vider (`-`) une valeur. Le lanceur affiche le message de la première cause d'une erreur au lieu de `null`.
- Exécutée par des sous-agents ; la revue finale a relevé trois points importants corrigés avant la fusion (secret en clair par la valeur par défaut d'une variable, mode script qui ne retenait aucune proposition, invites trompeuses). Points mineurs reportés : une URL avec `/` final ou un chemin donne `https://a//*` ou une origine avec un chemin, passage en SPA qui retire le secret sans le dire, question d'URL inutile quand `--redirect-uris` est donné, secret donné pour une SPA refusé après les premières questions, pas de retour de « une variable » vers « aucun » dans la question du secret, Ctrl-C en session qui prend les propositions, valeur par défaut d'une règle qui pourrait poser un secret en dur, quelques tests manquants (lignes d'avertissement affichées, retour d'erreur précis de `--declare-type`).

