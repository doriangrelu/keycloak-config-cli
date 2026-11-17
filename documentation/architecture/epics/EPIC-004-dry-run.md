# EPIC-004 — Dry-run et rapport des changements

**Statut :** À faire
**ADR :** [ADR-004](../ADR-004-moteur-de-reconciliation.md), [ADR-005](../ADR-005-erreurs-et-codes-de-sortie.md)

Exploiter le plan de réconciliation pour montrer ce qui va changer avant de le faire.

Code de sortie retenu : `0` sans modification, `10` avec modifications en attente (ADR-005).

## Stories

### US-004-01 — Option dry-run

**Statut :** À faire
`import.dry-run=true` calcule le plan de chaque realm sans rien modifier, en simulant les créations pour les types qui en dépendent.

### US-004-02 — Rapport des changements

**Statut :** À faire
Affichage du plan : par realm et par type, les créations, mises à jour (avec les champs modifiés) et suppressions. Format texte lisible, et format JSON pour la CI. Aucun secret affiché (mots de passe, secrets de clients masqués).

### US-004-03 — Code de sortie et documentation

**Statut :** À faire
Code de sortie `10` quand des modifications sont en attente. Documentation dans `documentation/` (guide et exemple d'utilisation en CI).
