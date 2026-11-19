# EPIC-004 — Dry-run et rapport des changements

**Statut :** Terminé
**ADR :** [ADR-004](../ADR-004-moteur-de-reconciliation.md), [ADR-005](../ADR-005-erreurs-et-codes-de-sortie.md)

Exploiter le plan de réconciliation pour montrer ce qui va changer avant de le faire.

Code de sortie retenu : `0` sans modification, `10` avec modifications en attente (ADR-005).

## Stories

### US-004-01 — Option dry-run

**Statut :** Terminé
`import.dry-run=true` calcule le plan de chaque realm sans rien modifier, en simulant les créations pour les types qui en dépendent.

### US-004-02 — Rapport des changements

**Statut :** Terminé
Affichage du plan : par realm et par type, les créations, mises à jour (avec les champs modifiés) et suppressions. Format texte lisible, et format JSON pour la CI. Aucun secret affiché (mots de passe, secrets de clients masqués).

### US-004-03 — Code de sortie et documentation

**Statut :** Terminé
Code de sortie `10` quand des modifications sont en attente. Documentation dans `documentation/` (guide et exemple d'utilisation en CI).

## Réalisé

- Le `RealmContext` porte le mode dry-run : le moteur calcule chaque changement comme d'habitude, l'enregistre dans le plan avec la ressource avant et après, mais n'appelle pas le reconciler. Le nettoyage est planifié de la même façon.
- Un realm absent n'est jamais lu en dry-run (`realmIsMissing`) : tout ce que l'import déclare est planifié en création. C'est la simulation retenue : un type planifié après un autre ne voit pas ses changements, puisqu'ils ne sont pas appliqués.
- `RealmImportService` saute en dry-run ce qui écrit hors du moteur : état distant, sommes de contrôle, rafraîchissement du jeton après création, synchronisation des fédérations d'utilisateurs.
- Rapport (`DryRunReport`, `infrastructure.cli`) : par realm, créations, mises à jour avec les noms des champs modifiés (attributs non modélisés compris) et suppressions ; texte ou JSON (`import.dry-run-format`), sur la sortie standard et, au besoin, dans un fichier (`import.dry-run-report-file`). Les valeurs ne sont jamais affichées : aucun secret ne peut fuiter.
- Code de sortie `10` si des changements sont en attente, `0` sinon.
- Un realm inchangé ne produit aucun changement (vérifié par `ImportDryRunIT`). Limite connue : l'autorisation des clients apparaît toujours en mise à jour, ses paramètres substituables n'étant résolus qu'à l'application.

