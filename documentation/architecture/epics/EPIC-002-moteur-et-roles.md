# EPIC-002 — Moteur de réconciliation et migration des rôles

**Statut :** Terminé
**ADR :** [ADR-002](../ADR-002-business-objects-purs.md), [ADR-004](../ADR-004-moteur-de-reconciliation.md), [ADR-006](../ADR-006-conversions-mapstruct.md)

Construire le moteur et le valider sur un premier type de ressource : les rôles de realm et de client. Après cet EPIC, les rôles ne passent plus par `RoleImportService`.

Décisions qui s'appliquent : champs non modélisés transportés dans `UnmodeledAttributes` (ADR-002), fichiers d'un même realm fusionnés avant le calcul du plan (ADR-004).

## Stories

### US-002-01 — Domaine commun de la réconciliation

**Statut :** Terminé
`ResourceKey`, `ManagedMode`, `ProtectionRule` (reprise de `ProtectedResource`), `Change`, `ChangeSet`, `ReconciliationPlan`. Règles de filtrage des suppressions (mode géré, état, protection), testées sans Keycloak.

### US-002-02 — Moteur et contexte par realm

**Statut :** Terminé
`ReconciliationEngine` (ordre par tri topologique, détection des cycles), fusion des fichiers d'un realm en un état voulu avec détection des définitions contradictoires, `RealmContext` en remplacement d'`ExecutionContextHolder` pour les types migrés, et reconciler « legacy » qui enveloppe les `*ImportService` pas encore migrés à leur place dans l'ordre actuel.

### US-002-03 — Rôles : BOs, accès Keycloak et conversions

**Statut :** Terminé
BOs `RealmRole` et `ClientRole`, `RoleApi` et `RoleAdapter`, `RoleKeycloakGateway`, `RoleKeycloakMapper` (MapStruct). Remplace `RoleRepository`. Crée `KeycloakClientFactory`, qui instancie les adapters à partir du client fourni par `KeycloakProvider` (reporté de l'US-001-05). `RoleRepository` reste en place : les groupes, les utilisateurs et les composites l'utilisent encore.

### US-002-04 — Reconcilers des rôles

**Statut :** Terminé
`RealmRoleReconciler` et `ClientRoleReconciler` : création, mise à jour, suppression des rôles absents de la configuration, en respectant `import.managed.role` et les ressources protégées. Les tests d'intégration existants des rôles restent verts.

### US-002-05 — Composites des rôles

**Statut :** Terminé
Migrer les composites (aujourd'hui `rolecomposites/` et `RoleCompositeRepository`), qui dépendent des rôles et des clients. Fait avec `RoleComposites` et `RoleCompositeReconciler`, appliqué une fois tous les rôles du fichier créés ; l'ancien code est supprimé. Une inclusion vers un rôle inexistant lève `UnknownRoleException` (même message).

## Points d'attention

- **Passerelle transitoire** : `LegacyReconciliationBridge` relie l'import historique, fichier par fichier, au moteur. Les créations et mises à jour de rôles ont lieu à leur place habituelle dans l'import de chaque fichier ; les suppressions ont lieu une fois par realm, à la fin, sur l'état voulu fusionné. Elle disparaît avec l'EPIC-003.
- **État distant des rôles** : il est encore écrit fichier par fichier par `StateService`. Avec plusieurs fichiers pour un même realm, il ne garde que les rôles du dernier fichier ; les rôles des autres fichiers ne sont alors plus supprimables (sens prudent). À corriger quand l'écriture de l'état passera au moteur.
- **Tests d'intégration** : 43 appels importent directement par `realmImportService.doImport(...)`, sans démarrer un contexte neuf comme le fait l'outil. Ils réutilisent le contexte du test précédent ; ceux des rôles passent désormais par `runImport`. Les autres sont à aligner au fil de l'EPIC-003.
