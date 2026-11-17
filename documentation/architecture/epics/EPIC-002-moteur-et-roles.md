# EPIC-002 — Moteur de réconciliation et migration des rôles

**Statut :** À faire
**ADR :** [ADR-002](../ADR-002-business-objects-purs.md), [ADR-004](../ADR-004-moteur-de-reconciliation.md), [ADR-006](../ADR-006-conversions-mapstruct.md)

Construire le moteur et le valider sur un premier type de ressource : les rôles de realm et de client. Après cet EPIC, les rôles ne passent plus par `RoleImportService`.

Décisions qui s'appliquent : champs non modélisés transportés dans `UnmodeledAttributes` (ADR-002), fichiers d'un même realm fusionnés avant le calcul du plan (ADR-004).

## Stories

### US-002-01 — Domaine commun de la réconciliation

**Statut :** À faire
`ResourceKey`, `ManagedMode`, `ProtectionRule` (reprise de `ProtectedResource`), `Change`, `ChangeSet`, `ReconciliationPlan`. Règles de filtrage des suppressions (mode géré, état, protection), testées sans Keycloak.

### US-002-02 — Moteur et contexte par realm

**Statut :** À faire
`ReconciliationEngine` (ordre par tri topologique, détection des cycles), fusion des fichiers d'un realm en un état voulu avec détection des définitions contradictoires, `RealmContext` en remplacement d'`ExecutionContextHolder` pour les types migrés, et reconciler « legacy » qui enveloppe les `*ImportService` pas encore migrés à leur place dans l'ordre actuel.

### US-002-03 — Rôles : BOs, accès Keycloak et conversions

**Statut :** À faire
BOs `RealmRole` et `ClientRole`, `RoleApi` et `RoleAdapter`, `RoleKeycloakGateway`, `RoleKeycloakMapper` (MapStruct). Remplace `RoleRepository`. Crée `KeycloakClientFactory`, qui instancie les adapters à partir du client fourni par `KeycloakProvider` (reporté de l'US-001-05).

### US-002-04 — Reconcilers des rôles

**Statut :** À faire
`RealmRoleReconciler` et `ClientRoleReconciler` : création, mise à jour, suppression des rôles absents de la configuration, en respectant `import.managed.role` et les ressources protégées. Les tests d'intégration existants des rôles restent verts.

### US-002-05 — Composites des rôles

**Statut :** À faire
Migrer les composites (aujourd'hui `rolecomposites/` et `RoleCompositeRepository`), qui dépendent des rôles et des clients.
