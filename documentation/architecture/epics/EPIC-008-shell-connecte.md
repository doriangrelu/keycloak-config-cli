# EPIC-008 — Shell en mode connecté

**Statut :** À faire
**ADR :** [ADR-010](../ADR-010-shell-mode-connecte.md), [ADR-008](../ADR-008-shell-edition-des-definitions.md), [ADR-004](../ADR-004-moteur-de-reconciliation.md), [ADR-007](../ADR-007-fichiers-seule-verite.md)

Connecter le shell à un Keycloak local, de développement ou de qualification (jamais la production) pour appliquer et simuler le realm ouvert, extraire un realm configuré à la main en fichiers découpés et nettoyés, comparer un élément entre Keycloak et les fichiers, et proposer dans les questions ce que Keycloak contient.

## Exemple de session visé

```text
kcc> connect dev
Secret of client 'kcc-dev' (or KCC_DEV_SECRET):
Connected to dev (https://keycloak.dev.example.org, Keycloak 26.5.3)
kcc@dev> realm extract legacy --folder legacy --format yaml
Realm 'legacy' extracted to legacy/: 00-realm.yaml, 20-clients.yaml, 30-roles.yaml, 40-groups.yaml
Variables to give at import: shopSecret, smtpPassword
kcc@dev> realm open legacy
kcc:legacy@dev> realm dry-run --var shopSecret=… --var smtpPassword=…
Dry run: no change pending.
kcc:legacy@dev> client diff frontend
In Keycloak, not in the files:
  frontchannelLogout: true
```

## Stories

Ordre décidé avec l'utilisateur : connexion, dry-run et import, extraction, comparaison, complétion.

### US-008-01 — Cibles et connexion

**Statut :** À faire
Fichier `kcc-targets.yaml` (cibles nommées, sans secret), `connect <cible>`, `disconnect`, cible affichée dans le prompt, `shell.target` pour les scripts, secret demandé sans écho ou lu dans `KCC_<CIBLE>_PASSWORD` / `KCC_<CIBLE>_SECRET`. Client Keycloak construit par cible, derrière un port de l'application.

### US-008-02 — Dry-run et import du realm ouvert

**Statut :** À faire
`realm dry-run` et `realm import` (confirmation, `--apply` en script) sur la cible, avec le plan de l'EPIC-004 ; variables par `--var`, `KCC_VAR_<NOM>` ou demandées (sans écho pour les secrets).

### US-008-03 — Première déclaration précise

**Statut :** À faire
Connecté, la confirmation de première déclaration d'un type liste ce que le prochain import supprimerait sur la cible.

### US-008-04 — Extraction d'un realm

**Statut :** À faire
`realm extract <realm> [--folder] [--format] [--with-users]` : nouveau dossier, contenu normalisé (EPIC-005), découpé (`StandardLayout`), secrets en variables, liste des variables à fournir. Critère : un dry-run juste après n'annonce aucun changement.

### US-008-05 — Comparaison d'un élément

**Statut :** À faire
`client diff`, `role diff`, `group diff`, `realm diff` : clés présentes d'un seul côté et valeurs différentes, entre l'élément normalisé sur la cible et sa vue dans les fichiers ; rien n'est écrit.

### US-008-06 — Complétion depuis Keycloak

**Statut :** À faire
Connecté, les choix proposent aussi les éléments de la cible absents des fichiers, marqués `(Keycloak)` ; les rôles et clients créés par Keycloak sont lus sur la cible.

### US-008-07 — Documentation et sample

**Statut :** À faire
Documentation utilisateur (anglais) du mode connecté ; scénarios du sample : connexion à la cible du sample, extraction d'un realm, dry-run sans changement, import, `diff` après une modification dans Keycloak.

## Hors périmètre

- La production, et toute protection par liste d'URL interdites (décision de l'utilisateur).
- La fusion de Keycloak vers des fichiers existants (aller-retour) : à cadrer si le besoin se confirme.
- Toute écriture sur Keycloak autre que l'import.

## Points de vigilance

- Le client Keycloak par cible touche l'adaptateur Keycloak (fabrique ou contexte par connexion) : à préparer dès l'US-008-01, sans casser l'import en ligne de commande.
- Les secrets ne doivent jamais apparaître dans un fichier écrit, une sortie ou un journal.
- L'extraction doit être vérifiée de bout en bout (sample) : un dry-run juste après n'annonce aucun changement.

## Réalisé

(vide)
