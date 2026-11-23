# EPIC-006 — Simplification : les fichiers comme seule vérité

**Statut :** En cours
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

**Statut :** Terminé
Retirer les modes gérés (`import.managed.*`, `ManagedMode`, `DeletionPolicy` réduite aux garanties) : un type déclaré est nettoyé, un type non déclaré n'est pas touché. Les garanties restent dans le code : utilisateurs jamais supprimés, ressources techniques (`isOwnedByKeycloak`), ressources protégées, types déclarés en partie.

- Vérifier type par type que `isOwnedByKeycloak` couvre bien ce que Keycloak crée, en particulier pour les types qui étaient en `no-delete` par défaut (client scopes, scope mappings).
- Test de garantie pour chaque type nettoyé : une ressource technique, une ressource protégée par égalité et une protégée par expression régulière, absentes des fichiers, survivent au nettoyage ; une ressource ordinaire absente est supprimée. Tests du domaine sur la règle, et test d'intégration de bout en bout sur un realm.
- Reprendre les tests de suppression (`ImportManagedClientsFullIT`, `ImportManagedNoDeleteIT` et les cas de nettoyage de chaque type).

### US-006-02 — Retrait de l'état distant

**Statut :** Terminé
Retirer `import.remote-state.*`, `StateService`, `StateRepository`, `CryptoUtil` et les hooks `usesRemoteState`, `prunesWithoutRemoteState`, `stateScope`, ainsi que `managedState` et `captureState` du `RealmContext`. `RealmSettings.mergedWith` ne garde plus les attributs d'état : ils disparaissent au premier import qui déclare les attributs du realm. Vérifier que Spring Security n'est plus nécessaire une fois `CryptoUtil` retiré.

### US-006-03 — Retrait du cache par checksum

**Statut :** Terminé
Retirer `import.cache.*`, `import.behaviors.checksum-with-cache-key`, `import.behaviors.checksum-changed`, `ChecksumService` et `ChecksumChangedException`, avec leurs tests (`AbstractChecksumServiceIT`, `DefaultChecksumKeyIT`, `PerResourceChecksumKeyIT`, `FailWhenChecksumChangedIT`, `ImportRealmCustomImportKeyIT`). La limite « fichier inchangé » du dry-run disparaît de sa documentation.

### US-006-04 — Retrait de l'import parallèle

**Statut :** Terminé
Retirer `import.parallel`, le hook `appliesInParallel`, l'application parallèle du moteur et `ImportParallelImportIT`.

### US-006-05 — Mustache, seul moteur de template

**Statut :** Terminé
- Retirer la substitution `$(...)` (`import.var-substitution.*`, commons-text si plus utilisé) et `ImportRealmSubstitutionIT`.
- Une variable non définie sans valeur par défaut fait échouer l'import (code de configuration invalide), avec le nom de la variable et du fichier.
- Échappement selon le format du fichier (JSON, YAML) ; plus d'échappement HTML.
- Tests unitaires du contexte (`MustacheContextWithDefaults`) et test d'intégration d'un import variabilisé : variable fournie, valeur par défaut, variable manquante, valeur avec guillemets.

### US-006-06 — Protection des ressources dans le domaine

**Statut :** Terminé
`ProtectedResource` (package `model`, avec son cache d'expressions régulières) fait double emploi avec `ProtectionRule` du domaine. Garder une seule règle dans le domaine, compilée une fois, et convertir la déclaration du fichier dans `infrastructure.source`. Une expression régulière invalide est une erreur de configuration, signalée avant toute modification de Keycloak. Option à étudier : restreindre une règle à un type de ressource (champ `type` facultatif, sans changer le comportement des règles existantes).

### US-006-07 — Lecture des fichiers dans `infrastructure.source`

**Statut :** Terminé
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

## Réalisé

### US-006-01

- `ManagedMode`, `import.managed.*` et `ClientAuthorizationReconciler.Modes` sont retirés. `DeletionPolicy` ne garde que l'état distant (retiré en US-006-02) et les règles de protection. Dans une ressource déclarée, ce qu'elle omet est retiré : rôles d'un groupe, rôles d'un scope mapping, textes d'une langue, ressources, politiques et scopes de l'autorisation d'un client.
- Ressources techniques, désormais reconnues par le domaine pour chaque type nettoyé, sans dépendre du mode `no-delete` qui les protégeait par défaut :
  - clients intégrés par leur `clientId` (et non plus seulement par leur nom affiché `${client_…}`), clients `<realm>-realm` du realm master ;
  - rôles `offline_access`, `uma_authorization` et `default-roles-<realm>` par leur nom, rôles des clients de Keycloak ;
  - client scopes intégrés, y compris `service_account` (Keycloak 26.5), qui n'est ni un scope par défaut ni optionnel du realm ;
  - scope mappings `offline_access` et `account-console` ;
  - required actions intégrées (on les désactive au lieu de les supprimer) ;
  - composants `KeyProvider`, `ClientRegistrationPolicy` et `UserProfileProvider` : sans cette règle, le retrait de l'état distant aurait permis de supprimer les clés du realm.
- Tests de garantie : `TechnicalResourcesTest` et `DeletionPolicyTest` (égalité et expression régulière) dans le domaine ; `ImportCleanupGuaranteesIT`, sans état distant, sur un vrai Keycloak : après un second import qui ne déclare plus que `app`, les ressources orphelines de chaque type sont supprimées, tandis que l'utilisateur, toutes les ressources techniques et les ressources protégées par `EQUALS` et par `PATTERN` restent.
- `ImportManagedNoDeleteIT` est supprimé avec le mode qu'il testait.

### US-006-02

- Retirés : `import.remote-state.*`, `StateService`, `StateRepository`, `CryptoUtil` et la dépendance Spring Security ; les hooks `stateScope`, `usesRemoteState` et `prunesWithoutRemoteState` des reconcilers ; `captureState`, `managedState` et `isRemoteStateEnabled` du `RealmContext` ; `ClientAuthorizationGateway.Deletions`. `DeletionPolicy` ne porte plus que les règles de protection.
- `Resource.stateName()` devient `protectedName()` : c'est le nom que les règles de protection comparent.
- Les attributs de l'ancien état distant sont supprimés par tout import, déclarés ou non (`RealmSettings.mergedWith`), écart assumé avec la première version de l'ADR-007, qui ne les supprimait qu'avec des attributs déclarés. Les attributs de checksum restent jusqu'à US-006-03.
- Deux comportements changent avec l'état distant : les langues des message bundles absentes des fichiers sont supprimées (elles ne l'étaient qu'avec l'état), et toutes les ressources non déclarées de l'autorisation d'un client le sont aussi.
- `ImportManagedWithEncryptedStateIT`, `CryptoUtilTest` et `ImportRemoteStatePropertiesTest` sont supprimés ; `ImportCleanupGuaranteesIT` tourne avec la configuration par défaut.
- Deux cas que l'état distant épargnait, tranchés le 20/11/2026 :
  - **sous-composants** : le SDK Keycloak ne distingue pas `subComponents` absent de `subComponents: {}`. Un composant qui déclare au moins un sous-composant a exactement ceux-là ; un composant qui n'en déclare aucun garde les siens, dont les mappers que Keycloak crée avec un LDAP. On ne peut plus supprimer tous les sous-composants d'un coup ;
  - **ressources d'autorisation créées à l'exécution** par l'application (API Protection) : quand le client permet la gestion distante des ressources (`allowRemoteResourceManagement`, actif par défaut dans Keycloak), les ressources non déclarées sont gardées, sauf la `Default Resource`, ainsi que les scopes qu'elles utilisent (Keycloak refuse de supprimer un scope utilisé). Une ressource retirée des fichiers n'est donc plus supprimée dans ce cas : il faut `allowRemoteResourceManagement: false` pour que les fichiers fassent foi.
- Bug révélé par le retrait de l'état : un client déclaré par son seul nom, créé pendant l'import avec un client id généré, était supprimé par le nettoyage du même import. Nouveau hook `Reconciler.isDeclaredByAnotherKey`, implémenté par `ClientReconciler`.

### US-006-03

- Retirés : `import.cache.*`, `import.behaviors.checksum-with-cache-key`, `import.behaviors.checksum-changed`, `ChecksumService`, `ChecksumChangedException`, le code de sortie `5`, le checksum des fichiers lus et la dépendance `commons-codec`. Chaque import compare tous les fichiers avec Keycloak : une modification manuelle est défaite au prochain import, et le dry-run la montre toujours.
- Tous les attributs que keycloak-config-cli écrivait dans les realms (préfixe `io.github.doriangrelu.keycloak.config.` : état et checksums) sont supprimés par tout import. `RealmSettings.mergedWith` ne garde plus d'attribut propre à l'outil.
- Tests supprimés avec le cache : `AbstractChecksumServiceIT`, `DefaultChecksumKeyIT`, `PerResourceChecksumKeyIT`, `FailWhenChecksumChangedIT`, `ImportRealmCustomImportKeyIT`. La page `state-management.md`, qui décrivait le cache (avec des propriétés `import.state` et `import.force` qui n'existaient pas), est supprimée.

### US-006-04

- Retirés : `import.parallel`, le hook `Reconciler.appliesInParallel` (et ses six surcharges), `RealmContext.isParallel`, l'application en `parallelStream` du moteur, `ImportParallelImportIT` et la page `parallel-processing.md`.
- `RealmContext` n'est plus partagé entre threads : ses verrous (`synchronized`, listes synchronisées, `volatile`) sont retirés. Constructeurs : `RealmContext(realm)` et `RealmContext(realm, dryRun)`.

### US-006-05

- Retirés : `import.var-substitution.*`, la substitution `$(...)` de commons-text (`env:`, `sys:`, `file:`…), la dépendance `commons-text`, `ImportRealmSubstitutionIT` et ses fichiers. `ImportUserFederationIT` passe l'hôte et le port du LDAP de test par des variables Mustache.
- `MustacheTemplates` rend un fichier : une variable non définie et sans valeur par défaut fait échouer l'import (`InvalidImportException`, code de configuration invalide) avec le nom de la variable, le fichier et la ligne.
- Échappement : les valeurs sont échappées comme le contenu d'une chaîne entre guillemets doubles (`"`, `\`, retours à la ligne, caractères de contrôle). Cet échappement est le même en JSON et en YAML : un seul échappeur couvre les deux formats, à condition qu'en YAML la variable soit écrite entre guillemets doubles quand sa valeur peut contenir ces caractères. Plus d'échappement HTML. `{{{nom}}}` insère une valeur telle quelle (un nombre, un fragment JSON).
- `MustacheTemplates` et `MustacheContextWithDefaults` restent provisoirement dans le paquet `provider`, à côté du lecteur de fichiers : `infrastructure` dépend encore de `provider`, et l'y placer créerait un cycle. US-006-07 les déplace avec le lecteur dans `infrastructure.source`.
- Tests : `MustacheTemplatesTest`, `MustacheContextWithDefaultsTest`, `ImportRealmTemplateIT` (variable fournie, valeur par défaut, fragment brut, YAML, variable manquante, valeur avec guillemets et antislash).

### US-006-06

- `ProtectedResource` (package `model`, avec son cache d'expressions régulières partagé) est supprimé. Une seule règle, `ProtectionRule`, déplacée de `domain.common` vers `domain.reconcile` à côté de `DeletionPolicy` et de `ResourceType` (sinon cycle entre les deux paquets). Elle compile son expression une fois, à sa création.
- Une expression invalide ou une règle sans `matcher` lève `InvalidProtectionRuleException` (exception du domaine, code de configuration invalide). La règle est créée à la lecture du fichier (`RealmImport.setProtectedResources`) : l'erreur survient avant toute modification de Keycloak, avec le nom du fichier.
- Option retenue : champ `type` facultatif, une valeur de `ResourceType`. Une règle sans type protège tous les types, comme avant. `DeletionPolicy.allowsDeletionOf(type, name)` et `RealmContext.isProtected(type, name)` reçoivent le type ; le moteur passe celui du reconciler, `GroupReconciler` celui des groupes pour les sous-groupes retirés.
- La déclaration du fichier (`RealmImport.ProtectedResourceDeclaration`) reste provisoirement dans `model` avec `RealmImport` : US-006-07 et 08 déplacent la lecture et l'analyse des fichiers.
- Tests : `ProtectionRuleTest`, `DeletionPolicyTest` et un cas de `ImportCleanupGuaranteesIT` (règle restreinte aux clients, expression invalide sans aucune modification).

### US-006-07

- Port de l'application `application.importation.port.ImportFileSource` : `read(locations)` rend des `ImportFile` (emplacement configuré, nom du fichier, contenu), documents bruts préparés, sans Jackson ni SDK Keycloak. `InvalidImportException` devient une exception de l'application (`application.importation`, sous-classe d'`ApplicationException`) : la source comme l'analyse des fichiers la lèvent, sans dépendance entre `infrastructure.source` et `infrastructure.keycloak`.
- `infrastructure.source` :
  - `FileLocations` lit les fichiers d'une liste d'emplacements (motifs, expressions régulières, zip, class path, URL avec authentification, fichiers cachés, exclusions, tri par nom, fichiers vides ignorés). Il remplace le code dupliqué de `KeycloakImportProvider` et de `KeycloakExportProvider`.
  - `ImportFileReader` implémente le port : lecture, puis préparation, une étape explicite qui rend le fichier avec `MustacheTemplates` quand `import.mustache.enabled` est actif.
  - `ExportFileReader` lit les exports à normaliser avec le même `FileLocations`, sans template.
  - `SourceConfiguration` crée ces beans selon l'opération (import ou normalisation). `FileUtils`, `MustacheTemplates` et `MustacheContextWithDefaults` y sont déplacés.
- `infrastructure.keycloak` : `RealmImport` et `AuthenticationFlowImport` sont déplacés dans `importation` (ce ne sont plus des beans Spring) ; `RealmImportParser` et `normalization.RealmExportParser` analysent les documents avec `mapping.RealmDocuments` (JSON ou YAML multi-documents, propriétés inconnues refusées).
- Supprimés : `KeycloakImportProvider`, `KeycloakExportProvider`, `KeycloakImport`, `ImportResource`, `exception.InvalidImportException` et le paquet `model`. `ExitCodeResolver` n'a plus de cas dédié : l'exception est une `ApplicationException`.
- Les deux lanceurs lisent puis analysent tous les fichiers avant de modifier Keycloak ; ils seront déplacés dans `infrastructure.cli` en US-006-09.
- Correction : les exclusions ne s'appliquaient jamais sous Windows (chemin avec antislashs et lettre de lecteur comparé à des motifs Ant).
- Tests : `FileLocationsTest`, `ImportFileReaderTest` (étape Mustache seule), `RealmDocumentsTest` ; `KeycloakImportProviderIT` devient `ImportFileReaderIT` ; `KeycloakExportProviderTest`, test boîte blanche à base de mocks, est remplacé par `FileLocationsTest`.

