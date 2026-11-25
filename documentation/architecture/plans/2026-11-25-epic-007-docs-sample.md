# EPIC-007, documentation et sample (US-007-10) — plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour exécuter ce plan tâche par tâche. Les étapes utilisent des cases à cocher (`- [ ]`).

**Objectif :** permettre à un script d'enchaîner des commandes du shell (`shell.script`) ou d'agir sur un realm existant (`shell.realm`), documenter le shell pour ses utilisateurs, et ajouter au sample un scénario qui crée un realm avec le shell puis l'importe dans Keycloak.

**Architecture :** `ShellLauncher` ouvre le realm de `shell.realm` dans la session, puis exécute `shell.command` ou chaque ligne du fichier `shell.script` avec le même `CommandExecutor` et la même session. La documentation utilisateur va dans `documentation/shell/`. Le sample monte un dossier `sample/shell/` en écriture dans le conteneur de la CLI.

**Stack :** Java 25, Spring Boot 4.0.2, Spring Shell 4.0.4 ; Bash, Docker Compose pour le sample.

**Spec :** section US-007-10 de l'[EPIC-007](../epics/EPIC-007-shell-edition.md) (décisions du 25/11/2026), [ADR-008](../ADR-008-shell-edition-des-definitions.md), [ADR-009](../ADR-009-regles-de-configuration.md).

Package racine : `io.github.doriangrelu.keycloak.config` (`...` ci-dessous).

## Contraintes globales

- Celles des plans précédents (licence, `spotless:apply`, ArchUnit, anglais pour le code et la doc utilisateur, français pour l'architecture, aucune mention d'organisation ni de projet interne).
- Branche `feature/US-007-10-docs-sample`. Commits au nom de `Dorian GRELU <doriangrelu839@gmail.com>`. Fin de story : `./mvnw -B verify` vert avec Docker (hors les 20 erreurs MockServer connues), `./sample/scripts/run-all.sh` vert, fusion, push sur les remotes du projet, « Réalisé » dans l'EPIC.
- Propriétés : `shell.realm` (un dossier de `shell.root`), `shell.script` (un chemin relatif au répertoire de lancement), `shell.command`.

## Points de revue

1. **Une commande du fichier qui échoue** arrête le script avec son code (`2` pour un refus d'une règle) ; les commandes suivantes ne sont pas exécutées et la sortie dit laquelle a échoué (numéro de ligne) → tâche 1.
2. **`realm create` dans un fichier** ouvre le realm pour les lignes suivantes, comme en session → tâche 1.
3. **`shell.realm` vers un dossier inconnu** fait échouer le lancement (code `2`) avant toute commande → tâche 1.
4. **Un fichier `shell.script` introuvable** ou vide : code `2` avec un message clair (vide : code `0`, rien à faire, dit) → tâche 1.
5. **Le realm créé par le shell s'importe tel quel** dans Keycloak avec les variables données à l'import (`appUrl`, secrets) → tâche 3.

---

### Task 1: `shell.realm` et `shell.script`

**Fichiers :**
- Modifier : `.../infrastructure/cli/shell/ShellProperties.java`, `ShellLauncher.java`.
- Tests : `ShellLauncherIT.java` (ajouts), ressources `src/test/resources/shell/*.kcc`.

**Interfaces :**
- Produit : `ShellProperties.getRealm()` → `Optional<String>`, `getScript()` → `Optional<Path>` ; au lancement : `shell.command` et `shell.script` ensemble → message `shell.command and shell.script cannot be used together` et code `2` ; `shell.realm` → `ReadRealms.open(folder)` puis `ShellSession.open(folder)`, échec → code `2` ; `shell.script` : lignes `strip()`, vides et `#…` ignorées, avant chaque commande la sortie affiche `> <commande>`, la première commande dont le code n'est pas `0` arrête le script avec ce code et la ligne `Stopped at line <n>: <commande>` ; fichier introuvable → code `2` ; sans commande → `No command in <fichier>` et code `0`. `shell.realm` vaut aussi en session interactive (realm ouvert au démarrage).

- [ ] **Étape 1 : tests qui échouent** (`ShellLauncherIT`, dossiers temporaires) : `aScriptRunsItsCommandsInOneSession` (realm create, client create, role create → code `0`, fichiers écrits) ; `aFailingCommandStopsTheScript` (une URI `*` refusée → code `2`, ligne suivante non exécutée, sortie contient `Stopped at line`) ; `commentsAndBlankLinesAreSkipped` ; `aMissingScriptFails` ; `commandAndScriptTogetherFail` ; `theRealmPropertyOpensTheRealm` (`--shell.realm` + `client list`) ; `anUnknownRealmPropertyFails`.
- [ ] **Étape 2 : lancer** `ShellLauncherIT` → échec.
- [ ] **Étape 3 : implémenter.**
- [ ] **Étape 4 : relancer** les tests du shell et `ArchitectureTest` → verts.
- [ ] **Étape 5 : commit** `feat(shell): a script of commands, and a realm opened at start`.

### Task 2: la documentation du shell

**Fichiers :**
- Créer : `documentation/shell/README.md` (vue d'ensemble, lancement, session, mode script, `shell.root`, `shell.realm`, `shell.command`, `shell.script`, codes de sortie), `documentation/shell/commands.md` (chaque commande, ses questions, ses options, exemples de sortie : `realm`, `client`, `role`, `group`, `variables`, `rules`), `documentation/shell/rules.md` (règles de base, fichier de l'organisation `shell.rules`, format, opérateurs, `when`, `defaults`, `severity`, désactivation avec `for` et `reason`, valeurs non vérifiables, ce qui bloque une écriture), `documentation/shell/files.md` (un realm par dossier, découpage `00-realm`/`20-clients`/`30-roles`/`40-groups`, `realm format`, commentaires conservés, placeholders et `variables list`, première déclaration d'un type).
- Modifier : `documentation/README.md` (section « Interactive Shell »), `README.md` (un paragraphe et un lien), `CHANGELOG.md` (entrée non publiée : le shell).

**Interfaces :**
- Consomme : le comportement réel du code (options et messages lus dans `infrastructure/cli/shell/*Commands.java`, `ShellLauncher`, `ConsoleQuestions`, `rules/base-rules.yaml`, `RuleFileReader`), l'ADR-009 pour le format des règles, la tâche 1 pour `shell.script` et `shell.realm`.

- [ ] **Étape 1 :** relever les options, questions et messages réels de chaque commande dans le code (pas dans les plans) ; noter tout écart avec l'EPIC dans le rapport.
- [ ] **Étape 2 :** écrire les quatre pages, en anglais, avec des exemples exécutables (YAML de règles, fichier `.kcc`, commandes).
- [ ] **Étape 3 :** vérifier chaque exemple de règle avec `RuleFileReader` (un test `DocumentedRulesTest` qui lit les blocs YAML d'exemple extraits dans `src/test/resources/shell/documented-rules.yaml`) et chaque lien relatif (fichier existant).
- [ ] **Étape 4 :** lancer le test → vert.
- [ ] **Étape 5 : commit** `docs(shell): user documentation of the interactive shell`.

### Task 3: le scénario du sample

**Fichiers :**
- Créer : `sample/shell/create-realm.kcc` (crée le realm `shelled` dans le dossier `shelled` : un client `spa` avec `--url {{appUrl}}`, un client `web-application` avec `--secret {{shopSecret}}`, un rôle de realm `reader`, un rôle du client `shop` `buyer`, un groupe `team` avec `reader` et un sous-groupe `devs` avec `shop/buyer`, `--declare-type` là où il faut), `sample/shell/refused.kcc` (un client avec `--redirect-uris *`), `sample/scripts/10-shell-create-and-import.sh`, `sample/scripts/11-shell-rule-refusal.sh`.
- Modifier : `sample/docker-compose.yml` (volume `./shell:/shell` en écriture pour le service `cli`), `sample/scripts/lib.sh` (fonction `shell_cli` : `--run.operation=SHELL --shell.root=/shell/work`), `sample/scripts/00-up.sh` (vide `sample/shell/work` et supprime le realm `shelled` d'un run précédent), `sample/scripts/run-all.sh` (inclut `1*.sh`), `sample/README.md` (les deux scénarios), `.gitignore` (`sample/shell/work/`).

**Interfaces :**
- Consomme : tâche 1 (`shell.script`) ; `lib.sh` (`cli`, `kcadm`, `expect_exit`, `expect_in`, `pass`, `fail`).

- [ ] **Étape 1 :** `10-shell-create-and-import.sh` : lancer le fichier `create-realm.kcc` → code `0` ; importer `/shell/work/shelled/*` avec `--import.mustache.enabled=true` et les variables `env`, `appUrl`, `shopSecret` → code `0` ; vérifier dans Keycloak : clients `frontend` et `shop`, PKCE `S256` sur `frontend`, rôle `reader`, rôle `buyer` de `shop`, groupe `/team/devs` avec `shop/buyer`.
- [ ] **Étape 2 :** `11-shell-rule-refusal.sh` : lancer `refused.kcc` → code `2`, sortie contenant `client.redirect-uris.no-wildcard`, aucun fichier `20-clients` écrit dans son dossier.
- [ ] **Étape 3 : lancer** `./sample/scripts/run-all.sh` (Docker démarré, JAR construit par `./mvnw -B -q clean package -DskipTests`) → tous les contrôles passent.
- [ ] **Étape 4 :** mettre à jour `sample/README.md`.
- [ ] **Étape 5 : commit** `test(sample): create a realm with the shell, then import it`.

Fin de story : « Réalisé » de l'US-007-10, `TASKS.md` (« US-007-01 à 10 terminées »), revue finale, `./mvnw -B verify` avec Docker, fusion, push sur les remotes du projet.
