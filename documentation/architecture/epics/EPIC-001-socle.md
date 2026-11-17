# EPIC-001 — Socle

**Statut :** À faire
**ADR :** [ADR-001](../ADR-001-architecture-en-couches.md), [ADR-003](../ADR-003-acces-keycloak-sdk.md), [ADR-005](../ADR-005-erreurs-et-codes-de-sortie.md)

Poser l'outillage et les fondations de la nouvelle architecture, sans changer le comportement de l'import.

La branche `feature/1-feature-interactive-cli-mode-with-spring-shell-for-realm-configuration` (mode interactif) n'est pas fusionnée : le mode interactif sera repris après la refonte, sur le nouveau point d'entrée (voir `TASKS.md`).

## Stories

### US-001-01 — Outillage du build

**Statut :** Terminé
Ajouter le wrapper Maven (`mvnw`), le formatage automatique (Spotless) vérifié par `mvn verify`, et une CI GitHub Actions : build, tests unitaires et d'intégration, analyse des dépendances.

### US-001-02 — Alignement des dépendances

**Statut :** Terminé (tests d'intégration à confirmer en CI)
`keycloak-admin-client` monté à 26.0.13 (dernière version de la ligne qui sert les serveurs 26.x). Jackson 2 suit la version gérée par Spring Boot (2.20.2) au lieu d'être figé en 2.17.2. Testcontainers passe en 2.0.3, la version gérée par Spring Boot. Les BOM JUnit et Testcontainers sont importés avant celui de Keycloak, qui imposait sinon des versions plus anciennes.

### US-001-03 — Sel de chiffrement de l'état distant

**Statut :** Terminé
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
