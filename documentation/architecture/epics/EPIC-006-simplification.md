# EPIC-006 — Simplification : les fichiers comme seule vérité

**Statut :** À faire
**ADR :** [ADR-007](../ADR-007-fichiers-seule-verite.md), [ADR-004](../ADR-004-moteur-de-reconciliation.md)

Recentrer l'outil sur son besoin : créer et mettre à jour les realms décrits par les fichiers, supprimer ce qui n'y figure plus, et variabiliser les valeurs avec Mustache. Les options qui compliquent le moteur ou contredisent ce besoin sont retirées.

Chaque story est livrée seule, build et tests d'intégration verts, comme pendant l'EPIC-003.

## Stories

### US-006-01 — Règle de nettoyage unique

**Statut :** À faire
Retirer les modes gérés (`import.managed.*`, `ManagedMode`) : un type déclaré est nettoyé, un type non déclaré n'est pas touché. Les exceptions restent dans le code : utilisateurs jamais supprimés, ressources de Keycloak (`isOwnedByKeycloak`), ressources protégées, types déclarés en partie. Vérifier type par type que `isOwnedByKeycloak` couvre bien ce que Keycloak crée, en particulier pour les types qui étaient en `no-delete` par défaut (client scopes, scope mappings). Reprendre les tests de suppression (`ImportManagedClientsFullIT`, `ImportManagedNoDeleteIT` et les cas de nettoyage de chaque type).

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

### US-006-06 — Documentation et migration

**Statut :** À faire
Mettre à jour la documentation utilisateur (configuration, concepts, ressources gérées, état, parallélisme, dry-run, `features.md`) et le CHANGELOG. Guide de migration : options retirées et leur remplacement, nouvelle règle de suppression, dry-run recommandé avant le premier import en 7.0.0.
