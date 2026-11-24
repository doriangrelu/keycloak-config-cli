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

**Statut :** À faire
`realm create` : nom du dossier, nom du realm (valeur, variable ou valeur composée), format. Écrit `00-realm` avec la clé `realm`, `enabled: true` et les valeurs par défaut des règles du realm, s'il y en a. Refuse un dossier déjà existant. Ouvre le realm créé.

### US-007-05 — Variables

**Statut :** À faire
`TemplatedValue` : reconnaissance des placeholders dans une saisie (`{{nom}}`, `{{nom:défaut}}`, valeurs composées), validation de la syntaxe (accolades, nom valide, pas de triple moustache), écriture en chaîne entre guillemets. Utilisée par toutes les questions. `variables list` : nom, valeur par défaut, fichiers où chaque variable apparaît ; signale une même variable avec deux valeurs par défaut différentes.

### US-007-06 — Règles de configuration

**Statut :** À faire
Concevoir le **formalisme déclaratif** des règles (à valider avant d'écrire le moteur) : un fichier de règles qui dit, pour un type d'élément et, pour un client, un type d'application, quelles valeurs fixer par défaut et quelles contraintes vérifier (valeur imposée, valeur interdite, motif d'URI refusé…), avec le message à afficher. Moteur de règles dans `domain.definition`, qui interprète ces règles ; lecture des fichiers de règles dans l'infrastructure. Règles de base embarquées dans l'outil ; règles d'un utilisateur ajoutées par un fichier donné au lancement. Une règle trop riche pour le formalisme peut rester écrite en Java. Cohérence du realm : identifiant unique dans le dossier, rôles et groupes référencés définis dans le dossier. Une valeur entièrement variable est signalée comme non vérifiable, sans être refusée. Non-conformités des éléments écrits à la main signalées à l'ouverture du realm. Une ou deux règles d'exemple (par exemple : une application front-end utilise PKCE S256 ; pas d'URI de redirection `*` seule) ; les règles de durcissement réelles seront fournies plus tard (dont le retrait de `offline_access` et `uma_authorization` des rôles par défaut du realm).

### US-007-07 — Clients

**Statut :** À faire
`client create` et `client edit <id>` guidés : identifiant, nom, description, type d'application (choix commentés), URL de l'application, URIs de redirection et origines web proposées à partir de l'URL. Les options Keycloak découlent du type et des règles (client public ou confidentiel, flows, compte de service, PKCE) ; le secret d'un client confidentiel est une variable, jamais une valeur en dur proposée par le shell. En modification, les valeurs actuelles sont proposées par défaut, et un client « personnalisé » n'est modifiable que dans ses champs communs. `client list`.

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

