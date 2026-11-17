# ADR-005 — Erreurs et codes de sortie

## Statut

Accepté — refonte 7.0.0. Code `10` du dry-run retenu le 17/11/2026.

## Contexte

`KeycloakConfigRunner` renvoie `1` pour toute erreur, attrape `Exception`, relance `NullPointerException` telle quelle, et ne relance l'erreur qu'en niveau DEBUG. Un pipeline qui appelle l'outil ne peut pas distinguer une configuration invalide d'un Keycloak indisponible, alors que la conduite à tenir diffère (corriger le fichier, ou relancer plus tard).

## Décision

### Trois familles d'exceptions

| Famille | Exemples | Où elle est levée |
|---|---|---|
| Métier (`DomainException`) | clé dupliquée, référence vers une ressource absente, suppression d'une ressource protégée demandée explicitement | `domain` |
| Applicative (`ApplicationException`) | fichier d'import invalide, checksum modifié avec `checksum-changed=fail`, cycle de dépendances | `application` |
| Technique | `KeycloakAccessException` (ADR-003), erreur de lecture de fichier | `infrastructure` |

### Un seul point de traduction

Les runners de `infrastructure/cli/` sont le seul endroit qui attrape les exceptions. Ils journalisent une fois l'erreur, avec son contexte (realm, type de ressource, clé), et renvoient le code de sortie correspondant. Aucun autre `catch (Exception)` n'est toléré.

### Codes de sortie

| Code | Signification | Conduite attendue |
|---|---|---|
| `0` | Succès ; en dry-run : aucune modification en attente | — |
| `1` | Erreur inattendue | Analyser les logs |
| `2` | Configuration invalide (fichier, propriétés, règle métier) | Corriger la configuration |
| `3` | Keycloak indisponible ou délai dépassé | Relancer plus tard |
| `4` | Keycloak refuse l'opération (droits, rejet, conflit) | Vérifier les droits ou la configuration |
| `5` | Checksum modifié avec `checksum-changed=fail` | Décision manuelle |
| `10` | Dry-run : des modifications sont en attente | Relire le plan |

Les codes sont une **interface publique** : ils sont documentés dans `documentation/reference/` et ne changent qu'avec une version majeure.

## Conséquences

- Changement incompatible avec la 6.x (seul `1` existait) : à annoncer dans le CHANGELOG de la 7.0.0.
- Les tests de `CommandLineImportIT` vérifient le code de sortie de chaque famille d'erreur.
