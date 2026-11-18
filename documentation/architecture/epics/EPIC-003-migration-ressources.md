# EPIC-003 — Migration des autres types de ressources

**Statut :** En cours
**ADR :** [ADR-002](../ADR-002-business-objects-purs.md), [ADR-003](../ADR-003-acces-keycloak-sdk.md), [ADR-004](../ADR-004-moteur-de-reconciliation.md)

Migrer chaque `*ImportService` restant vers un reconciler, du plus simple au plus complexe. Une story par type : BOs, `*Api` et `*Adapter`, gateway et mapper, reconciler, puis suppression de l'ancien service et de son repository.

## Ordre proposé

| Ordre | Type de ressource | Service actuel |
|---|---|---|
| 1 | Paramètres du realm, politique OTP, message bundles | `RealmImportService`, `OtpPolicyImportService`, `MessageBundleImportService` (message bundles migrés) |
| 2 | Actions requises | `RequiredActionsImportService` (migré) |
| 3 | Client scopes | `ClientScopeImportService` (migré, sauf les scopes par défaut du realm) |
| 4 | Clients | `ClientImportService` (migré, sauf les overrides de flux) |
| 5 | Groupes et groupes par défaut | `GroupImportService` (migré), `DefaultGroupsImportService` |
| 6 | Scope mappings et client scope mappings | `ScopeMappingImportService`, `ClientScopeMappingImportService` |
| 7 | Composants et profil utilisateur | `ComponentImportService`, `UserProfileImportService` |
| 8 | Utilisateurs | `UserImportService` (migré) |
| 9 | Fournisseurs d'identité et leurs mappers | `IdentityProviderImportService` (migré) |
| 10 | Politiques de clients | `ClientPoliciesImportService` (migré) |
| 11 | Flux d'authentification, exécutions, configurations d'authentificateurs | `AuthenticationFlowsImportService`, `ExecutionFlowsImportService`, `AuthenticatorConfigImportService` |
| 12 | Autorisations des clients | `ClientAuthorizationImportService` |

Les flux d'authentification (avec `UsedAuthenticationFlowWorkaroundFactory`) et les autorisations des clients viennent en dernier : ce sont les plus gros et les plus risqués.

## Fin de l'EPIC

Quand tous les types sont migrés : suppression du reconciler « legacy », de `RealmImportService.cleanRealm`, d'`ExecutionContextHolder`, de `ThreadHelper` et des packages hérités ; l'exclusion d'`ArchitectureTest` est retirée.

## Avancement

| Type | Statut | Remarques |
|---|---|---|
| Actions requises | Terminé | Remplacement complet des champs comme avant, `config` gardé s'il n'est pas donné, recréation si le `providerId` change. Appliquées à la place des rôles dans l'import, donc avant les utilisateurs. |
| Message bundles | Terminé | En mode `full`, les clés absentes d'une langue déclarée sont retirées ; en `no-delete`, gardées. Une langue entière n'est supprimée qu'avec l'état distant, comme avant. Le contrat des reconcilers reçoit le contexte à la fusion, et un type peut exiger l'état distant pour supprimer. |
| Client scopes | Terminé | Appliqués avant les clients, comme avant. Champs non donnés gardés, attributs et configuration des mappers fusionnés clé par clé, mappers déclarés = ensemble exact. Suppressions selon le seul mode géré (sans état distant), scopes par défaut du realm épargnés. La gestion des scopes par défaut du realm reste dans `ClientScopeImportService` jusqu'aux paramètres du realm. Le moteur sait appliquer un sous-ensemble de types à différents endroits de l'import. |
| Clients | Terminé | Champs utilisés par une règle typés (identité, attributs, scopes, mappers, secret, type d'accès), les autres en attributs non modélisés. Client donné par son seul nom résolu vers son `clientId`, validation des paramètres d'autorisation sur le fichier, clients système jamais créés, `admin-permissions` laissé à Keycloak avec FGAP v2. Les mappers voyagent avec la mise à jour du client, comme avant ; les scopes sont retirés puis ajoutés. Les overrides de flux restent dans `ClientImportService` jusqu'aux flux d'authentification. |
| Groupes | Terminé | Un groupe par chemin (`/parent/enfant`), parents créés avant leurs enfants et appliqués à la place des rôles. Attributs déclarés remplaçant les existants, comme avant ; rôles du realm et des clients remplacés en mode `full`, complétés en `no-delete`. Un groupe qui déclare ses sous-groupes perd ceux qui n'y figurent plus (sauf groupe protégé). Retirer un rôle disparu n'est pas une erreur, en ajouter un inconnu l'est. Suppressions selon le seul mode géré. Les groupes par défaut restent dans `DefaultGroupsImportService` jusqu'aux paramètres du realm. `ImportTracking` disparaît. |
| Utilisateurs | Terminé | Jamais supprimés. Seuls les utilisateurs déclarés sont lus : un utilisateur est retrouvé par son nom, sinon par son email, son prénom et son nom, puis renommé. Avec l'email comme nom d'utilisateur, l'email sert d'identité et un nom différent est refusé (hors comptes de service). Rôles et groupes déclarés exacts, déclarés ou non, sauf les rôles par défaut du realm (`remove-default-role-from-user`) et les rôles des clients de Keycloak, gardés tant qu'ils ne sont pas déclarés. Identifiants `initial` posés à la création seulement ; mot de passe refusé par l'historique : nouvel essai sans mot de passe. Attributs des utilisateurs fédérés laissés en l'état sur demande. |
| Fournisseurs d'identité | Terminé | Deux types : les fournisseurs (par alias) puis leurs mappers (par alias du fournisseur et nom). Champs non donnés gardés, `config` déclarée remplaçant l'existante, comme avant. Flux de connexion non déclarés réinitialisés : premier login sur `first broker login`, post-login retiré. Suppressions selon le seul mode géré (`identity-provider`, `identity-provider-mapper`), sans état distant, désormais en fin d'import. |
| Politiques de clients | Terminé | Deux types : les profils puis les politiques, par nom, chacun remplacé en entier ; Keycloak n'écrivant que des listes complètes, chaque changement relit et réécrit la liste, un élément à la fois. Les listes déclarées sont exactes, sans mode géré ni état distant. Un fichier qui déclare l'une des deux déclare l'autre, vide si absente : les politiques désignent des profils. Correction : un fichier qui ne déclare ni l'une ni l'autre ne les remet plus à zéro. |
