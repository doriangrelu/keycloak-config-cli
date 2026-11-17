# EPIC-005 — Normalisation sur le nouveau socle

**Statut :** À faire
**ADR :** [ADR-001](../ADR-001-architecture-en-couches.md), [ADR-002](../ADR-002-business-objects-purs.md)

Faire du mode normalisation (`run.operation=NORMALIZE`) un second cas d'usage du nouveau socle, qui réutilise les BOs et les mappers au lieu de ses propres services.

## Stories

### US-005-01 — Cas d'usage de normalisation

**Statut :** À faire
Déplacer `service/normalize/` dans `application/normalization/`, sur les BOs : la comparaison avec la référence de la version Keycloak devient une règle du domaine.

### US-005-02 — Références des versions récentes

**Statut :** À faire
Ajouter les références (`resources/baseline/`) des versions Keycloak supportées au-delà de 26.0.5.

### US-005-03 — Points en suspens de la normalisation

**Statut :** À faire
Traiter ou rattacher à une issue les `TODO` de `RealmNormalizationService` et `ScopeMappingNormalizationService`.
