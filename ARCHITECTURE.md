# Architecture

> Index des décisions d'architecture de keycloak-config-cli. Le détail de chaque choix est dans [`documentation/architecture/`](documentation/architecture/). L'avancement de la refonte est suivi dans [`TASKS.md`](TASKS.md).

## Refonte 7.0.0 en bref

La version 7.0.0 réorganise l'outil autour d'un **moteur de réconciliation** : pour chaque realm, il calcule les différences entre la configuration voulue et l'état de Keycloak, puis les applique. Le domaine est modélisé en Business Objects purs, isolés de Keycloak et de Spring. L'accès à l'API d'administration passe par une couche unique qui traduit les erreurs.

```text
infrastructure  ──▶  application  ──▶  domain
```

## Décisions d'architecture (ADR)

| ADR | Sujet | Statut |
|---|---|---|
| [ADR-001](documentation/architecture/ADR-001-architecture-en-couches.md) | Architecture domain / application / infrastructure, vérifiée par ArchUnit | Accepté |
| [ADR-002](documentation/architecture/ADR-002-business-objects-purs.md) | Modèle du domaine en Business Objects purs | Accepté |
| [ADR-003](documentation/architecture/ADR-003-acces-keycloak-sdk.md) | Accès à Keycloak : couche SDK et traduction des erreurs | Accepté |
| [ADR-004](documentation/architecture/ADR-004-moteur-de-reconciliation.md) | Moteur de réconciliation (plan, ordre par dépendances, contexte par realm, dry-run) | Accepté |
| [ADR-005](documentation/architecture/ADR-005-erreurs-et-codes-de-sortie.md) | Erreurs et codes de sortie | Accepté |
| [ADR-006](documentation/architecture/ADR-006-conversions-mapstruct.md) | Conversions avec MapStruct | Accepté |
| [ADR-007](documentation/architecture/ADR-007-fichiers-seule-verite.md) | Les fichiers d'import comme seule vérité : nettoyage unique, Mustache seul moteur de template, options retirées | Accepté |
| [ADR-008](documentation/architecture/ADR-008-shell-edition-des-definitions.md) | Shell interactif (Spring Shell) et édition guidée des définitions de realm, hors ligne, avec règles de configuration | Accepté |

## Décisions du 17/11/2026

| Question | Décision | Où |
|---|---|---|
| Champs Keycloak non modélisés dans les BOs | Transportés tels quels dans `UnmodeledAttributes` | ADR-002 |
| Plusieurs fichiers pour un même realm | Fusionnés en un seul état voulu ; une clé définie deux fois avec des contenus différents est une erreur | ADR-004 |
| Code de sortie du dry-run | `0` sans modification, `10` avec modifications en attente | ADR-004, ADR-005 |
| Mode interactif Spring Shell (branche `feature/1-…`) | Reporté après la refonte, comme nouvel adapter d'entrée dans `infrastructure/cli` | `TASKS.md` |

## Décisions du 20/11/2026

| Question | Décision | Où |
|---|---|---|
| Que supprimer à la fin d'un import ? | Pour un type déclaré, tout ce que les fichiers ne déclarent pas, sauf les utilisateurs, les ressources de Keycloak et les ressources protégées | ADR-007 |
| Cache par checksum, état distant, modes gérés, import parallèle, substitution `$(...)` | Retirés | ADR-007 |
| Variabilisation des fichiers | Mustache seul ; une variable manquante sans valeur par défaut est une erreur | ADR-007 |

## Changements incompatibles de la 7.0.0

Repris dans le [CHANGELOG](CHANGELOG.md) et le [guide de migration](documentation/getting-started/migration-7.md) :

- fusion des fichiers d'un même realm (le dernier fichier ne l'emporte plus) ;
- codes de sortie distincts (seul `1` existait) ;
- tous les fichiers lus et analysés avant toute modification ;
- règle de suppression unique, sans état distant ni modes gérés ; options retirées : cache par checksum, état distant, modes gérés, import parallèle, substitution `$(...)` (ADR-007) ;
- Mustache seul moteur de template : une variable manquante sans valeur par défaut fait échouer l'import, plus d'échappement HTML.
