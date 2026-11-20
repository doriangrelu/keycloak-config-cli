# EPIC-005 — Normalisation sur le nouveau socle

**Statut :** Terminé
**ADR :** [ADR-001](../ADR-001-architecture-en-couches.md), [ADR-002](../ADR-002-business-objects-purs.md)

Faire du mode normalisation (`run.operation=NORMALIZE`) un second cas d'usage du nouveau socle, qui réutilise les BOs et les mappers au lieu de ses propres services.

## Stories

### US-005-01 — Cas d'usage de normalisation

**Statut :** Terminé
Déplacer `service/normalize/` dans `application/normalization/`, sur les BOs : la comparaison avec la référence de la version Keycloak devient une règle du domaine.

### US-005-02 — Références des versions récentes

**Statut :** Terminé
Ajouter les références (`resources/baseline/`) des versions Keycloak supportées au-delà de 26.0.5.

### US-005-03 — Points en suspens de la normalisation

**Statut :** Terminé
Traiter ou rattacher à une issue les `TODO` de `RealmNormalizationService` et `ScopeMappingNormalizationService`.

## Réalisé

- **Filet d'abord.** `NormalizationGoldenIT` normalise des exports complets (deux realms personnalisés construits sur les références 26.0.5 et 26.5.3, et les exports du realm master 19.0.3, 25.0.5, 26.0.0 et 26.3.3) et compare la sortie à des fichiers attendus, sans tenir compte de l'ordre. La sortie de l'ancien code a été figée avant la réécriture : chaque écart de la nouvelle sortie a été relu. Mode `-Dnormalization.golden.update=true` pour régénérer les fichiers attendus.
- **Deux bugs de l'ancien code trouvés par le filet**, corrigés : tout export du realm master échouait (client sans `protocol`), et un export d'une version sans référence exacte (26.0.0) échouait faute de `fallback-version`.
- **US-005-01.** `service/normalize/` (14 services sur Javers) est remplacé par :
  - domaine : `ReferenceComparison` (garder ce qui diffère de la référence, signaler ce qui manque) et les minimisations `RealmSettings`, `RealmFlowBindings` et `Client.withoutValuesOf`, `Client.hasValuesDifferentFrom`, `Component.hasSameContentAs`, `UnmodeledAttributes.differingFrom` ;
  - application : le cas d'usage `RealmNormalization` et le port `ReferenceRealmGateway` ;
  - infrastructure (`infrastructure.keycloak.normalization`) : `RealmExportReader` (export vers BOs, sans ids, avec le nettoyage), `RealmExportWriter` (BOs vers fichier d'import), `ReferenceRealmKeycloakGateway`, `BaselineProvider` (déplacé) et `RealmExportNormalizer`, appelé par le runner.
  - Javers n'est plus une dépendance.
- **Écarts assumés avec l'ancienne sortie**, visibles dans les fichiers attendus : un client par défaut est comparé à sa propre référence et non au client générique ; les attributs de client sont comparés un à un ; un client dont la seule différence est une valeur absente de l'export n'est plus émis (un import ne peut pas la retirer) ; plus de `containerId` ; les flows built-in modifiés sont gardés ; les composants (fédération LDAP...) sont gardés avec tout leur arbre, sauf les key providers (clés générées) et le profil utilisateur ; un groupe est gardé avec l'arbre de son groupe racine ; les valeurs multiples de la configuration des composants sont comparées sans ordre.
- **US-005-02.** Référence 26.5.3 (version du SDK du projet), générée par `kc.sh export` d'un realm vierge et d'un client créé avec les valeurs par défaut, sans key providers ni secret. Choix de la référence quand la version exacte manque : la plus proche (inférieure de même majeure, sinon supérieure de même majeure, sinon inférieure), `normalization.fallback-version` restant prioritaire.
- **US-005-03.** Le `TODO` de `ScopeMappingNormalizationService` est résolu : la clé `ScopeMapping.Key` distingue les scope mappings portés par un client de ceux portés par un client scope (seul l'un d'eux était gardé). Les deux idées de `RealmNormalizationService` (ignorer d'autres valeurs par défaut, ignorer des clients par expression régulière) sont reportées dans `TASKS.md`, section « Après la refonte ».
- Documentation utilisateur réécrite (`documentation/advanced/normalization.md`) : l'ancienne décrivait une connexion à Keycloak et un export des utilisateurs qui n'existent pas.

