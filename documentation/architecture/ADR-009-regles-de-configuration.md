# ADR-009 — Règles de configuration : un formalisme déclaratif sur les clés d'export

## Statut

Accepté le 25/11/2026. Précise la section « Les règles de configuration » de l'[ADR-008](ADR-008-shell-edition-des-definitions.md) ; cadre la story US-007-06 de l'[EPIC-007](epics/EPIC-007-shell-edition.md).

## Contexte

Le shell (ADR-008) guide des développeurs qui connaissent mal Keycloak. Pour que leurs modifications soient robustes et sûres, des **règles de configuration** fixent des valeurs par défaut à la création et vérifient un élément avant de l'écrire. L'ADR-008 a déjà décidé :

- les règles de base sont intégrées à l'outil ; chaque organisation peut ajouter les siennes ;
- elles s'expriment dans un formalisme déclaratif, interprété par un moteur du domaine ; une règle trop riche pour le formalisme peut rester écrite en Java ;
- un élément écrit à la main qui ne respecte pas une règle est signalé à l'ouverture du realm, et n'est corrigé que si on le modifie ;
- une valeur entièrement variable (`{{url}}`) est signalée comme non vérifiable, sans être refusée.

Restait à concevoir le formalisme. Trois difficultés :

1. Le modèle d'édition du shell ne connaît qu'une partie des clés d'un élément (pour un client : identifiant, nom, description, type d'application, URIs de redirection, origines web, attributs). Le reste du fichier est conservé sans être compris. Les types d'éléments à venir (client scopes, flows, fournisseurs d'identité, brute force…) en ajouteront beaucoup d'autres.
2. Les règles de base doivent rester un socle de sécurité, sans empêcher l'outil de servir dans les cas réels (une ancienne application qui ne gère pas PKCE).
3. Les règles sont écrites par un expert de Keycloak, mais lues par un développeur qui ne l'est pas.

## Décision

### Les règles parlent en clés du format d'export

Une règle désigne les valeurs par les **clés du format d'export de Keycloak**, celles des fichiers de définition : `publicClient`, `redirectUris`, `attributes."pkce.code.challenge.method"`.

- Une règle peut porter sur n'importe quelle clé du fichier, même une que le shell n'édite pas. Couvrir un nouveau type d'élément ne change pas le formalisme.
- Les mêmes règles pourront vérifier les fichiers d'un import (ADR-008, « Placées dans le domaine… »).
- Seul l'auteur des règles voit ces clés ; le développeur guidé ne lit que les messages.

Une **exception** : le **type d'application** d'un client (`application-type`), notion du shell déduite des cinq clés de flows (ADR-008). L'écrire en clés serait pénible et source d'erreurs. Ses valeurs sont celles de `ApplicationType`, en minuscules avec des tirets : `spa`, `web-application`, `service`, `api`, `custom`.

Écriture d'une clé : un chemin pointé ; un segment qui contient un point est entre guillemets (`attributes."pkce.code.challenge.method"`). Les objets imbriqués sont aplatis ; une liste de valeurs simples (`redirectUris`) reste une liste. *Choix de conception :* les listes d'objets (`protocolMappers`, `composites`…) ne sont pas adressables dans cette première version ; une règle qui en a besoin est écrite en Java.

### Le format d'un fichier de règles

Un fichier YAML. Exemple, les deux règles d'exemple de l'US-007-06 :

```yaml
rules:
  - id: client.spa.pkce
    element: client
    when:
      application-type: spa
    defaults:
      attributes."pkce.code.challenge.method": S256
    checks:
      - key: attributes."pkce.code.challenge.method"
        equals: S256
    message: "A front-end application must use PKCE with S256: the browser cannot keep a secret."

  - id: client.redirect-uris.no-wildcard
    element: client
    checks:
      - key: redirectUris
        none-matches: '^\*$'
    message: "A redirect URI '*' lets any site receive the tokens. List the application's URIs."
```

Une règle :

| Champ | Rôle |
|-------|------|
| `id` | Identifiant unique, stable : il sert à désactiver la règle. Les règles de base sont préfixées par le type d'élément (`client.`, `role.`, `group.`, `realm.`). |
| `element` | `realm`, `client`, `role` ou `group`. |
| `when` | Facultatif. Conditions d'application : `application-type` (pour un client) et `checks`, une liste de conditions sur des clés écrites comme les vérifications, avec les opérateurs ci-dessous. Toutes doivent être vraies. |
| `defaults` | Facultatif. Clés et valeurs posées à la **création** d'un élément, seulement si la clé est absente. Une question du shell sur la même clé propose cette valeur par défaut. |
| `checks` | Facultatif. Vérifications faites **avant chaque écriture** et à **l'ouverture du realm**. Toutes doivent être vraies. |
| `severity` | `required` (par défaut) : une vérification fausse empêche l'écriture. `advice` : le shell avertit, puis écrit. |
| `message` | Le message affiché au développeur, en termes de développeur : ce qui ne va pas et pourquoi. |

Une règle a au moins `defaults` ou `checks`.

### Les opérateurs

Volontairement peu nombreux :

| Opérateur | Valeur simple | Liste |
|-----------|---------------|-------|
| `equals: v` | égale à `v` | — (erreur de chargement) |
| `one-of: [a, b]` | dans l'ensemble | chaque élément dans l'ensemble |
| `not-one-of: [a, b]` | hors de l'ensemble | aucun élément dans l'ensemble |
| `matches: 'regex'` | correspond au motif | chaque élément correspond |
| `none-matches: 'regex'` | ne correspond pas | aucun élément ne correspond |
| `present` / `absent` | la clé est présente / absente | idem |

- Une vérification porte un seul opérateur.
- Les motifs sont des expressions régulières Java, appliquées à la valeur entière.
- Les valeurs sont comparées comme du texte (`true`, `S256`, `3600`).
- **Clé absente :** `equals`, `one-of`, `matches` et `present` sont faux ; `not-one-of`, `none-matches` et `absent` sont vrais. *Choix de conception :* pour que « absent » ait le sens attendu, la vue d'un client contient toujours les **valeurs effectives des cinq clés de flows** (les valeurs par défaut de Keycloak quand la clé n'est pas écrite), que l'adaptateur connaît déjà (`ClientFlows.KEYCLOAK_DEFAULTS`). Pour les autres clés, la valeur par défaut de Keycloak n'est pas connue du moteur : l'auteur des règles écrit sa vérification en conséquence.

### Les valeurs variables

- Une valeur qui contient un placeholder (`{{url}}`, `{{appUrl}}/*`) **n'est pas jugée** : la vérification qui la touche est rangée parmi les « non vérifiables », affichées à part, sans refus ni avertissement. *Choix de conception :* l'ADR-008 parle des valeurs entièrement variables ; une valeur composée est traitée de même, car le moteur ne connaît pas la valeur à l'import.
- `present` et `absent` restent jugés : la présence d'une clé ne dépend pas de sa valeur.

### Règles de base, règles de l'organisation, désactivation

- **Les règles de base** sont un fichier embarqué dans l'outil (ressource `rules/base-rules.yaml`). Leurs messages sont en anglais. Avec l'US-007-06, il contient les deux règles d'exemple ci-dessus, **actives pour tous les utilisateurs** ; les règles de durcissement fournies plus tard s'y ajoutent.
- **Le fichier de l'organisation** est donné au lancement du shell par la propriété `shell.rules` (un chemin). Il ajoute ses règles (section `rules`, même format) et peut **désactiver** une règle par son identifiant, avec une **raison obligatoire** :

```yaml
disable:
  - id: client.spa.pkce
    for: [legacy-portal]
    reason: "Legacy portal without PKCE support, replacement planned in 2027."
rules:
  - ...
```

- `for` est facultatif : il limite la désactivation à des éléments précis (l'identifiant d'un client, le nom d'un rôle, le chemin d'un groupe). Sans `for`, la règle est désactivée pour tous les éléments.
- Le shell affiche les règles désactivées et leurs raisons à l'ouverture d'un realm, pour que l'écart reste visible.
- Une désactivation peut viser une règle de base, une règle Java ou une règle de l'organisation.

### Chargement des règles

Au démarrage du shell, les règles sont chargées et contrôlées. Un fichier de règles invalide arrête le shell avec le code 2 (ADR-005) et un message qui nomme le fichier, la règle et le problème. Sont refusés :

- un champ ou un opérateur inconnu, une expression régulière invalide, un `element` inconnu, `application-type` hors d'un client, `equals` sur une clé de liste connue ;
- une règle sans `defaults` ni `checks`, sans `message`, ou un identifiant déjà pris (y compris une règle de l'organisation qui reprend l'identifiant d'une règle de base) ;
- une désactivation sans raison ou d'un identifiant inconnu (sinon, une faute de frappe dans l'identifiant passerait inaperçue et la règle resterait active) ;
- deux règles d'un même fichier qui posent une valeur par défaut différente pour la même clé, sur le même `element` et avec le même `when`. Entre la base et l'organisation, la valeur de l'organisation l'emporte.

### Les règles écrites en Java

Une règle trop riche pour le formalisme implémente la même interface du domaine que les règles déclaratives (identifiant, élément, message, gravité, évaluation sur la vue des clés). Elle est désactivable de la même façon. Exemple attendu dans les règles de durcissement fournies plus tard : retirer `offline_access` et `uma_authorization` des composites du rôle `default-roles-<realm>`, qui touche une liste d'objets.

### La vue des clés : fournie par le port

Le moteur évalue une règle sur une **vue à plat des clés** de l'élément **tel qu'il sera écrit** (`ElementKeys` : chemin de clé → valeur simple ou liste, valeurs `TemplatedValue`).

- Le port `RealmDefinitions` fournit cette vue : les champs édités appliqués sur les clés déjà présentes dans le fichier. L'adaptateur fait déjà cette traduction pour écrire (`ClientKeys`, `RoleKeys`, `GroupKeys`) : il n'y a qu'**une seule traduction** modèle → clés.
- **Les valeurs par défaut peuvent viser n'importe quelle clé**, même une que le modèle d'édition ne connaît pas (`frontchannelLogout`, par exemple). L'écriture devient générique : les clés posées par les règles sont ajoutées au fichier à côté de celles du modèle, par l'adaptateur, avec la même réécriture depuis l'arbre (ADR-008).
- Le cas d'usage d'écriture enchaîne : obtenir la vue avant et après la modification, appliquer le moteur aux deux, écrire seulement si aucune règle `required` n'échoue **à cause de la modification**. Une non-conformité **introduite** par la modification (fausse après, vraie ou absente avant ; pour un nouvel élément, toute non-conformité) bloque l'écriture. Une non-conformité **déjà présente** est rappelée sans bloquer : le développeur n'est jamais coincé sur un défaut écrit à la main que le shell ne sait pas corriger, et sa modification ne dégrade rien. Les avertissements (`advice`) et les « non vérifiables » sont rendus avec le résultat.
- À l'ouverture d'un realm, la vue de chaque élément lu sert au signalement des non-conformités (règle, message, fichier et ligne).
- Pour un realm (`element: realm`), la vue est celle des clés du fichier du realm (`00-realm`), hors listes d'éléments.

Approches écartées :

- **Le domaine traduit lui-même le modèle en clés.** Le moteur serait autonome, mais la traduction existerait deux fois, avec un risque d'écart (le type d'application déduit des flows, par exemple).
- **Le moteur travaille dans l'adaptateur de fichiers, sur l'arbre YAML/JSON.** Plus direct, mais contraire à l'ADR-008 (règles dans le domaine) ; les règles ne pourraient plus vérifier un import.
- **Les règles parlent le vocabulaire du shell** (« type d'application », « URIs de redirection »). Plus lisible, mais limité à ce que le shell modélise : chaque nouveau champ couvert ferait évoluer le modèle.
- **Les règles de base comme plancher absolu**, ou **remplaçables librement** par l'organisation. Le plancher bloque les cas réels ; le remplacement libre laisse passer un écart en silence. La désactivation explicite, avec raison et affichage, garde l'outil utilisable sans contournement caché.

### Précisions

Décidés avec l'utilisateur : valeurs par défaut sur toute clé, blocage des seules non-conformités introduites, deux règles de base actives (ci-dessus). *Choix de conception*, faits à la rédaction :

- **Vue d'un rôle :** les clés d'export du rôle (`name`, `description`, `composite`…) et une clé synthétique `client`, l'identifiant du client propriétaire, absente pour un rôle du realm. Une règle distingue ainsi les rôles du realm (`when: { checks: [ { key: client, absent: true } ] }`) des rôles de client.
- **Vue d'un groupe :** `path`, `name`, `realmRoles` (liste) et `clientRoles."<client>"` (une liste par client).
- **Vue du realm :** les clés de premier niveau de tous les fichiers du dossier, hors listes d'éléments (`clients`, `roles`, `groups`…). Elle est vérifiée à l'ouverture et à `realm create` ; les commandes qui modifiaient le realm la vérifieraient avant d'écrire.
- **`when` :** les conditions sur une clé suivent les mêmes règles que les vérifications, clé absente comprise. `application-type` ne correspond jamais à un client personnalisé (`CUSTOM`), sauf `application-type: custom`.
- **Valeurs par défaut :** elles s'appliquent à la création, et quand une modification **change le type d'application** d'un client (les valeurs par défaut du nouveau type, pour les clés absentes). Si plusieurs règles qui s'appliquent posent une valeur différente pour la même clé, la dernière dans l'ordre l'emporte : base, puis Java, puis organisation, et dans un fichier l'ordre d'écriture. Une valeur par défaut qui contredit une vérification n'est pas refusée au chargement ; la vérification qui suit la création la signale.
- **`for` :** compare le texte tel qu'il est écrit dans le fichier (un identifiant variabilisé s'écrit avec son placeholder).
- **Valeurs :** un booléen ou un nombre YAML se compare comme son texte (`true` et `"true"` sont égaux). `null` vaut une clé absente ; un texte vide ou une liste vide est une clé présente. Une valeur multiple d'attribut est une liste. Une clé dont la forme ne correspond pas à l'opérateur (un objet là où on attend une valeur) rend la vérification non vérifiable.
- **Mode script :** un échec `required` fait échouer la commande avec le code 2 (ADR-005) et affiche les messages ; un `advice` est affiché, l'écriture a lieu, le code reste 0. Les « non vérifiables » tiennent en une ligne (leur nombre et les clés concernées).
- **Ouverture du realm :** d'abord les règles désactivées et leurs raisons, puis les non-conformités groupées par élément (fichier et ligne), `required` avant `advice`. Le rapport est fait même si le realm est en lecture seule.
- **Règles Java :** des beans Spring qui implémentent l'interface du domaine, collectés au démarrage ; leurs identifiants partagent l'espace de noms des règles déclaratives.
- **`shell.rules` :** un chemin relatif au répertoire de lancement ; le fichier est lu une fois au démarrage ; un fichier invalide arrête le shell, quelle que soit la commande. `rules list` fonctionne sans realm ouvert.
- **Cohérence du realm** (identifiant unique dans le dossier, rôles et groupes référencés définis dans le dossier ou créés par Keycloak d'après `baseline/<version>`, syntaxe des placeholders) : des règles Java de base, sur la même interface, dans la même story. Elles ont besoin du realm entier : leur évaluation reçoit le realm en plus de la vue de l'élément.

### Répartition dans les couches

```
domain.definition.rules        Rule, DeclarativeRule, Check, Operator, KeyPath, ElementKeys,
                               RuleSet (règles actives et désactivations), Verdict (échecs,
                               avertissements, non vérifiables), RuleEngine
application.definition         ports RealmDefinitions (+ vue des clés) et RuleSource ;
                               les cas d'usage d'écriture et d'ouverture appliquent le moteur
infrastructure.source.rules    lecture et contrôle des fichiers de règles (base embarquée,
                               fichier de l'organisation), traduction vers le domaine
infrastructure.cli.shell       propriété shell.rules ; affichage des verdicts ; commande rules list
```

*Choix de conception :* une commande `rules list` affiche les règles actives (identifiant, élément, gravité, message) et les désactivations avec leurs raisons.

## Conséquences

- Le formalisme ne change pas quand le shell couvre de nouveaux types d'éléments : il suffit que l'adaptateur fournisse leur vue de clés.
- Les auteurs de règles doivent connaître le format d'export de Keycloak ; c'est le rôle de l'expert, pas du développeur guidé.
- Les listes d'objets ne sont pas adressables : certaines règles de durcissement (rôles par défaut du realm) passent par Java.
- Une valeur variable n'est jamais refusée par une règle : la vérification complète d'un realm variabilisé passe par l'import (dry-run).
- Les règles du domaine pourront servir plus tard à une commande de vérification ou à un import strict.
- Les règles de durcissement réelles seront fournies plus tard ; elles s'ajoutent au fichier embarqué ou en Java, sans changer le mécanisme.
