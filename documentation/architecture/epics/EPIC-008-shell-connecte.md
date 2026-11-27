# EPIC-008 — Shell en mode connecté

**Statut :** En cours
**ADR :** [ADR-010](../ADR-010-shell-mode-connecte.md), [ADR-008](../ADR-008-shell-edition-des-definitions.md), [ADR-004](../ADR-004-moteur-de-reconciliation.md), [ADR-007](../ADR-007-fichiers-seule-verite.md)

Connecter le shell à un Keycloak local, de développement ou de qualification (jamais la production) pour appliquer et simuler le realm ouvert, extraire un realm configuré à la main en fichiers découpés et nettoyés, comparer un élément entre Keycloak et les fichiers, et proposer dans les questions ce que Keycloak contient.

## Exemple de session visé

```text
kcc> connect dev
Secret of client 'kcc-dev' (or KCC_DEV_SECRET):
Connected to dev (https://keycloak.dev.example.org, Keycloak 26.5.3)
kcc@dev> realm extract legacy --folder legacy --format yaml
Realm 'legacy' extracted to legacy/: 00-realm.yaml, 20-clients.yaml, 30-roles.yaml, 40-groups.yaml
Variables to give at import: shopSecret, smtpPassword
kcc@dev> realm open legacy
kcc:legacy@dev> realm dry-run --var shopSecret=… --var smtpPassword=…
Dry run: no change pending.
kcc:legacy@dev> client diff frontend
In Keycloak, not in the files:
  frontchannelLogout: true
```

## Stories

Ordre décidé avec l'utilisateur : connexion, dry-run et import, extraction, comparaison, complétion.

### US-008-01 — Cibles et connexion

**Statut :** Terminé
Fichier `kcc-targets.yaml` (cibles nommées, sans secret), `connect <cible>`, `disconnect`, cible affichée dans le prompt, `shell.target` pour les scripts, secret demandé sans écho ou lu dans `KCC_<CIBLE>_PASSWORD` / `KCC_<CIBLE>_SECRET`. Client Keycloak construit par cible, derrière un port de l'application.

### US-008-02 — Dry-run et import du realm ouvert

**Statut :** Terminé
`realm dry-run` et `realm import` (confirmation, `--apply` en script) sur la cible, avec le plan de l'EPIC-004 ; variables par `--var`, `KCC_VAR_<NOM>` ou demandées (sans écho pour les secrets).

### US-008-03 — Première déclaration précise

**Statut :** À faire
Connecté, la confirmation de première déclaration d'un type liste ce que le prochain import supprimerait sur la cible.

### US-008-04 — Extraction d'un realm

**Statut :** À faire
`realm extract <realm> [--folder] [--format] [--with-users]` : nouveau dossier, contenu normalisé (EPIC-005), découpé (`StandardLayout`), secrets en variables, liste des variables à fournir. Critère : un dry-run juste après n'annonce aucun changement.

### US-008-05 — Comparaison d'un élément

**Statut :** À faire
`client diff`, `role diff`, `group diff`, `realm diff` : clés présentes d'un seul côté et valeurs différentes, entre l'élément normalisé sur la cible et sa vue dans les fichiers ; rien n'est écrit.

### US-008-06 — Complétion depuis Keycloak

**Statut :** À faire
Connecté, les choix proposent aussi les éléments de la cible absents des fichiers, marqués `(Keycloak)` ; les rôles et clients créés par Keycloak sont lus sur la cible.

### US-008-07 — Documentation et sample

**Statut :** À faire
Documentation utilisateur (anglais) du mode connecté ; scénarios du sample : connexion à la cible du sample, extraction d'un realm, dry-run sans changement, import, `diff` après une modification dans Keycloak.

## Hors périmètre

- La production, et toute protection par liste d'URL interdites (décision de l'utilisateur).
- La fusion de Keycloak vers des fichiers existants (aller-retour) : à cadrer si le besoin se confirme.
- Toute écriture sur Keycloak autre que l'import.

## Points de vigilance

- Le client Keycloak par cible touche l'adaptateur Keycloak (fabrique ou contexte par connexion) : à préparer dès l'US-008-01, sans casser l'import en ligne de commande.
- Les secrets ne doivent jamais apparaître dans un fichier écrit, une sortie ou un journal.
- L'extraction doit être vérifiée de bout en bout (sample) : un dry-run juste après n'annonce aucun changement.

## Réalisé

### US-008-01

- Domaine : `Target` (nom, url, realm de login, client, type de grant, utilisateur, `ssl-verify`, `connect-timeout`) et `GrantType` (`password`, `client_credentials`). Ports `Targets` et `KeycloakConnector` dans `application.definition.port` ; cas d'usage `ConnectTarget` et `Connection` (cible, url, version de Keycloak, fermeture).
- `kcc-targets.yaml` lu par `TargetsFile` (`infrastructure.source.targets`) à la racine des dossiers de realms ; un fichier invalide fait échouer la commande (code 2) sans recopier le contenu du fichier. Les durées exigent une unité.
- Secret : `KCC_<CIBLE>_PASSWORD` ou `KCC_<CIBLE>_SECRET`, sinon demandé sans écho (`Questions.askSecret`) ; en mode script, seulement la variable d'environnement. Quand la console ne peut pas masquer l'écho, le secret est lu en clair après un avertissement (décision consignée dans le contrat de `askSecret`).
- Client Keycloak par cible : `ChildContextKeycloakConnector` construit un contexte Spring autonome par connexion (`TargetConnectionConfiguration`, importé et non scanné), qui reprend l'environnement principal sans aucune clé `keycloak*` puis pose celles de la cible ; version lue sur le serveur, pas d'attente de disponibilité, `read-timeout` jamais plus court que `connect-timeout`, pas de proxy pour une cible. L'import en ligne de commande garde ses beans. `KeycloakProvider.close()` libère désormais le client HTTP.
- Erreurs : `KeycloakUnreachableException` (code 3) et `KeycloakAuthenticationException` (code 4), sans l'erreur HTTP en cause (gardée en exception supprimée) pour que la session affiche un message clair ; un login refusé n'est jamais pris pour une connexion.
- Commandes `connect <cible>`, `disconnect`, `target list` (`ConnectionCommands`) ; prompt `kcc@cible>` ou `kcc:realm@cible>` (`ShellInputProvider`) ; `shell.target` connecte au démarrage, en script comme en session. Une nouvelle connexion remplace la précédente ; un échec de fermeture de l'ancienne devient un avertissement.
- Tests : `TargetsFileTest`, `ChildContextKeycloakConnectorTest` (Keycloak simulé), `ChildContextKeycloakConnectorIT` et `ConnectionCommandsKeycloakIT` (Keycloak 26.5.3, cibles `password` et `client_credentials`), `ConnectTargetTest`, `ConnectionCommandsIT`, `InteractiveShellSessionIT` (vrai lanceur de Spring Shell, console simulée), `MainContextWiringTest`. Aucun secret dans une sortie ni dans une trace.
- Exécutée par des sous-agents ; la revue finale a relevé un point critique (login refusé avalé par le repli de version) et deux points importants (erreur HTTP brute affichée en session, `shell.target` sans lecteur en session), corrigés avant la fusion.
- Points mineurs reportés : pas de test du code 2 d'une cible inconnue ; les messages d'erreur de `ssl-verify`, `connect-timeout` et `grant-type` recopient la valeur fautive ; `ConsoleQuestions.read` reste sensible à un `%` dans une question qui n'est pas un secret ; pas de test d'un `connect-timeout` vers une adresse qui ne répond jamais ; console simulée par Mockito dans le test de session ; les arguments de ligne de commande qui ne sont pas des options ne sont pas transmis au contexte d'une cible. Saisie réelle (prompt, Ctrl-D, secret sans écho) à vérifier à la main sur un vrai terminal.

### US-008-02

- `realm dry-run [--var nom=valeur]…` affiche le plan de l'EPIC-004 pour le realm ouvert sur la cible connectée ; `realm import [--var …] [--apply]` simule, affiche la cible et le plan, demande `Apply these <n> change(s) to <cible>?` (non par défaut), puis applique **les fichiers rendus qu'il a montrés** (`ImportOpenedRealm.prepare` puis `apply(PreparedImport)`). Plan vide : `Nothing to import`. En script sans `--apply` : `Nothing written: run again with --apply`, code 0. Commandes dans `RealmImportCommands`.
- Variables (`ResolveVariables`) : `--var`, puis la valeur gardée par la session (`VariableValues`, décision de l'utilisateur : option A), puis `KCC_VAR_<nom>`, puis la valeur par défaut, puis la question (sans écho quand le nom contient `secret` ou `password`). Une variable utilisée au moins une fois sans valeur par défaut doit recevoir une valeur. La mémoire est oubliée à `realm open`, `realm create`, `connect`, `disconnect` et à la sortie. ADR-010 complété en conséquence.
- Fichiers : ceux que lirait l'import de `<dossier>/*`, rendus par `RealmDefinitions.importFiles` (réutilise `ImportFileReader` et `MustacheTemplates`), avec les options d'import par défaut.
- Contexte de la cible : il porte désormais l'import (gestionnaires Keycloak, moteur de réconciliation, analyseur), sans runner, normalisation ni shell ; `ImportRealms` est construit à chaque appel. Un refus pendant un import dit « refused an operation of the import (missing rights?) », code 4.
- Codes : `realm dry-run` en script rend 10 s'il reste des changements ; dans un `shell.script`, 10 n'arrête pas le script et devient le code final si rien n'échoue (marque de session lue par `ShellLauncher`).
- Secrets : aucune valeur de variable dans une sortie ou un message. L'écho des scripts et les erreurs d'analyse de Spring Shell masquent `--var` (`SafeCommandParser`, `--var nom=***`) ; un fichier invalide après rendu ne donne que le fichier et la ligne ; `ShellLogLevels` plafonne à INFO les journaux qui montreraient une valeur (seule exception documentée : le journal HTTP brut).
- Tests : `ResolveVariablesTest`, `VariableValuesTest`, `RealmImportFilesTest`, `TargetImportIT`, `KeycloakFailuresTest`, `ImportOpenedRealmTest`, `RealmImportCommandsIT`, `RealmImportKeycloakIT`, `ShellLauncherTest`, `SafeCommandParserTest`. Documentation : `documentation/shell/`, CHANGELOG, codes de sortie.
- Exécutée par des sous-agents ; les revues ont relevé, et fait corriger avant la fusion, trois fuites possibles de valeurs (écho des scripts, erreur d'analyse, message d'un fichier invalide), un import qui relisait les fichiers après la confirmation, et une variable à valeur par défaut partielle jamais résolue.
- Points mineurs reportés : `variables list` affiche `default:` pour une variable dont un usage n'a pas de valeur par défaut ; `--var nom valeur` (espace, sans `=`) ne masque que le premier mot ; un dry-run avec changements suivi d'un import dans le même script garde le code 10 ; un `--var` inutilisé est accepté sans avertissement ; une `PreparedImport` reste utilisable après `disconnect` ; le corps de la réponse de Keycloak est repris sans limite dans le message d'un refus ; un dossier dont le nom contient `*`, `?` ou `{}` est lu comme un motif ; chaque `connect` démarre un contexte plus gros.
