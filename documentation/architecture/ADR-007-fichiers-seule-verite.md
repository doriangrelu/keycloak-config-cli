# ADR-007 — Les fichiers d'import comme seule vérité

## Statut

Accepté le 20/11/2026 — refonte 7.0.0. Remplace, dans l'[ADR-004](ADR-004-moteur-de-reconciliation.md), le filtre des suppressions par mode géré et par état distant.

## Contexte

Le besoin de l'outil est simple :

1. importer les realms décrits par les fichiers : créer ce qui manque, mettre à jour ce qui existe ;
2. à la fin, supprimer ce qui existe dans Keycloak mais ne figure plus dans les fichiers ;
3. variabiliser certaines valeurs des fichiers selon l'environnement.

L'outil hérité offre bien plus, et ces options compliquent le moteur ou contredisent ce besoin :

| Option | Effet | Problème |
|---|---|---|
| Cache par checksum (`import.cache.*`, `import.behaviors.checksum-*`) | Un fichier inchangé n'est pas importé | Une modification faite à la main dans Keycloak n'est jamais corrigée ; le dry-run annonce « rien à faire » alors que Keycloak a dérivé |
| État distant (`import.remote-state.*`), chiffré ou non | Seules les ressources créées par l'outil sont supprimées | Contredit « les fichiers sont la vérité » ; écrit des attributs techniques dans chaque realm ; le chiffrement repose sur AES-CBC sans authentification |
| Modes gérés par type (`import.managed.*`, 16 propriétés) | `full` ou `no-delete` pour chaque type | Les défauts sont incohérents (`client-scope` et `scope-mapping` en `no-delete`, le reste en `full`) ; la règle de suppression dépend de la configuration au lieu d'être lisible dans les fichiers |
| Import parallèle (`import.parallel`) | Les changements d'un même type sont appliqués en parallèle | Gain faible, nombre de requêtes vers Keycloak non borné |
| Substitution `$(...)` (`import.var-substitution.*`) | Variables via commons-text : `env:`, `sys:`, `file:`… | Fait double emploi avec Mustache, ajouté en 7.0.0 ; deux syntaxes et un ordre d'évaluation à expliquer |

## Décision

### Règle de nettoyage unique

Pour chaque realm importé, les fichiers sont la vérité :

- **Un type déclaré** par au moins un fichier du realm (clé présente, même avec une liste vide) est nettoyé : toute ressource de ce type qui existe dans Keycloak et qu'aucun fichier ne déclare est supprimée.
- **Un type non déclaré** n'est pas touché. Un fichier sans clé `groups` ne supprime aucun groupe ; `groups: []` les supprime tous.

Les exceptions sont fixées dans le code, pas dans la configuration :

| Exception | Raison |
|---|---|
| Les utilisateurs ne sont jamais supprimés | Un realm contient les vrais utilisateurs, qui ne sont pas dans les fichiers |
| Les ressources de Keycloak ne sont jamais supprimées (`isOwnedByKeycloak`) : clients et rôles par défaut, flows et configs built-in, client scopes par défaut du realm… | Keycloak les recrée ou en dépend |
| Les ressources protégées (`protectedResources` du fichier) ne sont jamais supprimées | Garde-fou pour ce qui vit dans le realm hors des fichiers (un client créé par une autre équipe) |
| Les types déclarés en partie (paramètres du realm, flow bindings, client scopes et groupes par défaut, composites, flow bindings et autorisation des clients) ne sont pas nettoyés comme des listes | Un fichier n'en déclare que ce qu'il veut changer |

### Options retirées

- Cache par checksum : `import.cache.*`, `import.behaviors.checksum-with-cache-key`, `import.behaviors.checksum-changed`. Chaque import compare tous les fichiers avec Keycloak ; le moteur n'appelle Keycloak en écriture qu'en cas de différence.
- État distant : `import.remote-state.*`. Les attributs d'état et de checksum déjà écrits dans les realms sont supprimés au premier import qui déclare les attributs du realm, comme tout attribut absent des fichiers.
- Modes gérés : `import.managed.*`.
- Import parallèle : `import.parallel`.
- Substitution `$(...)` : `import.var-substitution.*`.

`ImportConfigProperties` refuse les propriétés inconnues (`ignoreUnknownFields = false`) : une configuration qui garde une option retirée échoue au démarrage avec un code de configuration invalide, au lieu d'être ignorée en silence.

### Mustache, seul moteur de template

- Les variables viennent de `import.mustache.variables.*` : arguments, variables d'environnement (`IMPORT_MUSTACHE_VARIABLES_<NOM>`) ou fichier de configuration. Une valeur par défaut s'écrit `{{nom:défaut}}`.
- **Une variable non définie et sans valeur par défaut est une erreur de configuration** : une valeur vide passée en silence (une `redirectUri` vide, un secret vide) est pire qu'un échec.
- Les valeurs sont échappées selon le format du fichier : une valeur contenant un guillemet ou un antislash ne doit ni casser le JSON ni changer de sens. L'échappement HTML par défaut de jmustache (`&` devient `&amp;`) est désactivé.
- `import.mustache.enabled` reste, désactivé par défaut : un fichier existant peut contenir `{{` (un message bundle, un thème), qui ne doit pas être interprété sans le demander.

### Ce qui reste

- Le dry-run : avec un nettoyage qui supprime tout ce qui manque, relire les suppressions avant de les appliquer devient indispensable. Sans cache, il compare toujours tous les fichiers.
- Les codes de sortie (ADR-005).
- Les ressources protégées.
- La normalisation, mode à part (`run.operation=NORMALIZE`).
- Les comportements propres à un type : `import.behaviors.remove-default-role-from-user`, `skip-attributes-for-federated-user`, `sync-user-federation`, et `import.validate`.

## Conséquences

- **Changement incompatible.** Qui comptait sur `no-delete` ou sur l'état distant pour garder des ressources hors des fichiers doit les déclarer ou les protéger. Le guide de migration le dit, et recommande un dry-run avant le premier import en 7.0.0.
- Le moteur se simplifie : les hooks `usesRemoteState`, `prunesWithoutRemoteState`, `stateScope` et `appliesInParallel` disparaissent, ainsi que `managedState` et `captureState` du `RealmContext`. Il reste `prunes` et `isOwnedByKeycloak`.
- La règle de suppression se lit dans les fichiers et se teste sans configuration particulière.
- Un import compare toujours tous les fichiers : il est un peu plus long qu'avec le cache, mais toujours correct.
- Les tests d'intégration des options retirées disparaissent (`*Checksum*IT`, `ImportManaged*IT`, `ImportParallelImportIT`, `ImportRealmCustomImportKeyIT`, `ImportRealmSubstitutionIT`) ; ceux qui portaient sur la suppression sont repris pour la nouvelle règle.
