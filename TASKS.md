# Tasks

> Index des EPICs de la refonte 7.0.0. Le détail des stories et leur avancement est dans [`documentation/architecture/epics/`](documentation/architecture/epics/). Les décisions sont dans [`ARCHITECTURE.md`](ARCHITECTURE.md).

## EPICs

| EPIC | Sujet | Statut |
|---|---|---|
| [EPIC-001](documentation/architecture/epics/EPIC-001-socle.md) | Socle : outillage, dépendances, couches, couche SDK, codes de sortie | Terminé (tests d'intégration à confirmer en CI) |
| [EPIC-002](documentation/architecture/epics/EPIC-002-moteur-et-roles.md) | Moteur de réconciliation et migration des rôles | Terminé |
| [EPIC-003](documentation/architecture/epics/EPIC-003-migration-ressources.md) | Migration des autres types de ressources | À faire |
| [EPIC-004](documentation/architecture/epics/EPIC-004-dry-run.md) | Dry-run et rapport des changements | À faire |
| [EPIC-005](documentation/architecture/epics/EPIC-005-normalisation.md) | Normalisation sur le nouveau socle | À faire |

## Après la refonte

| Sujet | Origine |
|---|---|
| Mode interactif avec Spring Shell, repris comme adapter d'entrée de `infrastructure/cli` | Branche `feature/1-feature-interactive-cli-mode-with-spring-shell-for-realm-configuration`, conservée telle quelle |

## Principe de migration

La refonte avance type de ressource par type de ressource, sans rupture : à chaque étape, le build et les tests d'intégration existants (Testcontainers) restent verts. L'ancien code et le nouveau cohabitent jusqu'à la fin de l'EPIC-003.
