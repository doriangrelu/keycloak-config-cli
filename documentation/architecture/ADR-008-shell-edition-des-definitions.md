# ADR-008 — Shell interactif et édition guidée des définitions de realm

## Statut

Accepté le 24/11/2026. Complète l'[ADR-001](ADR-001-architecture-en-couches.md) (un second adapter d'entrée) et l'[ADR-007](ADR-007-fichiers-seule-verite.md) (les fichiers restent la seule vérité : le shell ne fait que les écrire).

## Contexte

Les fichiers de définition des realms sont écrits à la main, au format d'export de Keycloak. Ce format expose des dizaines d'options par client, dont beaucoup touchent à la sécurité (flows activés, PKCE, URIs de redirection, secrets). Les développeurs qui ont besoin d'un client, d'un rôle ou d'un groupe ne connaissent pas bien Keycloak : ils recopient un fichier existant, oublient une option ou en activent une de trop.

Le besoin, sur le modèle de la console de Doctrine / Symfony (`make:entity`) :

1. lancer un shell sur un **dossier racine** qui contient les définitions des realms ;
2. **ouvrir** un realm existant ou en **créer** un, puis travailler dans ce contexte ;
3. disposer d'un **panel de commandes** qui créent et modifient les éléments du realm en posant des questions, avec des **choix commentés** pour guider ;
4. écrire le résultat en **JSON ou en YAML**, dans les fichiers du realm, **sans abîmer** ce qui est écrit à la main (commentaires, ordre, placeholders) ;
5. accepter partout une valeur en dur, une **variable Mustache** (`{{appUrl}}`) ou une **valeur composée** (`{{env}}-mon-realm`), y compris dans le nom du realm ;
6. appliquer des **règles de configuration minimale**, fournies plus tard, pour que les modifications soient robustes et sûres même faites par quelqu'un qui ne connaît pas Keycloak.

Un realm est souvent découpé en un fichier par type d'objet (`00-realm.yaml`, `20-clients.yaml`, `30-roles.yaml`…), fusionnés à l'import (ADR-004). Les fichiers peuvent contenir des placeholders Mustache, rendus seulement à l'import.

La branche `feature/1-feature-interactive-cli-mode-with-spring-shell-for-realm-configuration` ne contient aucun commit de plus que `main` : il n'y a rien à reprendre.

## Décision

### Un second adapter d'entrée, hors ligne

- **Spring Shell 4** (4.0.x, aligné sur Spring Boot 4), dans `infrastructure.cli.shell`. Comme la ligne de commande, il ne porte aucune logique : il traduit les commandes et les réponses en appels aux cas d'usage.
- Lancement par `run.operation=SHELL`, avec le dossier racine en propriété (`shell.root`, par défaut le répertoire courant). Le shell fonctionne en session interactive, ou, pour un script ou la CI, exécute une commande (`shell.command`) ou un fichier de commandes (`shell.script`) dans une seule session, avec éventuellement un realm ouvert au départ (`shell.realm`), sans jamais attendre de réponse ; il rend alors les codes de sortie de l'ADR-005.
- **Hors ligne** : le shell ne lit et n'écrit que des fichiers, sans connexion à Keycloak. Il propose ce que le dossier définit déjà (rôles, groupes, clients). Un mode connecté (import, dry-run, complétion depuis le serveur) sera cadré dans une EPIC ultérieure.

### Un realm = un dossier

- Sous la racine, chaque realm a son dossier, au nom fixe choisi à la création (`mon-realm/`) ; on ouvre un realm par le nom de son dossier, pas par son nom Keycloak, qui peut contenir des variables.
- Chaque fichier du dossier porte la même clé `realm` (par exemple `realm: "{{env}}-mon-realm"`), qui permet la fusion à l'import.
- Le shell **s'adapte à l'organisation existante** : il charge tous les fichiers du dossier et retient, pour chaque élément, le fichier d'où il vient. Il modifie un élément dans son fichier ; il ajoute un nouvel élément dans le fichier qui contient déjà ce type, ou, à défaut, crée le fichier numéroté standard :

  | Fichier | Contenu |
  |---|---|
  | `00-realm` | La clé `realm` et les réglages du realm |
  | `20-clients` | Les clients |
  | `30-roles` | Les rôles de realm et de clients |
  | `40-groups` | Les groupes |

  Les numéros suivent l'ordre des dépendances. À l'import, les fichiers d'un realm sont fusionnés pour le nettoyage et la détection des définitions contradictoires, mais chaque fichier est appliqué en entier, dans l'ordre des noms, avant le suivant (`ImportRealms`) : un groupe qui référence un rôle doit venir après le fichier qui définit ce rôle. La numérotation n'est qu'une convention d'organisation ; elle ne demande aucun changement du moteur d'import. Les autres types (client scopes, flows, fournisseurs d'identité…) prendront leur place dans cette numérotation quand le shell les gérera.

- Le format (JSON ou YAML) est choisi à la création du realm ; un fichier existant garde son format.
- **Première déclaration d'un type.** Avec la règle de nettoyage (ADR-007), un type déclaré par les fichiers est aligné sur eux : si le dossier ne déclarait encore aucun groupe et que `group create` écrit le premier, le prochain import supprime tous les autres groupes de Keycloak. Hors ligne, le shell ne peut pas savoir ce que Keycloak contient. Quand une commande s'apprête à déclarer un type pour la première fois dans un realm, le shell le dit clairement (« à l'import, tous les groupes de Keycloak absents des fichiers seront supprimés ; lancez un dry-run avant ») et demande confirmation ; en mode non interactif, il refuse sauf option explicite `--declare-type`. La confirmation est aussi demandée dans un realm créé par le shell : hors ligne, le shell ne peut pas savoir que Keycloak n'a pas de realm de ce nom, donc rien ne dit que le type y est vide ; en script, l'option `--declare-type` la donne.

### Édition des fichiers : réécriture depuis l'arbre du document

Un fichier est chargé en arbre, modifié, puis réécrit en entier depuis l'arbre :

- **YAML** : arbre de nœuds de SnakeYAML avec traitement des commentaires (`LoaderOptions.setProcessComments`, `DumperOptions.setProcessComments`), déjà présent dans le projet.
- **JSON** : arbre de Jackson (`ObjectNode`), avec un affichage indenté fixe.

Un prototype (24/11/2026) a validé cette approche : commentaires (y compris en fin de ligne), ordre des clés, style de chaque valeur (guillemets simples ou doubles, listes sur une ligne en YAML) et placeholders sont conservés, et une modification ne produit que ses propres lignes de diff. Mais la première réécriture change la forme : en YAML, un commentaire placé au-dessus d'un élément de liste passe à l'intérieur de l'élément et la ligne vide qui le précède disparaît ; en JSON, les listes écrites sur une ligne passent sur plusieurs. La forme est stable après deux réécritures.

D'où la commande **`realm format`** : elle met tous les fichiers d'un realm au format de l'outil, jusqu'à stabilité, sans rien changer au fond. On la lance une fois et on commite ce changement de forme à part ; les diffs suivants ne montrent que les vraies modifications. Avant une première modification, le shell détecte qu'un fichier n'est pas au format (sa réécriture sans modification diffère de lui) et propose de lancer `realm format` ; en mode non interactif, il refuse (code `2`) et demande de lancer `realm format`.

Écarté : un patch ciblé du texte (seul le bloc modifié est régénéré et recollé), qui ne toucherait jamais la mise en forme, au prix de beaucoup plus de code (indentation, insertion dans une liste, styles). La réécriture complète par Jackson est écartée aussi : elle perd les commentaires.

Une commande **`realm reorganize`**, qui redécoupe un dossier selon la numérotation standard (pour un realm venu d'un export Keycloak monolithique), est prévue en fin d'EPIC ou plus tard ; elle déplace les éléments et ne garantit pas les commentaires.

### Les placeholders dans les fichiers édités

- Le shell lit et écrit le **fichier source**, jamais sa version rendue : les placeholders sont conservés tels quels.
- Toute valeur saisie peut être une valeur en dur, une variable (`{{nom}}`, `{{nom:défaut}}`) ou une valeur composée. Le shell reconnaît les `{{…}}`, vérifie leur syntaxe (accolades fermées, nom valide, pas de triple moustache) et les écrit en chaîne entre guillemets, pour que le fichier reste du JSON ou du YAML valide avant rendu.
- Un nombre ou un booléen variable (placeholder sans guillemets, `{{{…}}}`) n'est pas géré : un fichier qui en contient ne peut pas être analysé avant rendu. Le shell ne connaît alors pas tout le realm (unicité des identifiants, références) : **tout le realm passe en lecture seule**, avec un message qui désigne le fichier et la ligne à corriger. Le realm reste importable.
- `variables list` donne les variables d'un realm : nom, valeur par défaut, fichiers où elles apparaissent. C'est ce qu'il faut fournir à l'import (`import.mustache.variables.*`).

### Un modèle de définition propre à l'édition

Les commandes ne travaillent pas sur les objets métier de la réconciliation (`Client`, `Role`, `Group` de l'ADR-002) : conçus pour comparer et appliquer, ils rangent l'essentiel de ce qu'il faut guider (URIs, flows, PKCE…) dans des attributs génériques. Le shell a son propre modèle, dans **`domain.definition`**, en Java pur :

- `ClientDefinition` : identifiant, nom, description, **type d'application** (application web avec serveur, application front-end, service à service, API), URIs de redirection, origines web, et les options qui découlent du type. Le type est propre à chaque client : un realm contient en général plusieurs clients de types différents (un front-end, son API, des services) ;
- `RoleDefinition` (rôle de realm ou de client), `GroupDefinition` (sous-groupes, rôles affectés) ;
- `TemplatedValue` : une valeur qui peut contenir des placeholders, avec la validation de leur syntaxe.

Tous les champs texte acceptent des placeholders. Le vocabulaire est celui d'un développeur (« application front-end »), pas celui de l'export Keycloak (`publicClient`, `standardFlowEnabled`).

L'adapter de fichiers traduit ce modèle vers les clés du format d'export, et dans l'autre sens. À la modification d'un élément existant, il ne réécrit que les clés que le modèle gère : les autres clés écrites à la main restent intactes.

Le type d'application d'un client correspond à une combinaison exacte de ses flows (à relire, proposition du 24/11/2026) :

| Type | `publicClient` | `standardFlowEnabled` | `implicitFlowEnabled` | `directAccessGrantsEnabled` | `serviceAccountsEnabled` |
|---|---|---|---|---|---|
| Application front-end (SPA) | `true` | `true` | `false` | `false` | `false` |
| Application web avec serveur | `false` | `true` | `false` | `false` | `false` |
| Service à service | `false` | `false` | `false` | `false` | `true` |
| API (reçoit des jetons, ne connecte personne) | `false` | `false` | `false` | `false` | `false` |

- Une clé absente vaut la valeur par défaut de Keycloak. `bearerOnly`, obsolète, n'est jamais écrit.
- La déduction est stricte : un client dont les flows ne correspondent exactement à aucune ligne est présenté comme « personnalisé », et le shell n'en modifie que les champs communs (nom, description, URIs, origines web).
- Changer le type d'un client réécrit les cinq clés selon le nouveau type.
- Les autres options de sécurité (PKCE, `pkce.code.challenge.method: S256`…) ne servent pas à déduire le type : elles viennent des règles.

### Les règles de configuration

- Une règle s'applique à un type d'élément (et, pour un client, à un type d'application) : elle **fixe des valeurs par défaut** à la création et **vérifie** un élément avant son écriture. Le refus est expliqué en termes de développeur.
- **Les règles de base sont intégrées à l'outil** : des règles de durcissement valables pour tous les utilisateurs. **Chaque utilisateur peut en ajouter** les siennes, propres à son organisation.
- Les règles ne doivent être ni codées en dur ni compliquées à maintenir : elles s'expriment dans un **formalisme déclaratif** (un fichier de règles), interprété par un moteur de règles du domaine. Les règles de base sont un fichier embarqué dans l'outil ; celles d'un utilisateur, un fichier donné au lancement. Le formalisme reste à concevoir (US-007-06) ; une règle trop riche pour lui pourra rester écrite en Java.
- Le durcissement porte aussi sur le realm : par exemple, retirer `offline_access` et `uma_authorization` des rôles par défaut du realm (`default-roles-<realm>`), sans supprimer ces rôles, dont Keycloak a besoin. *À confirmer à la conception des règles.*
- Les règles réelles seront fournies plus tard ; elles s'ajoutent sans changer le mécanisme.
- Une modification n'est écrite que si les règles et la **cohérence du realm** sont respectées : identifiant unique dans le dossier, rôles et groupes référencés définis dans le dossier ou créés par Keycloak, syntaxe des placeholders.
- **Les rôles et clients créés par Keycloak** (`realm-management` et ses rôles, `offline_access`, `default-roles-<realm>`…) ne sont définis par aucun fichier. Le shell les connaît par le realm de référence de la version de Keycloak, déjà embarqué pour la normalisation (`baseline/<version>`) : il les accepte dans les références et les propose dans les choix, moins ceux que les règles écartent.
- Un élément écrit à la main qui ne respecte pas une règle est signalé à l'ouverture du realm ; il n'est corrigé que si on le modifie.
- Une règle ne peut pas juger une valeur entièrement variable (`{{url}}`) : elle la signale comme non vérifiable, sans la refuser.

Placées dans le domaine, ces règles pourront plus tard vérifier les fichiers d'un import (commande de vérification, ou import strict), en traduisant les éléments lus vers ce modèle.

### Des questions guidées, posées par le cas d'usage

- Les commandes sont **interactives par défaut**, avec des **choix commentés** (« Quel type d'application ? 1. Application web avec serveur — le secret reste côté serveur… »). Des options permettent de répondre d'avance (`client create --id=… --type=…`) : une même commande sert à un humain et à un script ; en mode non interactif, une réponse manquante est une erreur.
- L'enchaînement des questions (lesquelles poser selon le type d'application, les valeurs proposées) est de la logique applicative : il est porté par les cas d'usage de `application.definition`, à travers un port `Questions` (saisir une valeur, choisir dans une liste commentée, confirmer). Spring Shell implémente ce port dans `infrastructure.cli.shell`. Les cas d'usage se testent avec des réponses préparées, sans terminal.

### Répartition dans les couches

```text
infrastructure.cli.shell        commandes Spring Shell, implémentation du port Questions
        │
application.definition          cas d'usage : ouvrir/créer un realm, créer/modifier un client, un rôle,
        │                       un groupe, lister les variables, formater ; ports Questions et RealmDefinitions
domain.definition               modèle de définition, TemplatedValue, règles, cohérence d'un realm
        ▲
infrastructure.source.definition  adapter RealmDefinitions : lecture du dossier, arbres SnakeYAML / Jackson,
                                  traduction modèle ↔ clés du format d'export, écriture
```

- L'adapter de fichiers est dans `infrastructure.source` : il ne dépend ni du SDK Keycloak (il écrit les clés du format d'export, sans `ClientRepresentation`) ni de `infrastructure.keycloak` (règles ArchUnit existantes).
- Une règle ArchUnit vérifie que `infrastructure.cli.shell` ne dépend ni de `infrastructure.source` ni de `infrastructure.keycloak` : il n'utilise que l'application et le domaine (par exemple le type d'application d'un client), et Spring Shell.

## Conséquences

- Les développeurs créent et modifient clients, rôles et groupes sans connaître le format d'export ni les options de sécurité de Keycloak ; les fichiers produits s'importent tels quels avec la ligne de commande.
- Les fichiers restent la seule vérité (ADR-007) : le shell n'écrit rien d'autre, et une retouche à la main reste possible.
- Un passage par `realm format` est nécessaire avant la première modification d'un fichier écrit à la main, pour que les diffs restent lisibles.
- Deux modèles coexistent pour un client : celui de la réconciliation (complet, générique) et celui de l'édition (restreint, guidé). Le second ne couvre que ce que le shell sait guider ; le reste du fichier est conservé tel quel.
- Les placeholders non quotés (nombres, booléens, fragments) rendent un fichier non modifiable par le shell.
- Le mode connecté, et avec lui l'extraction d'un realm existant de Keycloak en fichiers JSON ou YAML découpés par type, les autres types d'éléments (client scopes, policies, flows, fournisseurs d'identité, protocol mappers, attributs utilisateur, thèmes, brute force, options de connexion, required actions) et `realm reorganize` sont reportés.
