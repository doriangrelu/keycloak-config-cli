# EPIC-003 — Migration des autres types de ressources

**Statut :** À faire
**ADR :** [ADR-002](../ADR-002-business-objects-purs.md), [ADR-003](../ADR-003-acces-keycloak-sdk.md), [ADR-004](../ADR-004-moteur-de-reconciliation.md)

Migrer chaque `*ImportService` restant vers un reconciler, du plus simple au plus complexe. Une story par type : BOs, `*Api` et `*Adapter`, gateway et mapper, reconciler, puis suppression de l'ancien service et de son repository.

## Ordre proposé

| Ordre | Type de ressource | Service actuel |
|---|---|---|
| 1 | Paramètres du realm, politique OTP, message bundles | `RealmImportService`, `OtpPolicyImportService`, `MessageBundleImportService` |
| 2 | Actions requises | `RequiredActionsImportService` |
| 3 | Client scopes | `ClientScopeImportService` |
| 4 | Clients | `ClientImportService` |
| 5 | Groupes et groupes par défaut | `GroupImportService`, `DefaultGroupsImportService` |
| 6 | Scope mappings et client scope mappings | `ScopeMappingImportService`, `ClientScopeMappingImportService` |
| 7 | Composants et profil utilisateur | `ComponentImportService`, `UserProfileImportService` |
| 8 | Utilisateurs | `UserImportService` |
| 9 | Fournisseurs d'identité et leurs mappers | `IdentityProviderImportService` |
| 10 | Politiques de clients | `ClientPoliciesImportService` |
| 11 | Flux d'authentification, exécutions, configurations d'authentificateurs | `AuthenticationFlowsImportService`, `ExecutionFlowsImportService`, `AuthenticatorConfigImportService` |
| 12 | Autorisations des clients | `ClientAuthorizationImportService` |

Les flux d'authentification (avec `UsedAuthenticationFlowWorkaroundFactory`) et les autorisations des clients viennent en dernier : ce sont les plus gros et les plus risqués.

## Fin de l'EPIC

Quand tous les types sont migrés : suppression du reconciler « legacy », de `RealmImportService.cleanRealm`, d'`ExecutionContextHolder`, de `ThreadHelper` et des packages hérités ; l'exclusion d'`ArchitectureTest` est retirée.
