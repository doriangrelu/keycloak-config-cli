# ADR-003 — Accès à Keycloak : couche SDK et traduction des erreurs

## Statut

Accepté — refonte 7.0.0.

## Contexte

Aujourd'hui, 19 classes `repository/*Repository` appellent le SDK `keycloak-admin-client`. Chacune gère ses erreurs à sa façon (`try/catch` locaux, `ResponseUtil`), et 17 blocs `catch (Exception)` subsistent dans `src/main`. Une même erreur Keycloak (404, 409, délai dépassé) produit des messages et des comportements différents selon l'endroit.

## Décision

L'accès au SDK est regroupé dans `infrastructure/keycloak/sdk/` :

```text
infrastructure/keycloak/sdk/
├── api/                         ← une interface par famille d'opérations (niveau *Representation)
│   ├── RealmApi.java
│   ├── RoleApi.java
│   ├── ClientApi.java
│   └── …
├── adapter/                     ← implémentations, appels au SDK uniquement
│   ├── AbstractAdapter.java     ← execute(...), createAndGetId(...), traduction des erreurs
│   ├── RoleAdapter.java
│   └── …
├── KeycloakClientFactory.java   ← @Configuration : client Keycloak, délais, adapters
└── KeycloakAccessException.java ← erreur Keycloak qualifiée
```

| Composant | Rôle |
|---|---|
| `*Api` | Opérations du SDK pour une famille de ressources. Jamais visible de `application` |
| `AbstractAdapter` | Seul point de traduction des erreurs : `execute(Supplier)`, `execute(Runnable)`, `createAndGetId(Supplier<Response>)` |
| `*Adapter` | Appels au SDK, sans logique métier ; pas de `catch` local |
| `*KeycloakGateway` | Implémente un port de `application` en BOs ; utilise une ou plusieurs `*Api` et un mapper |

### Choix de mise en œuvre

1. **Pas d'interface composite.** Regrouper toutes les `*Api` derrière une seule interface donnerait une interface d'une vingtaine de familles d'opérations. Chaque gateway reçoit directement les `*Api` dont elle a besoin, ce qui garde des dépendances lisibles et des tests ciblés.
2. **Pas de statut HTTP.** Une CLI n'a pas de réponse HTTP : `KeycloakAccessException` porte une **cause qualifiée**, traduite en code de sortie par la CLI (ADR-005).

### Traduction des erreurs

| Erreur côté SDK | Cause qualifiée |
|---|---|
| `WebApplicationException` 400, 422 | `REJECTED` (Keycloak refuse la configuration) |
| `WebApplicationException` 401, 403 | `UNAUTHORIZED` |
| `WebApplicationException` 404 | `NOT_FOUND` |
| `WebApplicationException` 409 | `CONFLICT` |
| `WebApplicationException` 429, 503 | `UNAVAILABLE` |
| `WebApplicationException` autre, ou sans réponse | `UNEXPECTED` |
| `ProcessingException` causée par un délai de lecture | `TIMEOUT` |
| autre `ProcessingException` (connexion refusée, hôte inconnu…) | `UNAVAILABLE` |

Le message conserve le corps de la réponse Keycloak quand il existe (`errorMessage`), sans secret.

### Règles d'implémentation

- Tout appel au SDK passe par `AbstractAdapter.execute(...)` ; aucun adapter n'attrape `WebApplicationException` ni `ProcessingException`.
- Les adapters ne sont pas des beans Spring : ils sont créés par `KeycloakClientFactory`.
- Un `404` attendu (lecture d'une ressource qui peut ne pas exister) est converti en `Optional.empty()` par l'adapter, à partir de `NOT_FOUND`, jamais par un `catch` générique.
- Le client Keycloak a des délais de connexion et de lecture explicites (propriétés `keycloak.connect-timeout`, `keycloak.read-timeout`, déjà présentes).

## Conséquences

- Les `repository/*` disparaissent au fil de la migration, remplacés par les `*Api` et `*Adapter`.
- Un test par adapter vérifie la traduction des erreurs avec MockServer (déjà utilisé dans les tests).
- `keycloak-admin-client` est publié à part du serveur : la ligne 26.0.x sert tous les serveurs 26.x. L'EPIC-001 la monte à la dernière version (26.0.13).
