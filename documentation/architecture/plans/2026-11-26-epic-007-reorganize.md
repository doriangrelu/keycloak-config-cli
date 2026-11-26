# EPIC-007, réorganisation d'un realm (US-007-11) — plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour exécuter ce plan tâche par tâche. Les étapes utilisent des cases à cocher (`- [ ]`).

**Objectif :** `realm reorganize` redécoupe un dossier de realm (typiquement un export Keycloak monolithique) selon la numérotation standard, après avoir montré le plan des déplacements.

**Architecture :** la correspondance clé → fichier standard est une connaissance du domaine (`StandardLayout`). Le port `RealmDefinitions` calcule un plan (`ReorganizationPlan`) et l'applique sur les arbres YAML/JSON, comme `realm format`. Le cas d'usage `ReorganizeRealm` vérifie que le realm est lisible et formaté, puis délègue ; la commande du shell montre le plan et demande confirmation.

**Stack :** Java 25, Spring Boot 4.0.2, Spring Shell 4.0.4, SnakeYAML 2.5, Jackson 2.20 ; Bash et Docker pour le sample.

**Spec :** section US-007-11 de l'[EPIC-007](../epics/EPIC-007-shell-edition.md) (décisions du 26/11/2026), [ADR-008](../ADR-008-shell-edition-des-definitions.md) (« Un realm = un dossier », réécriture depuis l'arbre).

Package racine : `io.github.doriangrelu.keycloak.config` (`...` ci-dessous).

## Contraintes globales

- Celles des plans précédents (licence, `spotless:apply`, ArchUnit, placeholders conservés et écrits entre guillemets, anglais pour le code et la doc utilisateur, français pour l'architecture, aucune mention d'organisation ni de projet interne, fins de ligne LF).
- Branche `feature/US-007-11-reorganize`. Commits au nom de `Dorian GRELU <doriangrelu839@gmail.com>`. Fin de story : `./mvnw -B verify` vert avec Docker (hors les 20 erreurs MockServer connues), `./sample/scripts/run-all.sh` vert, fusion, push sur les remotes du projet, « Réalisé » dans l'EPIC et EPIC-007 « Terminé ».
- Correspondance (exacte) : `clients` → `20-clients` ; `roles` → `30-roles` ; `groups` → `40-groups` ; `users`, `scopeMappings`, `clientScopeMappings`, `defaultGroups` → `50-links` ; toute autre clé → `00-realm`. La clé `realm` est écrite dans chaque fichier.
- Textes : `Moves planned:`, une ligne par déplacement `  <clé> (<nombre>)  <origine> -> <cible>` (le nombre pour une liste ; rien pour un objet ou une valeur), une ligne `  <origine> -> <cible> (what is left)` pour un renommage, puis `Comments around moved elements may be lost. Commit your files first, then run a dry run after.`, la question `Apply these moves?` (non par défaut), le résultat `Realm '<dossier>' reorganized: <n> files written, <m> removed`, et `Nothing to move` pour un dossier déjà rangé.

## Points de revue

1. **Un export monolithique YAML puis JSON** est découpé en `00-realm`, `20-clients`, `30-roles`, `40-groups`, `50-links`, et l'union des clés des fichiers produits est égale au contenu d'origine (aucune clé perdue, aucune en double) → tâche 2.
2. **Un dossier déjà découpé autrement** (par exemple clients et rôles dans un même `10-app.yaml`) : les éléments rejoignent les fichiers standard, et un fichier vidé est supprimé → tâche 2.
3. **Un placeholder** (`"{{appUrl}}/*"`) reste écrit tel quel, entre guillemets, après le déplacement → tâche 2.
4. **Un fichier non formaté ou illisible** fait refuser la commande sans rien écrire → tâche 1.
5. **Le realm réorganisé s'importe** dans Keycloak avec le même résultat que les fichiers d'origine → tâche 4 (sample).

---

### Task 1: le plan de réorganisation et le cas d'usage

**Fichiers :**
- Créer : `.../domain/definition/StandardLayout.java`, `.../domain/definition/ReorganizationPlan.java`, `.../application/definition/ReorganizeRealm.java`.
- Modifier : `.../application/definition/port/RealmDefinitions.java` (deux méthodes), `src/test/java/.../application/definition/FakeRealmDefinitions.java`, `RealmDefinitionFiles.java` (méthodes vides levant `UnsupportedOperationException` en attendant la tâche 2).
- Tests : `StandardLayoutTest`, `ReorganizeRealmTest`.

**Interfaces :**
- Produit :
  - `StandardLayout` : `static String fileStemOf(String key)` selon la correspondance des contraintes globales ; `static List<String> stems()` dans l'ordre `00-realm`, `20-clients`, `30-roles`, `40-groups`, `50-links`.
  - `record ReorganizationPlan(List<Move> moves, List<Rename> renames, List<String> removals)` avec `record Move(String key, OptionalInt count, String from, String to)` et `record Rename(String from, String to)` ; `boolean isEmpty()`.
  - Port : `ReorganizationPlan planReorganization(String folder)` et `List<String> reorganize(String folder, ReorganizationPlan plan)` (les fichiers écrits).
  - `ReorganizeRealm(RealmDefinitions)` : `ReorganizationPlan plan(String folder)` (refuse `UnknownRealmFolderException`, `ReadOnlyRealmException` si un fichier est illisible, `UnformattedFileException` si un fichier n'est pas formaté — mêmes exceptions que `SaveElement`) ; `List<String> apply(String folder, ReorganizationPlan plan)`.

- [ ] **Étape 1 : tests qui échouent** : `StandardLayoutTest` (chaque clé de la correspondance, `clientScopes` et `authenticationFlows` → `00-realm`, ordre de `stems()`) ; `ReorganizeRealmTest` : `anUnformattedFileIsRefused`, `anUnreadableFileIsRefused`, `thePlanComesFromThePort`, `applyWritesThroughThePort`.
- [ ] **Étape 2 : lancer** `./mvnw -B -q test -Dtest='StandardLayoutTest,ReorganizeRealmTest' -Djacoco.skip=true` → échec de compilation.
- [ ] **Étape 3 : implémenter.**
- [ ] **Étape 4 : relancer** + `ArchitectureTest` → verts.
- [ ] **Étape 5 : commit** `feat(shell): the standard layout of a realm, and the reorganization use case`.

### Task 2: planifier et appliquer sur les fichiers

**Fichiers :**
- Modifier : `.../infrastructure/source/definition/RealmDefinitionFiles.java` (et si besoin `TreeObject`, `YamlDocumentTree`, `JsonDocumentTree` pour lire et déplacer un nœud de premier niveau tel quel, commentaires compris quand c'est possible).
- Tests : `ReorganizeFilesTest.java` ; ressources `src/test/resources/definition/reorganize/{monolithic-yaml,monolithic-json,mixed,already-standard}/`.

**Interfaces :**
- Consomme : `StandardLayout`, `ReorganizationPlan` (tâche 1).
- Produit : l'implémentation des deux méthodes du port. Le plan : pour chaque clé de premier niveau de chaque fichier (hors `realm`), un `Move` quand le fichier qui la porte n'a pas pour nom `<stem>.<ext>` ; un `Rename` quand le fichier d'origine garde des clés `00-realm` et ne s'appelle pas `00-realm.<ext>` ; une suppression pour un fichier qui ne garde que `realm`. Format des fichiers créés : celui du fichier qui porte le plus de clés `00-realm` (à défaut, celui du premier fichier). Une clé de liste présente dans deux fichiers est concaténée dans le fichier cible (dans l'ordre des noms de fichiers). L'application réécrit les arbres et ne laisse aucun fichier à moitié écrit : tout est calculé en mémoire, puis écrit.

- [ ] **Étape 1 : tests qui échouent** (`ReorganizeFilesTest`, sur une copie des ressources dans un dossier temporaire) : `aMonolithicYamlExportIsSplit` et `aMonolithicJsonExportIsSplit` (fichiers produits, clés de chacun, union = contenu d'origine, point de revue 1) ; `aMixedFolderJoinsTheStandardFiles` (fichier vidé supprimé, point de revue 2) ; `placeholdersStayQuoted` (point de revue 3) ; `anAlreadyStandardFolderHasNothingToMove` ; `listsInTwoFilesAreConcatenated` ; `thePlanListsCountsForLists`.
- [ ] **Étape 2 : lancer** `-Dtest=ReorganizeFilesTest` → échec.
- [ ] **Étape 3 : implémenter.**
- [ ] **Étape 4 : relancer** les tests de `infrastructure.source.definition` et `ArchitectureTest` → verts.
- [ ] **Étape 5 : commit** `feat(shell): plan and apply the reorganization of a realm folder`.

### Task 3: la commande `realm reorganize`

**Fichiers :**
- Modifier : `.../infrastructure/cli/shell/RealmCommands.java`, `ShellCommandConfiguration` / `ShellAdaptersConfiguration` (bean `ReorganizeRealm`), `documentation/shell/commands.md` et `documentation/shell/files.md`.
- Tests : `RealmReorganizeIT.java` (par le vrai lanceur en mode script et par `ShellTestClient`).

**Interfaces :**
- Consomme : `ReorganizeRealm.plan/apply`, `ShellSession.requireOpenedRealm()`, `Questions.confirm`.
- Produit : `realm reorganize [--apply]` sur le realm ouvert ; textes des contraintes globales ; sans `--apply` en mode script, la confirmation prend sa valeur par défaut (non) : rien n'est écrit et la commande se termine avec le code `0` et la ligne `Nothing written: run again with --apply`.

- [ ] **Étape 1 : tests qui échouent** : `thePlanIsShownAndNothingIsWrittenWithoutApply` ; `applyReorganizesTheFolder` ; `aStandardFolderSaysNothingToMove` ; `anUnformattedRealmIsRefused` (code `2`).
- [ ] **Étape 2 : lancer** `RealmReorganizeIT` → échec.
- [ ] **Étape 3 : implémenter**, puis documenter la commande (anglais) dans `commands.md` et le découpage standard, `50-links` compris, dans `files.md`.
- [ ] **Étape 4 : relancer** les tests du shell et `ArchitectureTest` → verts.
- [ ] **Étape 5 : commit** `feat(shell): realm reorganize`.

### Task 4: le scénario du sample

**Fichiers :**
- Créer : `sample/shell/monolithic/realm-export.yaml` (le realm `reorganized` : les clients, rôles, groupes et utilisateurs de `sample/realms/initial/realm.yaml`, réunis dans un seul fichier, avec `defaultGroups`), `sample/scripts/12-shell-reorganize.sh` (mode `100755`, fins de ligne LF).
- Modifier : `sample/scripts/00-up.sh` (supprime le realm `reorganized` d'un run précédent), `sample/README.md`.

**Interfaces :**
- Consomme : `realm reorganize --apply` (tâche 3), `shell_cli`, `cli`, `kcadm` de `lib.sh`.

- [ ] **Étape 1 :** le scénario copie `monolithic/` dans `shell/work/reorganized/`, lance `realm reorganize --apply` avec `--shell.realm=reorganized` → code `0` ; vérifie les fichiers `00-realm`, `20-clients`, `30-roles`, `40-groups`, `50-links` ; importe le dossier → code `0` ; vérifie dans Keycloak les clients, rôles, groupes, utilisateurs et le groupe par défaut ; relance l'import en dry-run → aucune modification annoncée.
- [ ] **Étape 2 :** construire le JAR et lancer `./sample/scripts/run-all.sh` → tous les contrôles passent.
- [ ] **Étape 3 : commit** `test(sample): reorganize a monolithic export, then import it`.

Fin de story : « Réalisé » de l'US-007-11, EPIC-007 « Terminé », `TASKS.md` (EPIC-007 terminée), revue finale, `./mvnw -B verify` avec Docker, fusion, push sur les remotes du projet.
