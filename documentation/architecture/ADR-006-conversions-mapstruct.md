# ADR-006 — Conversions avec MapStruct

## Statut

Accepté — refonte 7.0.0.

## Contexte

Avec des BOs purs (ADR-002), chaque type de ressource a besoin de conversions `*Representation` → BO (lecture de l'existant et du fichier d'import) et BO → `*Representation` (création, mise à jour). Écrites à la main, ces conversions représentent beaucoup de code répétitif, où un champ oublié passe inaperçu.

## Décision

- Les conversions sont des interfaces MapStruct, une par type de ressource : `<Ressource>KeycloakMapper`, dans `infrastructure/keycloak/<ressource>/`.
- Les mappers sont configurés avec `unmappedTargetPolicy = ReportingPolicy.ERROR` : un champ ajouté au BO sans conversion fait échouer la compilation.
- Le domaine n'importe jamais MapStruct ; les mappers ne contiennent aucune règle (pas de diff, pas de filtre).
- Version de MapStruct figée dans `pom.xml`, processeur d'annotations déclaré dans `maven-compiler-plugin`.
- Lombok n'est pas utilisé : les BOs sont des records.

## Conséquences

- Nouvelle dépendance de compilation (`org.mapstruct:mapstruct`, `mapstruct-processor`), licence Apache 2.0, activement maintenue.
- Chaque mapper a un test aller-retour (Representation → BO → Representation) sur un jeu de données complet.
- Le mapper gère aussi les champs non modélisés (`UnmodeledAttributes`, ADR-002) : il y range les champs de la `*Representation` non couverts par le BO, et les restitue à l'écriture.
