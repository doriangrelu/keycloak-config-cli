# Tasks

> Index des EPICs de la refonte 7.0.0. Le détail des stories et leur avancement est dans [`documentation/architecture/epics/`](documentation/architecture/epics/). Les décisions sont dans [`ARCHITECTURE.md`](ARCHITECTURE.md).

## EPICs

| EPIC | Sujet | Statut |
|---|---|---|
| [EPIC-001](documentation/architecture/epics/EPIC-001-socle.md) | Socle : outillage, dépendances, couches, couche SDK, codes de sortie | Terminé (tests d'intégration à confirmer en CI) |
| [EPIC-002](documentation/architecture/epics/EPIC-002-moteur-et-roles.md) | Moteur de réconciliation et migration des rôles | Terminé |
| [EPIC-003](documentation/architecture/epics/EPIC-003-migration-ressources.md) | Migration des autres types de ressources | Terminé |
| [EPIC-004](documentation/architecture/epics/EPIC-004-dry-run.md) | Dry-run et rapport des changements | Terminé |
| [EPIC-005](documentation/architecture/epics/EPIC-005-normalisation.md) | Normalisation sur le nouveau socle | Terminé |
| [EPIC-006](documentation/architecture/epics/EPIC-006-simplification.md) | Simplification : les fichiers comme seule vérité, Mustache seul moteur de template | En cours (US-006-01 terminée) |

## Après la refonte

| Sujet | Origine |
|---|---|
| Normalisation : ignorer d'autres valeurs par défaut que celles de la référence | `TODO` de l'ancien `RealmNormalizationService` (EPIC-005) |
| Normalisation : ignorer des clients par expression régulière | `TODO` de l'ancien `RealmNormalizationService` (EPIC-005) |
| Normalisation : profil utilisateur et utilisateurs | Limites de l'EPIC-005 |
| Mode interactif avec Spring Shell, repris comme adapter d'entrée de `infrastructure/cli`, une fois l'EPIC-006 terminé | Branche `feature/1-feature-interactive-cli-mode-with-spring-shell-for-realm-configuration`, conservée telle quelle |

## Principe de migration

La refonte avance type de ressource par type de ressource, sans rupture : à chaque étape, le build et les tests d'intégration existants (Testcontainers) restent verts. L'ancien code et le nouveau cohabitent jusqu'à la fin de l'EPIC-003.
