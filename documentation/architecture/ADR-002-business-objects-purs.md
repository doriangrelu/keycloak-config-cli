# ADR-002 — Modèle du domaine en Business Objects purs

## Statut

Accepté — refonte 7.0.0. Champs non modélisés : option (c) retenue le 17/11/2026.

## Contexte

Le format d'entrée de keycloak-config-cli est la représentation Keycloak elle-même (`RealmRepresentation` et ses sous-objets). Deux approches ont été étudiées :

| Option | Principe | Conséquences |
|---|---|---|
| Hybride | Le contenu des ressources reste en `*Representation` ; seuls les concepts propres à l'outil (identité, protection, mode géré, plan) sont des objets du domaine | Peu de code ; mais le domaine dépend de Keycloak, et les règles de diff portent sur des objets mutables du SDK |
| **BOs purs** | Chaque type de ressource géré a son modèle Java pur ; la conversion se fait dans l'infrastructure | Plus de code (BOs, mappers) ; mais le domaine est stable, testable sans Keycloak, et indépendant des versions du SDK |

## Décision

**BOs purs**.

- Le domaine ne dépend que du JDK : ni Keycloak, ni Spring, ni Jackson.
- Un BO est un `record` immuable, valide dès sa construction (vérifications dans le constructeur compact). Les collections sont copiées (`List.copyOf`, `Map.copyOf`).
- Chaque type de ressource a une **clé d'identité** explicite (`ResourceKey`) : le `name` d'un rôle de realm, le couple `clientId` + `name` d'un rôle de client, le chemin d'un groupe, le `clientId` d'un client… C'est elle que le diff utilise pour apparier le voulu et l'existant, jamais l'identifiant technique Keycloak.
- L'identifiant technique Keycloak (UUID) n'entre pas dans le BO voulu. Il est porté par le BO lu depuis Keycloak, sous forme d'objet valeur (`KeycloakId`), pour que les gateways puissent mettre à jour et supprimer.
- Les références entre ressources passent par la clé de la ressource cible, jamais par un lien objet (un groupe référence ses rôles par `RoleKey`).
- Les conversions `*Representation` ↔ BO sont dans `infrastructure.keycloak.<ressource>` (ADR-006). Le fichier d'import est lu en `*Representation` (format public inchangé), puis converti en BOs.

### Champs non modélisés

Une `RealmRepresentation` a plusieurs centaines de champs, et chaque version de Keycloak en ajoute. Il faut décider ce que devient un champ présent dans le fichier d'import mais absent du BO.

| Option | Effet |
|---|---|
| (a) Tout modéliser | Aucun champ perdu ; coût très élevé, et retouche à chaque version de Keycloak |
| (b) Rejeter à la validation | Sûr (aucune perte silencieuse) ; mais les fichiers existants qui utilisent un champ non modélisé ne passent plus |
| (c) Transporter tel quel | Le BO porte les champs non modélisés dans une `Map<String, Object>` opaque, comparée et réappliquée telle quelle. Le domaine reste pur ; les règles ne portent que sur les champs typés |

Décision : **(c)**.

- Chaque BO porte un objet valeur `UnmodeledAttributes`, qui enveloppe une `Map<String, Object>` immuable (types JSON simples uniquement : chaînes, nombres, booléens, listes et maps de ces types).
- Le diff compare ces attributs comme un bloc : une différence produit une mise à jour, sans détail par champ dans le rapport au-delà du nom des clés modifiées.
- On type en priorité les champs utilisés par une règle (identité, références, protection, mode géré), puis on enrichit au fil des versions. Un champ qui devient typé sort de `UnmodeledAttributes`.
- Aucune règle du domaine ne lit `UnmodeledAttributes`. Les règles de comparaison (diff, normalisation) les comparent clé par clé sans interpréter leurs valeurs ; elles ne nomment une clé que pour la garder ou la comparer entrée par entrée (`attributes`, `enabled` d'un realm, `protocol` d'un client).

## Conséquences

- Un BO par type de ressource et un mapper par sens : c'est le plus gros volume de la refonte.
- La compatibilité avec une nouvelle version de Keycloak se traite dans les mappers et les adapters, sans toucher au domaine.
- Les règles (diff, protection, mode géré) se testent avec des BOs construits en mémoire.
