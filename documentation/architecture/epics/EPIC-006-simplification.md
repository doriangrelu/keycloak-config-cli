# EPIC-006 — Simplification : les fichiers comme seule vérité

**Statut :** À faire
**ADR :** [ADR-007](../ADR-007-fichiers-seule-verite.md), [ADR-004](../ADR-004-moteur-de-reconciliation.md)

Recentrer l'outil sur son besoin, sur le modèle de `kubectl apply` : créer et mettre à jour les realms décrits par les fichiers, supprimer ce qui n'y figure plus, et variabiliser les valeurs avec Mustache. Les options qui compliquent le moteur ou contredisent ce besoin sont retirées, et les fonctionnalités gardées sont reprises sur le nouveau socle.

Non négociable à chaque story : les utilisateurs, les ressources techniques de Keycloak et les ressources protégées (par égalité ou par expression régulière) ne sont jamais supprimés.

Chaque story est livrée seule, build et tests d'intégration verts, comme pendant l'EPIC-003.

## Répartition cible des paquets hérités

À la fin de l'EPIC, le code ne contient plus que `domain`, `application` et `infrastructure`, plus la classe de démarrage `KeycloakConfigApplication`, qui reste à la racine pour que Spring trouve tous les composants. `ArchitectureTest` le vérifie.

| Paquet hérité | Contenu | Destination |
|---|---|---|
| `service` | `RealmImportService` : orchestration d'un import | `application.importation` : le cas d'usage, sur des ports ; ce qui appelle Keycloak passe dans des gateways de `infrastructure.keycloak` |
| `provider` | `KeycloakImportProvider`, `FileUtils` : lecture des fichiers (motifs, zip, URL distantes) | `infrastructure.source` |
| `provider` | `KeycloakExportProvider` : lecture des exports à normaliser | `infrastructure.source`, partagé avec l'import |
| `provider` | `KeycloakProvider` : client Keycloak, version du serveur | `infrastructure.keycloak.sdk` |
| `mustache` | `MustacheContextWithDefaults` | `infrastructure.source` : étape de préparation des fichiers |
| `model` | `RealmImport`, `AuthenticationFlowImport` (sous-classes de représentations Keycloak) | `infrastructure.keycloak.importation` : voir le point de vigilance sur le format des fichiers |
| `model` | `ImportResource`, `KeycloakImport` : fichiers lus | `infrastructure.source` |
| `model` | `ProtectedResource` | Supprimé au profit de `ProtectionRule` du domaine (US-006-06) |
| `properties` | Binding des propriétés Spring Boot | `infrastructure.cli` ; l'application reçoit des options simples (records), jamais les classes de propriétés |
| `configuration` | Beans Spring (`KeycloakConfigConfiguration`, `NormalizationConfiguration`) | Le paquet d'infrastructure dont ils créent les beans |
| `resource` | `ManagementPermissions` (proxy de l'API d'administration) | `infrastructure.keycloak.sdk` |
| `util` | `ResteasyUtil` et ses filtres | `infrastructure.keycloak.sdk` |
| `util` | `CloneUtil`, `JsonUtil`, `VersionUtil` | `infrastructure.keycloak` (seuls utilisateurs) |
| `util` | `CryptoUtil` | Supprimé avec l'état distant (US-006-02) |
| `util` | `LocalizationUtil` | Supprimé : plus utilisé |
| `exception` | `KeycloakProviderException`, `KeycloakRepositoryException` | `infrastructure.keycloak.sdk`, ou fusion avec `KeycloakAccessException` |
| `exception` | `InvalidImportException`, `ImportProcessingException` | `infrastructure.source` (fichier invalide) ; les erreurs de configuration vues par l'application restent des exceptions de `application` (ADR-005) |
| `exception` | `NormalizationException` | `infrastructure.keycloak.normalization` |
| `exception` | `KeycloakVersionUnsupportedException` | Supprimé : plus utilisé |
| Racine | `KeycloakConfigRunner`, `KeycloakConfigNormalizationRunner` | `infrastructure.cli` |

### Points de vigilance

- **Le format des fichiers est celui de Keycloak.** `RealmImport` hérite de `RealmRepresentation` ; or `infrastructure.source` ne doit pas dépendre du SDK Keycloak (ADR-001, vérifié par ArchUnit). Partage retenu : `infrastructure.source` lit et prépare les fichiers (motifs, zip, URL, Mustache) et rend des documents bruts ; leur lecture en `RealmImport` puis en BOs reste dans `infrastructure.keycloak.importation`. Ne pas assouplir la règle ArchUnit pour aller plus vite.
- **L'application ne voit ni Spring Boot ni Jackson.** Le cas d'usage d'import reçoit ses options et ses fichiers par des types de l'application ; seules les annotations `org.springframework.stereotype` y sont permises.
- **Pas de cycle entre les paquets d'infrastructure.** `infrastructure.cli` appelle l'application ; `infrastructure.keycloak` et `infrastructure.source` restent indépendants l'un de l'autre.
- **Un paquet à la fois**, build et tests d'intégration verts à chaque étape. Les tests suivent leurs classes.
- **La ligne de commande reste un adapter d'entrée.** Rien de ce qui est déplacé dans `infrastructure.cli` ne doit porter de logique d'import : c'est ce qui permettra d'ajouter plus tard Spring Shell comme second adapter d'entrée, sans toucher à l'application (voir `TASKS.md`, « Après la refonte »).

## Stories

### US-006-01 — Règle de nettoyage unique

**Statut :** À faire
Retirer les modes gérés (`import.managed.*`, `ManagedMode`, `DeletionPolicy` réduite aux garanties) : un type déclaré est nettoyé, un type non déclaré n'est pas touché. Les garanties restent dans le code : utilisateurs jamais supprimés, ressources techniques (`isOwnedByKeycloak`), ressources protégées, types déclarés en partie.

- Vérifier type par type que `isOwnedByKeycloak` couvre bien ce que Keycloak crée, en particulier pour les types qui étaient en `no-delete` par défaut (client scopes, scope mappings).
- Test de garantie pour chaque type nettoyé : une ressource technique, une ressource protégée par égalité et une protégée par expression régulière, absentes des fichiers, survivent au nettoyage ; une ressource ordinaire absente est supprimée. Tests du domaine sur la règle, et test d'intégration de bout en bout sur un realm.
- Reprendre les tests de suppression (`ImportManagedClientsFullIT`, `ImportManagedNoDeleteIT` et les cas de nettoyage de chaque type).

### US-006-02 — Retrait de l'état distant

**Statut :** À faire
Retirer `import.remote-state.*`, `StateService`, `StateRepository`, `CryptoUtil` et les hooks `usesRemoteState`, `prunesWithoutRemoteState`, `stateScope`, ainsi que `managedState` et `captureState` du `RealmContext`. `RealmSettings.mergedWith` ne garde plus les attributs d'état : ils disparaissent au premier import qui déclare les attributs du realm. Vérifier que Spring Security n'est plus nécessaire une fois `CryptoUtil` retiré.

### US-006-03 — Retrait du cache par checksum

**Statut :** À faire
Retirer `import.cache.*`, `import.behaviors.checksum-with-cache-key`, `import.behaviors.checksum-changed`, `ChecksumService` et `ChecksumChangedException`, avec leurs tests (`AbstractChecksumServiceIT`, `DefaultChecksumKeyIT`, `PerResourceChecksumKeyIT`, `FailWhenChecksumChangedIT`, `ImportRealmCustomImportKeyIT`). La limite « fichier inchangé » du dry-run disparaît de sa documentation.

### US-006-04 — Retrait de l'import parallèle

**Statut :** À faire
Retirer `import.parallel`, le hook `appliesInParallel`, l'application parallèle du moteur et `ImportParallelImportIT`.

### US-006-05 — Mustache, seul moteur de template

**Statut :** À faire
- Retirer la substitution `$(...)` (`import.var-substitution.*`, commons-text si plus utilisé) et `ImportRealmSubstitutionIT`.
- Une variable non définie sans valeur par défaut fait échouer l'import (code de configuration invalide), avec le nom de la variable et du fichier.
- Échappement selon le format du fichier (JSON, YAML) ; plus d'échappement HTML.
- Tests unitaires du contexte (`MustacheContextWithDefaults`) et test d'intégration d'un import variabilisé : variable fournie, valeur par défaut, variable manquante, valeur avec guillemets.

### US-006-06 — Protection des ressources dans le domaine

**Statut :** À faire
`ProtectedResource` (package `model`, avec son cache d'expressions régulières) fait double emploi avec `ProtectionRule` du domaine. Garder une seule règle dans le domaine, compilée une fois, et convertir la déclaration du fichier dans `infrastructure.source`. Une expression régulière invalide est une erreur de configuration, signalée avant toute modification de Keycloak. Option à étudier : restreindre une règle à un type de ressource (champ `type` facultatif, sans changer le comportement des règles existantes).

### US-006-07 — Lecture des fichiers dans `infrastructure.source`

**Statut :** À faire
Déplacer la lecture des fichiers (`KeycloakImportProvider`, `FileUtils`, `ImportResource`, `KeycloakImport`, `RealmImport`, `AuthenticationFlowImport`) et Mustache (`MustacheContextWithDefaults`) dans `infrastructure.source`, derrière un port de l'application. Mustache y devient une étape explicite de la préparation des fichiers, testée seule.

### US-006-08 — Cas d'usage d'import dans `application.importation`

**Statut :** À faire
Remplacer `RealmImportService` (package `service`) par le cas d'usage « appliquer les fichiers » dans `application.importation` : fusion des fichiers par realm, création du realm, application puis nettoyage par le moteur, dry-run. La synchronisation des fédérations d'utilisateurs devient un appel de gateway. `RealmImportReconciliation` perd ce qui relève de l'orchestration.

### US-006-09 — Ligne de commande dans `infrastructure.cli`

**Statut :** À faire
Déplacer les runners (`KeycloakConfigRunner`, `KeycloakConfigNormalizationRunner`) et le binding des propriétés (`properties`) dans `infrastructure.cli`. Les runners ne font plus que lire la configuration, appeler le cas d'usage et traduire le résultat en code de sortie.

### US-006-10 — Derniers paquets hérités

**Statut :** À faire
Ranger ce qui reste hors des couches : `provider.KeycloakProvider`, `resource.ManagementPermissions` et `util.ResteasyUtil` dans `infrastructure.keycloak.sdk` ; `provider.KeycloakExportProvider` avec la normalisation ; `exception`, `configuration` et les `util` restants à leur couche, ou supprimés s'ils ne servent plus. Durcir `ArchitectureTest` : plus aucune classe hors de `domain`, `application`, `infrastructure`, à part la classe de démarrage.

### US-006-11 — Documentation et migration

**Statut :** À faire
Mettre à jour la documentation utilisateur (configuration, concepts, ressources gérées, état, parallélisme, dry-run, `features.md`) et le CHANGELOG. Guide de migration : options retirées et leur remplacement, nouvelle règle de suppression, dry-run recommandé avant le premier import en 7.0.0.
