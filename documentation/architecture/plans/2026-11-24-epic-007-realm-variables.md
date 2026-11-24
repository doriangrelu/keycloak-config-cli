# EPIC-007, création d'un realm et variables (US-007-04 et 05) — plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour exécuter ce plan tâche par tâche. Les étapes utilisent des cases à cocher (`- [ ]`).

**Objectif :** créer un realm depuis le shell (`realm create`), saisir partout une valeur en dur, une variable ou une valeur composée avec contrôle de la syntaxe, et lister les variables d'un realm (`variables list`).

**Architecture :** sur le socle (plan [2026-11-24-epic-007-socle](2026-11-24-epic-007-socle.md)) : nouveaux cas d'usage dans `application.definition`, nouvelles méthodes du port `RealmDefinitions` implémentées par `RealmDefinitionFiles`, nouvelles commandes dans `infrastructure.cli.shell` qui posent leurs questions par `ConsoleQuestions`, construit sur l'`InputReader` et la sortie du `CommandContext` que Spring Shell injecte dans la méthode de la commande (vérifié dans `MethodInvokerCommandAdapter`).

**Stack :** celle du socle (Java 25, Spring Boot 4.0.2, Spring Shell 4.0.4, SnakeYAML 2.5, Jackson 2.20).

**Spec :** [ADR-008](../ADR-008-shell-edition-des-definitions.md), [EPIC-007](../epics/EPIC-007-shell-edition.md) (US-007-04, US-007-05).

## Contraintes globales

- Celles du plan du socle : en-tête de licence, `spotless:apply`, couches et règles ArchUnit, placeholders conservés et écrits entre guillemets doubles, numérotation `00-realm`, code et doc utilisateur en anglais, architecture en français, une branche par story, commits au nom de `Dorian GRELU <doriangrelu839@gmail.com>`, fin de story : `./mvnw -B verify` vert (hors les 20 erreurs MockServer connues sous Windows), fusion, push `origin` et `adsn`, section « Réalisé ».
- Une commande sans réponse possible en mode script échoue par `MissingAnswerException` (code `2`) ; toute réponse peut être donnée d'avance par une option de la commande.
- Les règles de configuration (US-007-06) ne sont pas encore là : `realm create` écrit `realm` et `enabled: true` seulement ; les valeurs par défaut des règles du realm s'y ajouteront en US-007-06.

## Points de revue

1. **Un nom de dossier qui sort de la racine** (`../autre`, `/tmp/x`, `a/b`) ou caché (`.x`) est refusé, avec un message clair → test de la tâche 3.
2. **Un placeholder mal formé** (`{{env-mon-realm`) est redemandé en session, et fait échouer la commande (code `2`) en mode script, avant toute écriture → tests des tâches 1 et 3.
3. **Une variable écrite dans un commentaire YAML est listée**, puisque Mustache rend aussi les commentaires à l'import → test de la tâche 2.
4. **Une même variable avec deux valeurs par défaut différentes est signalée** ; avec la même valeur par défaut dans deux fichiers, elle ne l'est pas → test de la tâche 2.
5. **`realm create` sur un dossier existant ne touche à rien** → test de la tâche 3.

---

## US-007-05 — Variables (d'abord : `realm create` en a besoin)

Branche : `feature/US-007-05-variables`.

### Task 1: saisir une valeur avec variables

**Fichiers :**
- Modifier : `src/main/java/.../application/definition/port/Questions.java`
- Modifier : `src/main/java/.../infrastructure/cli/shell/ConsoleQuestions.java`
- Tests : `src/test/java/.../infrastructure/cli/shell/ConsoleQuestionsTest.java`

**Interfaces :**
- Consomme : `TemplatedValue.of(String)` et `InvalidTemplatedValueException` (socle, tâche 3).
- Produit (`Questions`) : `TemplatedValue askValue(String question, Optional<TemplatedValue> proposed)` — une valeur en dur, une variable ou une valeur composée ; une valeur dont un placeholder est invalide affiche le message de `InvalidTemplatedValueException` et est redemandée ; une réponse vide prend `proposed`.

- [ ] **Étape 1 : écrire les tests qui échouent** (`ConsoleQuestionsTest`) :
  - `aValueMayHoldVariables` : réponse `{{env}}-mon-realm` → `TemplatedValue.of("{{env}}-mon-realm")`.
  - `anInvalidPlaceholderIsAskedAgain` : réponses `{{env-mon-realm`, `{{env}}-mon-realm` → la seconde ; la sortie contient `Invalid value '{{env-mon-realm'`.
  - `anEmptyValueTakesTheProposedOne` : réponse vide, `proposed` = `{{appUrl}}` → `{{appUrl}}`.
  - `aMissingValueFailsInsteadOfWaiting` : lecteur vide → `MissingAnswerException`.
- [ ] **Étape 2 : lancer** `./mvnw -B -q test -Dtest=ConsoleQuestionsTest -Djacoco.skip=true` → échec de compilation (`askValue` inconnu).
- [ ] **Étape 3 : implémenter** `askValue` dans le port et dans `ConsoleQuestions` (sur `ask`, avec la conversion et la nouvelle demande en cas d'`InvalidTemplatedValueException`).
- [ ] **Étape 4 : relancer** → verts.
- [ ] **Étape 5 : commit** `feat(shell): ask for a value that may hold variables, asked again when a placeholder is invalid`.

### Task 2: lister les variables d'un realm

**Fichiers :**
- Créer : `src/main/java/.../domain/definition/VariableUse.java`, `src/main/java/.../domain/definition/Variables.java`
- Modifier : `src/main/java/.../application/definition/port/RealmDefinitions.java`, `src/test/java/.../application/definition/FakeRealmDefinitions.java`
- Créer : `src/main/java/.../application/definition/ListVariables.java`
- Modifier : `src/main/java/.../infrastructure/source/definition/RealmDefinitionFiles.java`
- Créer : `src/main/java/.../infrastructure/cli/shell/VariableCommands.java` ; modifier `ShellCommandConfiguration.java`, `infrastructure/cli/ShellAdaptersConfiguration.java`
- Tests : `src/test/java/.../domain/definition/VariablesTest.java`, `src/test/java/.../infrastructure/source/definition/RealmDefinitionFilesTest.java`, `src/test/java/.../infrastructure/cli/shell/VariableCommandsIT.java`

**Interfaces :**
- Produit (domaine) : `record VariableUse(String name, List<String> defaultValues, List<String> files)` (valeurs par défaut distinctes et triées, fichiers triés) avec `boolean hasConflictingDefaults()` (plus d'une valeur par défaut) ; `final class Variables` avec `static List<VariableUse> of(Map<String, String> sourcesByFile)` (variables triées par nom). Une variable sans valeur par défaut dans un fichier et avec une dans un autre n'est pas un conflit. Un placeholder invalide est ignoré (le fichier est alors illisible pour le shell, ce que `realm open` signale déjà).
- Produit (port) : `Map<String, String> sources(String folder)` — le texte de chaque fichier de définition du realm, tel qu'écrit (commentaires compris), par nom de fichier.
- Produit : `ListVariables(RealmDefinitions)` : `List<VariableUse> list(String folder)` (lève `UnknownRealmFolderException`).
- Produit (commande) : `variables list`, sur le realm ouvert ; une ligne par variable : `<name>  no default  <files>`, `<name>  default: <value>  <files>`, ou `<name>  defaults differ: <v1>, <v2>  <files>` ; `No variable` s'il n'y en a pas.

- [ ] **Étape 1 : écrire les tests qui échouent** :
  - `VariablesTest.variablesAreListedWithTheirDefaultsAndFiles` : `00-realm.yaml` = `realm: "{{env}}-mon-realm"`, `20-clients.yaml` = `realm: "{{env}}-mon-realm"\n...redirectUris: ["{{appUrl:https://localhost}}/*"]` → `appUrl` (défaut `https://localhost`, `20-clients.yaml`), `env` (sans défaut, les deux fichiers).
  - `VariablesTest.aVariableInACommentIsListed` : `# set {{secret}} at import` → `secret` listée.
  - `VariablesTest.differentDefaultsAreAConflict` : `{{url:a}}` et `{{url:b}}` → `hasConflictingDefaults()` ; `{{url:a}}` deux fois, et `{{url}}` → pas de conflit.
  - `RealmDefinitionFilesTest.sourcesAreTheFilesAsWritten` : sur `split`, `sources("split")` a 4 entrées et celle de `00-realm.yaml` commence par `# The realm and its settings`.
  - `VariableCommandsIT.listsTheVariablesOfTheOpenedRealm` (`ShellTestClient`, racine `src/test/resources/definition/realms`) : `realm open split` puis `variables list` contient `appUrl`, `env`, `no default`.
- [ ] **Étape 2 : lancer** `./mvnw -B -q test -Dtest='VariablesTest,RealmDefinitionFilesTest' -Djacoco.skip=true` → échec ; `./mvnw -B -q verify -Dsurefire.skip=true -Dit.test=VariableCommandsIT -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true` → échec.
- [ ] **Étape 3 : implémenter** le domaine (recherche des placeholders `\{\{([^{}]*)}}` dans chaque texte, chacun lu par `TemplatedValue.of`), le port et `FakeRealmDefinitions`, le cas d'usage, la commande et son câblage.
- [ ] **Étape 4 : relancer** les deux commandes → verts ; `ArchitectureTest` vert.
- [ ] **Étape 5 : commit** `feat(shell): variables list, the variables of a realm with their default values and files`.

### Fin de US-007-05

- [ ] `./mvnw -B verify` vert ; « Réalisé » de US-007-05 dans l'EPIC-007 ; `TASKS.md` ; fusion, push `origin` et `adsn`.

---

## US-007-04 — Création d'un realm

Branche : `feature/US-007-04-realm-create`.

### Task 3: `realm create`

**Fichiers :**
- Modifier : `src/main/java/.../application/definition/port/RealmDefinitions.java`, `src/test/java/.../application/definition/FakeRealmDefinitions.java`
- Créer : `src/main/java/.../application/definition/CreateRealm.java`, `RealmFolderExistsException.java`, `InvalidRealmFolderException.java`
- Modifier : `src/main/java/.../infrastructure/source/definition/RealmDefinitionFiles.java`
- Modifier : `src/main/java/.../infrastructure/cli/shell/RealmCommands.java` ; `ShellAdaptersConfiguration.java`, `ShellCommandConfiguration.java`
- Tests : `src/test/java/.../application/definition/CreateRealmTest.java`, `src/test/java/.../infrastructure/source/definition/CreateRealmFilesTest.java`, `src/test/java/.../infrastructure/cli/shell/RealmCreateIT.java`

**Interfaces :**
- Consomme : `Questions.askValue` (tâche 1), `Questions.choose`, `Choice`, `ShellSession.open` (socle).
- Produit (port) : `String createRealm(String folder, TemplatedValue realm, DefinitionFormat format)` — crée le dossier et `00-realm` + extension, avec `realm` (entre guillemets doubles s'il est templaté) et `enabled: true` ; rend le nom du fichier écrit.
- Produit : `CreateRealm(RealmDefinitions)` : `String create(String folder, TemplatedValue realm, DefinitionFormat format)`. Refuse un nom de dossier invalide (`InvalidRealmFolderException extends ApplicationException` ; valide : `[A-Za-z0-9][A-Za-z0-9._-]*`, ce qui exclut `..`, `/`, `\`, un nom caché ou vide) et un dossier existant (`RealmFolderExistsException`, message `Realm folder '<folder>' already exists: use 'realm open <folder>'`), avant toute écriture.
- Produit (commande) : `realm create [--folder <dossier>] [--realm <nom>] [--format yaml|json]` ; chaque option absente est demandée (`ask` pour le dossier, `askValue` pour le nom du realm proposé égal au dossier, `choose` pour le format avec `YAML — readable, accepts comments` et `JSON — no comment`) ; une option `--realm` dont un placeholder est invalide fait échouer la commande (code `2`) ; affiche `Realm '<folder>' created: <folder>/<file>` et ouvre le realm.

- [ ] **Étape 1 : écrire les tests qui échouent** :
  - `CreateRealmTest` (avec `FakeRealmDefinitions`) : `create("mon-realm", {{env}}-mon-realm, YAML)` appelle le port ; `create("../x", …)`, `create(".x", …)`, `create("a/b", …)`, `create("", …)` → `InvalidRealmFolderException` sans appel au port ; dossier existant → `RealmFolderExistsException` sans appel au port.
  - `CreateRealmFilesTest` (`@TempDir`) : `createRealm("mon-realm", {{env}}-mon-realm, YAML)` → `00-realm.yaml` contient `realm: "{{env}}-mon-realm"` et `enabled: true`, et `load("mon-realm")` rend ce realm ; en JSON → `00-realm.json` analysable, même contenu ; le fichier écrit est déjà au format (`unformattedFiles` vide).
  - `RealmCreateIT` (`ShellTestClient`, racine dans un `@TempDir` vide) : `realm create --folder demo --realm "{{env}}-demo" --format json` → sortie `Realm 'demo' created: demo/00-realm.json`, puis `realm show` fonctionne (le realm est ouvert) ; en session, `realm create` avec les réponses `demo2`, vide (nom proposé `demo2`), `1` → `demo2/00-realm.yaml` avec `realm: demo2`.
- [ ] **Étape 2 : lancer** `./mvnw -B -q test -Dtest='CreateRealmTest,CreateRealmFilesTest' -Djacoco.skip=true` puis `./mvnw -B -q verify -Dsurefire.skip=true -Dit.test=RealmCreateIT -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true` → échecs.
- [ ] **Étape 3 : implémenter** le port et son adapter (le contenu est écrit depuis un arbre vide puis rendu, comme les autres écritures), le cas d'usage, la commande (le `CommandContext` en paramètre, `ConsoleQuestions` construit sur son `inputReader()` et son `outputWriter()`), le câblage.
- [ ] **Étape 4 : relancer** → verts ; `ArchitectureTest` vert.
- [ ] **Étape 5 : commit** `feat(shell): realm create, a new realm folder with its realm file`.

### Fin de US-007-04

- [ ] `./mvnw -B verify` vert ; « Réalisé » de US-007-04 dans l'EPIC-007 ; `TASKS.md` : EPIC-007 « En cours (US-007-01 à 05 terminées) » ; fusion, push `origin` et `adsn`.
- [ ] Revue finale de la branche par un relecteur indépendant (comme pour le socle), puis cadrage avec l'utilisateur du formalisme des règles (US-007-06).
