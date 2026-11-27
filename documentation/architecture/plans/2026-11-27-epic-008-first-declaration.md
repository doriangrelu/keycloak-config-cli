# EPIC-008, première déclaration précise (US-008-03) — plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour exécuter ce plan tâche par tâche. Les étapes utilisent des cases à cocher (`- [ ]`).

**Objectif :** connecté à une cible, la confirmation de la première déclaration d'un type (premier client, premier groupe, premiers rôles du realm ou d'un client) liste ce que le prochain import supprimerait sur la cible, au lieu de l'avertissement générique.

**Architecture :** la liste vient d'une **simulation de l'import** (et non d'une comparaison de noms), pour être exactement celle que donnerait `realm dry-run` : le port `RealmDefinitions` rend les fichiers du realm tels qu'ils seraient après l'enregistrement de l'élément (en mémoire, rien n'est écrit), la `Connection` les simule (US-008-02), et le cas d'usage `DeclarationDeletions` ne garde que les suppressions du type nouvellement déclaré. Les cas d'usage de création reçoivent le texte de l'avertissement par une petite interface (`DeclarationWarning`) que le shell fournit avec la connexion et la mémoire de la session.

**Stack :** Java 25, Spring Boot 4.0.2, Spring Shell 4.0.4, SnakeYAML 2.5, Jackson 2.20, Keycloak 26.5.3, Testcontainers.

**Spec :** [ADR-010](../ADR-010-shell-mode-connecte.md) (« Import et dry-run du realm ouvert », point « Première déclaration d'un type »), [ADR-008](../ADR-008-shell-edition-des-definitions.md) (avertissement de première déclaration, `--declare-type`), [EPIC-008](../epics/EPIC-008-shell-connecte.md) (US-008-03).

Package racine : `io.github.doriangrelu.keycloak.config` (`...` ci-dessous).

## Contraintes globales

- Celles des plans précédents (licence, `spotless:apply`, ArchUnit : `infrastructure.cli.shell` ne voit ni `infrastructure.source` ni `infrastructure.keycloak`, ces deux-là ne se voient pas ; anglais pour le code et la doc utilisateur, français pour l'architecture ; aucune mention d'organisation ni de projet interne ; fins de ligne LF).
- Branche `feature/US-008-03-first-declaration`. Commits au nom de `Dorian GRELU <doriangrelu839@gmail.com>`. Fin de story : `./mvnw -B verify` vert avec Docker (hors les 20 erreurs MockServer connues), `./sample/scripts/run-all.sh` vert, fusion, push sur les remotes du projet, « Réalisé » dans l'EPIC.
- **Hors connexion, rien ne change** : même avertissement, même question, mêmes tests (aucun test existant de première déclaration n'est modifié).
- **Variables (décision de l'utilisateur, option A)** : la simulation n'utilise que les valeurs déjà connues, sans rien demander : `--var` n'existe pas sur ces commandes, donc la mémoire de la session, puis `KCC_VAR_<nom>`, puis la valeur par défaut. S'il manque une valeur, l'avertissement générique est affiché avec la raison. Aucune valeur n'est retenue ni affichée.
- **Ce qui est listé** : les changements `DELETE` du plan de la simulation dont le type est celui que l'élément déclare : `CLIENT` (clé : `clientId`) pour un client ; `GROUP` (clé : chemin) pour un groupe ; `REALM_ROLE` pour un rôle du realm ; `CLIENT_ROLE` dont `RoleKey.clientId` est le client du rôle, pour un rôle de client (clé affichée : le nom du rôle). Dans l'ordre du plan, au plus 10 noms, puis `and <m> more`.
- **Textes** (`<cleaned>` est le texte actuel de `FirstDeclarationException.cleanedWith` : `clients`, `groups`, `realm roles`, `roles of client '<clientId>'`) :
  - au moins une suppression : `At the next import, these <cleaned> of Keycloak missing from the files will be deleted on <cible> (<n>): <k1>, <k2>, … and <m> more. Confirm?` (sans `and <m> more` jusqu'à 10) ;
  - aucune : `No <cleaned> of Keycloak would be deleted on <cible> now, but at each import the <cleaned> missing from the files will be. Confirm?` ;
  - simulation impossible : l'avertissement actuel (`FirstDeclarationException.warning`), suivi de ` (dry run on <cible> not possible: <raison>)`, la raison étant `variable '<nom>' has no value`, `Keycloak unreachable`, `Keycloak refused the dry run` ou `a file is invalid` ; jamais le message d'origine de l'erreur (il pourrait citer une valeur).
  - Hors connexion : l'avertissement actuel, inchangé.
- **Script** : sans `--declare-type`, l'échec (`FirstDeclarationException`, code `2`) porte le même texte que la question (précis si connecté) suivi de ` In a script, add --declare-type.` ; avec `--declare-type`, connecté, le texte précis (sans `Confirm?`) est affiché pour information (`Questions.tell`) et l'élément est écrit.
- La simulation n'écrit rien sur la cible, ni dans les fichiers ; elle a lieu seulement quand le type n'est pas encore déclaré.
- **Secrets** : aucune valeur de variable dans une sortie, un message ou un journal.

## Points de revue

1. **La liste est celle du dry-run** : sur la même cible, les éléments listés à la confirmation sont exactement les suppressions du type que montre `realm dry-run` juste après l'enregistrement → tâche 3 (IT Keycloak).
2. **Les éléments que l'import ne supprime pas** (clients créés par Keycloak, rôles par défaut, éléments protégés par les fichiers) **n'apparaissent pas** dans la liste → tâche 3.
3. **Un rôle de client** ne liste que les rôles de ce client, jamais ceux des autres clients ni les rôles du realm → tâches 2 et 3.
4. **Une variable sans valeur, une cible injoignable ou un fichier invalide** donnent l'avertissement générique avec la raison, sans valeur affichée ni question supplémentaire → tâche 2.
5. **Hors connexion**, le comportement de l'EPIC-007 est inchangé (tests existants verts sans modification) → tâche 3.

---

### Task 1: les fichiers du realm avec l'élément, sans rien écrire

**Fichiers :**
- Modifier : `.../application/definition/port/RealmDefinitions.java` (une méthode), `.../infrastructure/source/definition/RealmDefinitionFiles.java`, `src/test/java/.../application/definition/FakeRealmDefinitions.java`.
- Tests : `RealmImportFilesWithElementTest.java` (dossier temporaire).

**Interfaces :**
- Consomme : `RealmDefinitions.importFiles(folder, values)` (US-008-02), le code d'enregistrement existant (`save`, `fileOf`) et ses arbres YAML/JSON.
- Produit : `List<ImportFile> importFilesWith(String folder, ElementDefinition element, Map<String, String> values)` : les fichiers que lirait l'import du dossier, rendus comme `importFiles`, **tels qu'ils seraient après `save(folder, element, Optional.empty(), ElementKeys.empty())`** (même fichier cible, même représentation de l'élément ; les clés que les règles ajoutent ne changent pas ce que l'import supprime), calculés en mémoire : aucun fichier n'est créé ni modifié, y compris quand l'élément irait dans un nouveau fichier. Mêmes exceptions que `importFiles`.

- [ ] **Étape 1 : tests qui échouent** : `theElementIsAddedToItsFile` (un client dans `20-clients.yaml` existant), `anElementWithoutFileGetsTheFileSaveWouldCreate` (premier groupe d'un realm sans `40-groups`), `aClientRoleGoesUnderItsClient` (`roles.client.<clientId>`), `nothingIsWritten` (contenu et date des fichiers inchangés, aucun fichier créé), `theContentIsWhatSaveWrites` (comparer avec un `save` réel sur une copie du dossier), `placeholdersAreRendered`.
- [ ] **Étape 2 : lancer** `./mvnw -B -q test -Dtest=RealmImportFilesWithElementTest -Djacoco.skip=true` → échec.
- [ ] **Étape 3 : implémenter** en réutilisant le code d'enregistrement sur des arbres en mémoire (pas de second chemin d'écriture).
- [ ] **Étape 4 : relancer** les tests de `infrastructure.source` et `ArchitectureTest` → verts.
- [ ] **Étape 5 : commit** `feat(shell): the import files of a realm with one more element, nothing written`.

### Task 2: le cas d'usage `DeclarationDeletions`

**Fichiers :**
- Créer : `.../application/definition/DeclarationDeletions.java`, `.../application/definition/DeclarationWarning.java` (interface fonctionnelle).
- Modifier : `.../application/definition/ResolveVariables.java` (résolution sans question), `FirstDeclarationException.java` (le texte peut venir de l'extérieur).
- Tests : `DeclarationDeletionsTest.java`, `ResolveVariablesTest.java` (cas ajoutés).

**Interfaces :**
- Consomme : `RealmDefinitions.importFilesWith` (tâche 1), `Connection.dryRun` (US-008-02), `ImportReport`, `PlannedChange`, `RoleKey`, `VariableValues`.
- Produit :
  - `ResolveVariables.resolveKnown(String folder, VariableValues remembered)` : comme `resolve` sans `--var` ni question ; `MissingVariableException` (avec le nom) pour la première variable sans valeur ; ne retient rien.
  - `interface DeclarationWarning { String of(String folder, ElementDefinition element); }` ; `DeclarationWarning.OFFLINE` rend `FirstDeclarationException.warning(element)`.
  - `DeclarationDeletions(ResolveVariables, RealmDefinitions)` : `String warning(String folder, ElementDefinition element, Connection connection, VariableValues remembered)` selon les textes des contraintes globales (avec `Confirm?`) ; `String information(...)` (même texte sans ` Confirm?`, pour `--declare-type`) ; la simulation passe par `connection.dryRun(importFilesWith(...))` ; `MissingVariableException`, `KeycloakUnreachableException`, `KeycloakAuthenticationException` (ou un autre refus de Keycloak) et `InvalidImportException` donnent le texte générique avec leur raison ; toute autre exception remonte.
  - `FirstDeclarationException(ElementDefinition element, String warning)` : message `<warning> In a script, add --declare-type.` ; le constructeur actuel garde son message.

- [ ] **Étape 1 : tests qui échouent** (doubles de `Connection` et de `RealmDefinitions`) : `theDeletedClientsAreListed`, `onlyTheDeletionsOfTheDeclaredTypeAreListed` (un `DELETE` d'un autre type est ignoré), `aClientRoleListsOnlyTheRolesOfItsClient`, `realmRolesIgnoreClientRoles`, `tenNamesThenAndMore` (13 suppressions → 10 noms et `and 3 more`), `noDeletionSaysSo`, `aMissingVariableGivesTheGenericWarningWithItsName`, `anUnreachableKeycloakGivesTheGenericWarning`, `aRefusalGivesTheGenericWarning`, `anInvalidFileGivesTheGenericWarningWithoutItsMessage` (un message contenant une valeur repérable n'apparaît pas), `noQuestionIsAsked` (le double de `Questions` échoue s'il est appelé), `theValuesAreNotRemembered`, `informationHasNoQuestion` ; dans `ResolveVariablesTest` : `resolveKnownNeverAsks`, `resolveKnownNamesTheMissingVariable`.
- [ ] **Étape 2 : lancer** `-Dtest='DeclarationDeletionsTest,ResolveVariablesTest'` → échec.
- [ ] **Étape 3 : implémenter.**
- [ ] **Étape 4 : relancer** + tests de `application.definition` et `ArchitectureTest` → verts.
- [ ] **Étape 5 : commit** `feat(shell): what the first declaration of a type would delete on the target`.

### Task 3: brancher la confirmation dans le shell

**Fichiers :**
- Modifier : `.../application/definition/{CreateClient, CreateRole, CreateGroup}.java` (le texte vient d'un `DeclarationWarning`, `tell` de l'information avec `--declare-type`), `.../infrastructure/cli/shell/{ClientCommands, RoleCommands, GroupCommands}.java` (ou la configuration qui construit les cas d'usage) pour fournir un `DeclarationWarning` lié à la session (connexion courante et `VariableValues` ; `OFFLINE` sans connexion), `ShellCommandConfiguration` / `ShellAdaptersConfiguration`, `documentation/shell/commands.md` (première déclaration connectée), `CHANGELOG.md` (`Unreleased`).
- Tests : `FirstDeclarationConnectedIT.java` (doubles de `Connection`, par le vrai lanceur en mode script et par `ShellTestClient`), `FirstDeclarationKeycloakIT.java` (Keycloak 26.5.3, mode script).

**Interfaces :**
- Consomme : `DeclarationDeletions`, `DeclarationWarning` (tâche 2), `ShellSession.connection()` et `variables()`.
- Produit : les textes et comportements des contraintes globales, dans les trois commandes de création (`client create`, `role create`, `group create`) ; les autres chemins (édition, élément d'un type déjà déclaré) ne simulent rien.

- [ ] **Étape 1 : tests qui échouent** : `FirstDeclarationConnectedIT` : `theQuestionListsTheDeletions`, `aRefusedQuestionWritesNothing`, `declareTypeShowsTheListAndWrites`, `withoutDeclareTypeTheScriptFailsWithTheList` (code `2`), `offlineTheWarningIsUnchanged`, `aTypeAlreadyDeclaredDoesNotSimulate` (aucun appel à `dryRun`), `aMissingVariableGivesTheGenericWarning` (mode script, sans `KCC_VAR_`), `aRememberedValueIsUsed` (valeur donnée plus tôt par `realm dry-run --var`) ; `FirstDeclarationKeycloakIT` : `theListIsTheDryRunDeletions` (groupes `/legacy` et `/old` créés dans Keycloak, premier groupe déclaré dans les fichiers : la liste vaut les suppressions `GROUP` de `realm dry-run` lancé ensuite), `keycloakOwnedClientsAreNotListed` (premier client : `account`, `admin-cli`, `broker`, `realm-management`, `security-admin-console` absents de la liste), `aClientRoleListsOnlyItsClient`.
- [ ] **Étape 2 : lancer** `FirstDeclarationConnectedIT`, puis `FirstDeclarationKeycloakIT` (Docker) → échec.
- [ ] **Étape 3 : implémenter**, puis documenter (anglais) la première déclaration connectée dans `commands.md` et la ligne du `CHANGELOG`.
- [ ] **Étape 4 : relancer** les tests du shell (dont tous les tests existants de première déclaration, inchangés), les deux IT et `ArchitectureTest` → verts.
- [ ] **Étape 5 : commit** `feat(shell): the first declaration of a type lists what the import would delete`.

Fin de story : « Réalisé » de l'US-008-03, `TASKS.md`, revue finale, `./mvnw -B verify` avec Docker, `./sample/scripts/run-all.sh`, fusion, push sur les remotes du projet. Le scénario du sample en mode connecté vient avec l'US-008-07.
