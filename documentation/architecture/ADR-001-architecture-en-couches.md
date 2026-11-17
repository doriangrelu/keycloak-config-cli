# ADR-001 — Architecture : domain, application, infrastructure

## Statut

Accepté — refonte 7.0.0.

## Contexte

Le code hérité d'adorsys est rangé par type technique (`service/`, `repository/`, `util/`, `provider/`). Les services d'import manipulent directement les `*Representation` du SDK Keycloak, lisent la configuration Spring et appellent les repositories. Conséquences :

- une règle (ressource protégée, mode géré `full` / `no-delete`, nettoyage des orphelins) est dispersée dans plusieurs services et ne se teste qu'avec un vrai Keycloak ;
- l'ordre des imports, l'état et le nettoyage sont couplés à l'implémentation de chaque service ;
- rien n'empêche le découpage de dériver.

## Décision

Trois packages sous la racine `io.github.doriangrelu.keycloak.config`. Les dépendances ne vont que **vers l'intérieur** :

```text
infrastructure  ──▶  application  ──▶  domain
(cli, sources,       (moteur de         (BOs, règles de
 keycloak, état)      réconciliation,    réconciliation,
                      cas d'usage,       exceptions métier)
                      ports)
```

```text
config/
├── domain/
│   ├── common/                  ← ResourceKey, ManagedMode, ProtectionRule, DomainException
│   ├── reconcile/               ← Change, ChangeSet, ReconciliationPlan (ADR-004)
│   └── <ressource>/model/       ← Business Objects : realm, role, client, group… (ADR-002)
├── application/
│   ├── reconcile/               ← ReconciliationEngine, Reconciler, RealmContext, ports communs
│   ├── importation/             ← cas d'usage « importer une configuration »
│   ├── normalization/           ← cas d'usage « normaliser un export »
│   └── <ressource>/
│       ├── <Ressource>Reconciler
│       └── port/<Ressource>Gateway
└── infrastructure/
    ├── cli/                     ← runners, propriétés, codes de sortie (ADR-005)
    ├── source/                  ← lecture des fichiers, substitution, Mustache, checksum
    └── keycloak/
        ├── sdk/                 ← *Api, *Adapter, AbstractAdapter, client (ADR-003)
        ├── state/               ← état distant stocké dans les attributs du realm
        └── <ressource>/         ← <Ressource>KeycloakGateway + <Ressource>KeycloakMapper (ADR-006)
```

Le rangement suit **la couche d'abord, puis la ressource**. Un type de ressource se retrouve au même endroit dans chaque couche.

### Règles par couche

| Couche | Contient | Peut dépendre de |
|---|---|---|
| `domain` | BOs, règles de diff, de protection et de mode géré, exceptions métier | JDK uniquement |
| `application` | Moteur, reconcilers, cas d'usage, ports (`*Gateway`) | `domain`, JDK, `org.springframework.stereotype` |
| `infrastructure.cli` | Runners, binding des propriétés, traduction des erreurs en codes de sortie | `application`, `domain`, Spring Boot |
| `infrastructure.source` | Lecture et préparation des fichiers d'import | `application` (ports), `domain`, Jackson, JMustache |
| `infrastructure.keycloak` | Gateways, mappers BO ↔ `*Representation`, SDK, état | `application` (ports), `domain`, `org.keycloak..` |

En plus :

- `org.keycloak..` n'est importé que dans `infrastructure.keycloak` ;
- `infrastructure.keycloak` et `infrastructure.source` ne dépendent pas l'un de l'autre ;
- aucun cycle entre packages.

Ces règles sont vérifiées par un test ArchUnit (`ArchitectureTest`) exécuté par `mvn verify` : une dérive fait échouer le build.

## Conséquences

- Une règle de réconciliation se teste sans mock ni Keycloak (tests du domaine). Un reconciler se teste en simulant ses ports. La traduction vers le SDK se teste sur la gateway. Les tests d'intégration Testcontainers existants restent le filet de non-régression de bout en bout.
- Ajouter un type de ressource touche une classe par couche : BO, reconciler et port, gateway et mapper.
- Le coût est réel : un BO, un port, une gateway et un mapper par type de ressource. Il est accepté pour isoler les règles (voir ADR-002).
- La migration se fait type de ressource par type de ressource : l'ancien code et le nouveau cohabitent pendant la refonte (voir `TASKS.md`).
