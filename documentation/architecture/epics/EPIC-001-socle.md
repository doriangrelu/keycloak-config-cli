# EPIC-001 — Socle

**Statut :** À faire
**ADR :** [ADR-001](../ADR-001-architecture-en-couches.md), [ADR-003](../ADR-003-acces-keycloak-sdk.md), [ADR-005](../ADR-005-erreurs-et-codes-de-sortie.md)

Poser l'outillage et les fondations de la nouvelle architecture, sans changer le comportement de l'import.

La branche `feature/1-feature-interactive-cli-mode-with-spring-shell-for-realm-configuration` (mode interactif) n'est pas fusionnée : le mode interactif sera repris après la refonte, sur le nouveau point d'entrée (voir `TASKS.md`).

## Stories

### US-001-01 — Outillage du build

**Statut :** À faire
Ajouter le wrapper Maven (`mvnw`), le formatage automatique (Spotless) vérifié par `mvn verify`, et une CI GitHub Actions : build, tests unitaires et d'intégration, analyse des dépendances.

### US-001-02 — Alignement des dépendances

**Statut :** À faire
Aligner `keycloak-admin-client` sur la version cible de Keycloak (26.5.x). Clarifier la cohabitation Jackson 2 (SDK Keycloak, RESTEasy) et Jackson 3 (Spring Boot 4), source des correctifs récents sur RESTEasy.

### US-001-03 — Sel de chiffrement de l'état distant

**Statut :** À faire
Retirer la valeur par défaut versionnée de `import.remote-state.encryption-salt` dans `application.properties`. Exiger un sel fourni par l'environnement quand une clé de chiffrement est configurée, et le documenter. Changement incompatible à annoncer.

### US-001-04 — Squelette des couches et ArchitectureTest

**Statut :** À faire
Créer les packages `domain`, `application`, `infrastructure` et le test ArchUnit des règles de l'ADR-001. Les packages hérités sont exclus du test jusqu'à leur migration.

### US-001-05 — Couche SDK

**Statut :** À faire
Créer `AbstractAdapter`, `KeycloakAccessException` et `KeycloakClientFactory` (ADR-003), avec les tests de traduction des erreurs (MockServer). Le client Keycloak existant (`KeycloakProvider`) y est déplacé.

### US-001-06 — Erreurs et codes de sortie

**Statut :** À faire
Introduire les trois familles d'exceptions et la traduction en codes de sortie dans les runners (ADR-005). Supprimer les `catch (Exception)` du runner.
