# ADR-010 — Shell en mode connecté : cibles, import, extraction, comparaison

## Statut

Accepté le 26/11/2026. Prolonge l'[ADR-008](ADR-008-shell-edition-des-definitions.md) (le shell hors ligne) en levant son report du mode connecté ; s'appuie sur l'[ADR-004](ADR-004-moteur-de-reconciliation.md) (import et dry-run), l'[ADR-007](ADR-007-fichiers-seule-verite.md) (les fichiers sont la seule vérité) et la normalisation de l'EPIC-005.

## Contexte

Le shell de l'EPIC-007 ne lit et n'écrit que des fichiers. Il ne sait donc ni appliquer un realm sur Keycloak, ni dire ce qu'un import supprimerait, ni partir d'un realm déjà configuré à la main. Trois besoins exprimés par l'utilisateur :

1. **Appliquer et simuler** depuis le shell le realm ouvert sur un Keycloak, sans changer d'outil.
2. **Extraire** un realm existant, configuré à la main dans la console, en fichiers découpés et nettoyés, pour le passer « en code » une fois pour toutes.
3. **Trouver la bonne écriture** d'un réglage : on le configure dans la console de développement, puis on veut voir les clés à reporter dans les fichiers.

Le shell ne vise que des Keycloak **locaux, de développement ou de qualification**, jamais la production, et il peut y écrire.

## Décision

### Des cibles nommées, sans secret dans les fichiers

Un fichier **`kcc-targets.yaml`**, à la racine des dossiers de realms (`shell.root`) et versionné, nomme les Keycloak joignables. Il ne contient aucun secret.

```yaml
targets:
  local:
    url: http://localhost:18080
    user: admin
  dev:
    url: https://keycloak.dev.example.org
    client-id: kcc-dev
    grant-type: client_credentials
```

- Champs : `url` (obligatoire), `login-realm` (`master` par défaut), `client-id` (`admin-cli` par défaut), `grant-type` (`password` par défaut, ou `client_credentials`), `user` (pour `password`).
- **Le mot de passe ou le secret** n'est jamais lu dans un fichier : il est demandé à la connexion, sans écho, ou lu dans la variable d'environnement `KCC_<CIBLE>_PASSWORD` ou `KCC_<CIBLE>_SECRET` (nom de la cible en majuscules, `-` remplacé par `_`), pour les scripts.
- Seules les cibles du fichier sont joignables. Pas de liste d'URL interdites : le fichier de l'équipe ne déclare pas la production (décision de l'utilisateur).

### Une connexion ouverte à la demande, derrière des ports

- `connect <cible>` ouvre la connexion (vérifiée par un premier appel, qui donne la version de Keycloak), `disconnect` la ferme ; une seule cible à la fois. Le prompt l'affiche : `kcc:mon-realm@dev>`. La propriété `shell.target` connecte au démarrage, pour les scripts.
- Une commande qui a besoin d'une connexion échoue sans elle, avec le code 2 et un message qui dit d'utiliser `connect`.
- **Architecture** : les commandes passent par des cas d'usage de `application.definition` qui utilisent des ports (connexion, import, lecture d'un realm sur le serveur) ; ces ports sont implémentés dans `infrastructure.keycloak` par le code existant (client SDK, import, normalisation). La règle ArchUnit qui interdit au shell de voir `infrastructure.keycloak` reste vraie. Le client Keycloak, aujourd'hui unique et fixé au démarrage, devient constructible **par cible** ; la façon de l'obtenir (fabrique, contexte Spring enfant par connexion…) est laissée au plan.
- Approches écartées : une cible fixée au lancement (on ne pourrait pas extraire de `qualif` puis simuler sur `local` dans une session) ; lancer l'outil d'import comme un programme séparé (lent, et impossible pour la complétion et la comparaison).

### Import et dry-run du realm ouvert

- `realm dry-run` simule l'import des fichiers du realm ouvert sur la cible et affiche le plan de l'EPIC-004 ; `realm import` affiche la cible, le même plan, puis demande confirmation (non par défaut ; `--apply` en script).
- **Variables** : les placeholders sont rendus avec des valeurs données par `--var nom=valeur`, par la variable d'environnement `KCC_VAR_<NOM>`, ou demandées pour celles qui manquent (sans écho quand le nom contient `secret` ou `password`, sans tenir compte de la casse). Une variable sans valeur ni valeur par défaut fait échouer la commande en script (code 2), comme l'import aujourd'hui.
- **Première déclaration d'un type** : connecté, le shell dit précisément ce que le prochain import supprimerait (« 3 groupes de Keycloak absents des fichiers seront supprimés : /legacy, … ») au lieu de l'avertissement générique de l'ADR-008.

### Extraction d'un realm : `realm extract`

- `realm extract <realm> [--folder] [--format yaml|json] [--with-users]` lit le realm sur la cible et crée un **nouveau dossier** (refusé s'il existe, comme `realm create`), qui s'importe tel quel.
- Le contenu est **normalisé** par le moteur de l'EPIC-005 (ce que Keycloak met par défaut est retiré, pour ne garder que ce qui a été configuré), puis **découpé** selon la numérotation standard (`StandardLayout` de l'US-007-11 : `00-realm`, `20-clients`, `30-roles`, `40-groups`, `50-links`).
- **Tout le realm** est extrait, y compris les types que le shell n'édite pas (flows, client scopes, fournisseurs d'identité…), qui vont dans `00-realm`. Les **utilisateurs** ne le sont qu'avec `--with-users`.
- **Les secrets** ne sont jamais écrits : le secret d'un client devient la variable proposée par le shell (`{{shopSecret}}`, US-007-07), et les autres secrets connus (secret d'un fournisseur d'identité, mot de passe SMTP, identifiants d'un annuaire LDAP) deviennent des variables nommées de même. La liste des variables à fournir est affichée à la fin.
- **Décision de l'utilisateur** : le secret des clients est lui aussi **variabilisé** (option écartée : ne pas l'écrire, l'import gardant alors le secret généré par Keycloak). Le secret reste maîtrisé sur tous les environnements ; pour un realm fait à la main, il faut fournir à l'import les secrets actuels de ses clients.
- **Critère de réussite** : un dry-run sur la même cible juste après l'extraction, avec les variables données à leur valeur actuelle, n'annonce aucun changement.

### Comparaison d'un élément : `diff`

- `client diff <id>`, `role diff <nom> [--client]`, `group diff <chemin>` et `realm diff` (réglages du realm) comparent l'élément tel qu'il est sur la cible, normalisé, avec sa vue dans les fichiers (les clés d'export de l'ADR-009) et listent : les clés présentes dans Keycloak et absentes des fichiers, les clés absentes de Keycloak, les valeurs différentes.
- La commande n'écrit rien : on reporte les clés avec le shell ou à la main. Les fichiers restent la seule vérité (ADR-007). Un vrai aller-retour qui fusionnerait Keycloak dans les fichiers est écarté pour l'instant, et pourra être cadré si le besoin se confirme.

### Complétion depuis Keycloak

- Connecté, les choix du shell (rôles d'un groupe, clients d'un rôle) proposent aussi les éléments présents sur la cible et absents des fichiers, marqués `(Keycloak)`. Les rôles et clients créés par Keycloak sont alors lus sur la cible plutôt que dans le realm de référence `baseline/<version>`, qui reste utilisé hors ligne.

### Précisions

*Choix de conception*, faits à la rédaction :

- **Lecture du realm pour l'extraction** : l'export partiel de l'API d'administration (`partial-export`, clients, groupes et rôles compris), lu par le lecteur d'export de la normalisation (`RealmExportReader`) puis normalisé comme un fichier d'export ; les utilisateurs, absents de cet export, sont lus à part avec `--with-users` (avec leurs rôles et leurs groupes, jamais leurs mots de passe). Le realm de référence est choisi comme le fait déjà la normalisation (la version de la cible, sinon la plus proche).
- **Ce qui est écrit** : ce que la normalisation garde. Un type entièrement par défaut n'est pas écrit, donc pas déclaré, donc pas nettoyé à l'import, ce qui est voulu. Le découpage est celui de `StandardLayout` : les types qu'il ne nomme pas (flows, client scopes, fournisseurs d'identité, composants, profil utilisateur…) vont dans `00-realm`.
- **Client Keycloak par cible** : un contexte Spring enfant par connexion, construit avec les propriétés `keycloak.*` de la cible, qui réutilise le câblage existant de l'import et de la normalisation. L'import en ligne de commande garde son comportement ; son câblage peut évoluer. Le fichier des cibles accepte aussi `ssl-verify` (`true` par défaut) et `connect-timeout`, pour un Keycloak de développement derrière une autorité de certification interne. Le secret reste en mémoire le temps de la session, jamais écrit.
- **Import et dry-run** : le pipeline `ImportRealms` sur les fichiers du dossier ouvert (un dossier = un realm, ADR-008), avec les options d'import par défaut ; pas de réglage des `import.behaviors` depuis le shell. Le plan affiché montre les suppressions ; la confirmation est la même.
- **Codes de sortie** : ceux de l'ADR-005 (3 pour un Keycloak injoignable, 4 pour un refus, dont une authentification refusée, 2 pour une erreur de configuration, 10 pour un dry-run avec des changements). En mode script, `connect` ne lit le secret que dans les variables d'environnement. Les secrets ne sont jamais affichés ni journalisés.
- **Variables** : `--var` l'emporte sur `KCC_VAR_<NOM>`, qui l'emporte sur la valeur par défaut écrite dans le placeholder ; les noms sont comparés tels qu'écrits.
- **Comparaison** : l'élément de la cible, normalisé et aplati en clés d'export (comme la vue des règles de l'ADR-009), contre sa vue dans les fichiers ; une valeur des fichiers qui contient un placeholder est rendue avec les variables données (`--var`, `KCC_VAR_*`, valeur par défaut), sinon signalée « non comparée » ; les secrets ne sont jamais comparés. Les listes d'objets (protocol mappers, autorisations) sont comparées en bloc : « différent » ou « identique ».
- **Extraction** : le dossier prend par défaut le nom du realm ; la commande n'ouvre pas le realm ; le format JSON est éditable par le shell comme le YAML.
- **Complétion** : les rôles, groupes et clients de la cible sont lus une fois à la connexion et relus après un import ; un échec de lecture se replie sur le realm de référence avec un avertissement. La marque `(Keycloak)` n'apparaît que dans les choix, jamais dans les fichiers ; la règle `group.defined-roles` accepte alors les rôles de la cible.
- **Tests** : Keycloak 26.5.3 du sample et des tests d'intégration (Testcontainers), avec une cible `password` et une cible `client_credentials`.

## Conséquences

- Le shell devient l'outil de la boucle de travail : modifier, simuler, appliquer, sur un Keycloak de développement.
- Passer un realm existant en code devient une commande ; la normalisation de l'EPIC-005 sert à la fois à l'outil de normalisation et à l'extraction.
- Le client Keycloak par cible est un changement d'architecture de l'adaptateur Keycloak, préparé dès la première story.
- Les secrets ne quittent jamais Keycloak vers les fichiers ; ils passent par des variables, données à l'import.
- Hors périmètre : la production, la fusion de Keycloak vers des fichiers existants (aller-retour), toute écriture sur Keycloak autre que l'import.
