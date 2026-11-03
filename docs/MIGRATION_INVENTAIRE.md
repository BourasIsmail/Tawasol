# Suivi RH : inventaire pour la migration (phase M1, première version)

Source : archive `suivirh-pour-migration.zip` (code Laravel, `EXTRAITS_LOGIQUE_METIER.txt`, deux sauvegardes SQL). Les chiffres ci-dessous viennent de la sauvegarde du **27/06/2024** (la plus complète), importée dans une base de travail isolée. La production est probablement plus récente : refaire cet inventaire sur la base de production avant la migration réelle.

## 1. Barèmes lus dans les données (à faire valider, ce ne sont pas des valeurs officielles)

| Échelles | Taux 1 (MAD/repas) | Taux 2 (MAD/repas) |
|---|---|---|
| 1 à 7 et 13 | 40 | 35 |
| 8 à 10 | 60 | 48 |
| 11 | 80 | 65 |
| 99 | 100 | 80 |

- Puissance fiscale (indemnité kilométrique) : 6 ou moins 1,20 ; 7, 8 et 9 : 1,75 ; 10 ou plus : 2,30 MAD/km.
- Plafonds par programme (identiques pour toutes les échelles, sauf le programme « personnel Entraide Nationale » 1 500 pour les échelles 1 à 9 et 2 000 au-delà) : GISSR-SDI 5 000, GISSR-CDC 2 000, GISSR-CSD 1 000, GISSR-ANNEXE 700, CFA 5 000, COAPH 2 000, EMF 2 000.
- `nombre_fois_ans` vaut **0 pour les 8 programmes** de la sauvegarde : la génération par lot (division par ce nombre) ne peut pas avoir tourné avec ces réglages ; il n'y a d'ailleurs aucun déplacement « programme ».

## 2. Algorithme des repas (R2)

Le code source de `calculateMeals` est fourni. Il a été porté à l'identique dans `scripts/legacy/suivirh_calculate_meals.py` (et en JavaScript dans la maquette), puis comparé aux repas stockés (257 missions remboursables avec dates de départ et de retour) :

| Période de création | Identiques | Écarts |
|---|---|---|
| du 03 au 12/06/2024 | 98 | **44** |
| à partir du 13/06/2024 | **115** | **0** |

- Le code actuel reproduit donc **100 % des repas des missions créées à partir du 13/06/2024**. Les 44 écarts datent tous de la première semaine d'usage : une version antérieure du code appliquait des seuils plus bas (42 fois un repas de plus dans la base ; 2 fois des repas à 0 pour une mission remboursable, cause non établie). Ces constats recoupent ceux de l'annexe (§8.1).
- Décision : le moteur de TAWASSOL est le code actuel, avec **seuils paramétrables et datés** (`bareme_repas`, V10). Les repas des missions migrées sont **conservés tels que stockés** (instantané) ; les 44 écarts sont marqués `ecart_moteur_historique`.
- À trancher par la RH et le contrôle financier : quelle règle de décompte est la règle officielle. Tant que ce n'est pas fait, le moteur actuel fait référence et la valeur est « à valider ».
- Les 93 missions non remboursables ont bien tous leurs repas à 0.

## 3. Déplacements propres (490 lignes)

| Constat | Valeur |
|---|---|
| Statuts | créé 160, modifié 53, validé 262, annulé 15 ; **aucun « payé »** dans cette sauvegarde |
| Supprimés logiquement | 11 (dont 8 annulés) |
| Sans date de retour | **129** (87 créés, 42 modifiés) : 50 non remboursables, **79 remboursables sans aucun repas** à corriger |
| Sans numéro d'ordre de mission | 36 |
| Numéros d'état en double | **9** (ex. `PR / KT - 1 / 2024` deux fois) |
| Départ et arrivée identiques | **13** dont 12 non supprimées (la règle R5 les interdit) : migrées avec l'indicateur `anomalie_depart_arrivee` |
| Chevauchements missions/missions et missions/congés | 0 |
| Voiture privée : sans distance, puissance ou taux | 0 sur 51 |
| Agents hors activité avec mission | 0 |
| Années | 2023 : 3, 2024 : 487 |
| Montants hors supprimés (référence pour la réconciliation) | repas 61 636,00 ; frais de transport 40 268,02 ; kilométrique 15 532,20 MAD |

Les compteurs d'ordre de mission des délégations sont vides (numéros saisis librement pour les déplacements propres) ; 60 délégations sur 83 ont un compteur d'état.

## 4. Personnes, comptes, congés, divers

- **2 628 personnes** : matricules tous distincts (5 caractères, ex. `P1635`), aucun CNIE manquant ni en double, 52 sorties de service, 2 supprimées, 2 356 sans unité administrative (normal : l'unité ne concerne que le siège). Tous ont un `programme_id`. Nom et prénom sont dans un seul champ (français : `NOM PRÉNOM` en majuscules ; arabe : ordre variable).
- **117 comptes, aucun lié à un agent** : 82 délégations, 12 coordinations, 16 unités administratives, 7 comptes de cellules, 1 super-administrateur. Tous les comptes sont des comptes d'entité : il faudra créer des comptes individuels.
- **Droits déplacements** (43 permissions au total, 20 pour les déplacements) :
  - Les comptes **délégation, coordination et unité administrative peuvent créer, modifier et valider** les mêmes missions. La séparation « celui qui saisit ne valide pas » n'existe pas dans l'existant.
  - La validation du paiement est réservée à la cellule de paie et au super-administrateur ; la suppression à la cellule des RH et au super-administrateur.
  - La cellule des congés n'a aucun droit sur les déplacements ; le superviseur est en lecture.
- **2 024 congés** (8 types présents : annuel 1 832, exceptionnel 30, maternité 19, maladie courte 84, longue 6, moyenne 4, sans solde 1, permission d'absence 48), statuts créé 652, validé 1 303, modifié 57, annulé 12.
- 83 délégations (symboles tous distincts, tous avec un signataire), 13 coordinations dont la n° 13 « siège central », 18 jours fériés, 12 messages (tous avec réponse).

## 5. Points de conformité et décisions à consigner

1. **Génération aléatoire (R7)** : le code tire au hasard dates (09:00 par défaut) et délégation d'arrivée pour consommer un plafond, avec objet fixe « مهمة إدارية ». Non reproduit. À trancher par la direction et le juridique.
2. **Séparation des tâches** : cible TAWASSOL = le responsable valide en premier niveau, la cellule des déplacements valide, la cellule de paie valide le paiement, personne ne cumule saisie et validation. Rompt avec l'usage actuel des comptes d'entité.
3. **Montants** : `double` en source, `DECIMAL(10,2)` en cible. Taux copiés dans chaque mission conservés (jamais recalculés).
4. **Données sensibles** : CNIE chiffré, jamais dans les rapports ni les journaux.

## 6. Décisions de l'annexe (section 8) et points restant ouverts

Décidé :
- Moteur de repas, missions sans retour (indicateur `retour_non_renseigne`), départ égal à arrivée (indicateur `anomalie_depart_arrivee`), comptes d'entité migrés désactivés (`est_compte_legacy`), séparation saisie / validation de niveau 1 / validation de la cellule des déplacements / validation du paiement : voir `docs/MIGRATION.md`.
- Valeurs de réconciliation recalculées indépendamment : repas 61 636,00 ; transport 40 268,02 ; kilométrique 15 532,20 MAD (479 missions non supprimées).

Restent ouverts :
1. Programmes : `nombre_fois_ans` vaut 0 dans la sauvegarde alors que les seeders prévoient 12 (GISSR) et 1 (CFA, COAPH, EMF). À vérifier sur la production : `SELECT code, nombre_fois_ans FROM programmes;` et `SELECT COUNT(*), MIN(created_at), MAX(created_at) FROM deplacement_programmes;`. Si la génération est utilisée, appliquer le point de conformité R7 avant toute reprise.
2. Règle de décompte officielle des repas (seuils de la première semaine ou seuils actuels).
3. Texte et date d'effet des barèmes, taux kilométriques et plafonds.
4. Désignation par la RH des personnes qui reçoivent un compte individuel avec périmètre.
