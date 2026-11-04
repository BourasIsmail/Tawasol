# Migration de Suivi RH vers TAWASSOL (phase M1 : schéma et correspondances)

Statut : **proposition à valider**. Aucune donnée n'est migrée à ce stade. Source : sauvegardes Suivi RH du 12/06 et du 27/06/2024 (jeux d'essai) ; la migration finale part de la base de production à jour. Constats chiffrés : `docs/MIGRATION_INVENTAIRE.md`.

## 1. Principes
- Outil dédié (CLI `tawassol-migrate`, phase M2/M4), **à blanc** puis réel, transactions par lot, aucune suppression dans la source, source lue en lecture seule.
- Chaque ligne migrée est enregistrée dans `migration_map(run_id, table_legacy, id_legacy, table_cible, id_cible, empreinte)` rattachée à un `migration_run`. Rejouer ne crée pas de doublon ; supprimer un `migration_run` supprime ses correspondances (annulation).
- L'identifiant d'origine est conservé dans `id_legacy` (index unique) sur les tables migrées. Nouveaux identifiants : UUID.
- Les horodatages sources (`created_at`) sont en UTC : à convertir en Africa/Casablanca s'ils servent de date (ex. férié créé le 2023-12-31 22:00 UTC). Les colonnes de type date (`jour`, dates de mission) sont reprises telles quelles.
- Aucune donnée n'est inventée : date manquante = vide, auteur de validation inconnu = `date_validation_approximative` et ligne d'historique « migré ».

## 2. Schéma ajouté (migrations V10 et V11, testées)
- **V10** : `ordre_mission` étendu (circuit `brouillon, soumise, validee_responsable, approuvee, payee, rejetee, annulee`, `date_retour` facultative, véhicule, montants `DECIMAL(10,2)`, origine, indicateurs de migration) ; `ordre_mission_historique` ; `bareme_indemnite` ; `bareme_repas` ; `puissance_fiscale` ; `programme_mission_plafond` et `nombre_cycles_an` ; `personnel_programme` ; `delegation_compteur` (par délégation, année et type) ; `signataire_autorisation` (historisé) ; `migration_run` et `migration_map` ; `delegation_province.symbole`, `est_siege` (plus d'identifiant codé en dur).
- **V11** : `role`, `permission`, `role_permission` (13 permissions, 8 rôles, séparation des tâches testée) ; `responsabilite` ; colonnes de reprise de `personnel` (CNIE chiffrée avec empreinte, situation de service, stagiaire, unité administrative, `a_completer`, noms d'origine) ; `structure.type = unite_administrative` ; `id_legacy` sur les tables migrées.
- Les statuts des ordres de mission existants sont convertis (`en_attente` en `soumise`, `approuve` en `finalisee`, `rejete` en `rejetee`, `annule` en `annulee`).
- Seeds de référence lus dans Suivi RH : barèmes par échelle (14), puissances fiscales (5), seuils des repas (3), tous `non_valide`.

## 3. Correspondances table par table

| Source (Suivi RH) | Cible (TAWASSOL) | Règles |
|---|---|---|
| `holidays` | `jour_ferie` | `jour` est déjà une date (pas de conversion) ; `nom` copié en `libelle_fr`, `libelle_ar` à compléter ; fêtes religieuses `a_confirmer` |
| `administrations` | `personnel.administration_origine` | texte libre |
| `situation_administratives` | `personnel.situation_service` + `est_actif` | `sortie_de_service` donne `est_actif = 0`, les missions sont conservées |
| `responsabilites` | `responsabilite` | `id_legacy` ; alimentera `responsable_perimetre` après validation RH (coordinateur régional : sa coordination ; délégué provincial : sa délégation) |
| `moyen_transports` | `moyen_transport` | par `code` (`transports_en_commun`, `voiture_de_service`, `voiture_privee`) |
| `puissance_fiscales` | `puissance_fiscale` | `taux` en `taux_km`, date d'effet à documenter, `statut_validation = non_valide` |
| `deplacement_programme_statuses` | statut d'`ordre_mission` | `generated`, `created` et `modified` en `approuvee` quand un numéro d'ordre existe (l'ordre est déjà émis et signé), sinon `soumise` ; `modified` pose en plus l'indicateur `modifie` ; `finalized` en `finalisee` (état d'indemnités existant) ; `paid` en `payee` ; `cancelled` en `annulee` |
| `programmes` | `programme_mission` + `programme_mission_plafond` | `code`, `name_trans` (JSON) en `nom_fr` et `nom_ar` ; `montant_max_fois` (JSON par échelle) en lignes de plafond ; `nombre_fois_ans` en `nombre_cycles_an` |
| `coordinations` (13) | `coordination_region` | **table manuelle des 13 vers R01 à R12 + siège (`est_siege`)**, à valider par la RH ; signataire vers `signataire_autorisation` |
| `delegations` (83) | `delegation_province` | `symbols` en `symbole` ; la n° 83 (`SC`) porte `est_siege` ; `num_*_counter` (JSON) donne le maximum par année dans `delegation_compteur` ; signataire vers `signataire_autorisation` avec dates d'effet |
| `structures` (49 unités administratives) | `structure` (type `unite_administrative`) | rattachées à la délégation siège ; `personnel.unite_administrative_id` |
| `grades` (39) | `grade` + `bareme_indemnite` | `echelle` conservée ; `taux_1`, `taux_2` en barème daté (un taux par échelle) ; l'instantané de chaque mission est conservé |
| `personnes` (2 628) | `personnel` | voir §4 |
| `roles` (9) | `role` + `role_permission` | voir §5 |
| `users` (117) | `utilisateur` | voir §5 |
| `conges` (2 024) | `demande` (`source_legacy = conge`) | `finalized` en `approuvee`, `created` et `modified` en `soumise`, `cancelled` en `annulee` ; 4 types ajoutés (V15) : permission d'absence, maternité, sans solde, accident du travail ; dates invalides (année 0004) laissées vides et signalées ; numéro d'origine dans `donnees_json` |
| `deplacement_propres` (490) | `ordre_mission` (`source_legacy = propre`) | voir §6 |
| `deplacement_programmes` | `ordre_mission` (`source_legacy = programme`, `origine = generation`) | historique uniquement, avec `parent_ordre_id` ; **aucune génération aléatoire n'est reprise** |
| `messages` (12) | `legacy_archive` (lecture seule) | les messages viennent de comptes d'entité, sans agent : aucune réclamation ne peut leur être rattachée, ils sont archivés intégralement |
| `centers`, `applications` et tables associées | hors périmètre | archivées dans `legacy_archive` en lecture seule |

## 4. Personnes
- **Noms** : `nom_prenom_fr` (nom en majuscules puis prénom) et `nom_prenom_ar` (ordre variable) sont scindés par heuristique ; le texte d'origine reste dans `nom_prenom_*_legacy` et un **fichier de contrôle manuel** liste les cas ambigus.
- **Matricule** : unique, 5 caractères dans les données (limite 10) ; jamais de fusion automatique de deux personnes.
- **CNIE** : chiffrée (`cnie_chiffre`) avec empreinte (`cnie_empreinte`) ; masquée dans tous les rapports et journaux. La colonne `cin` historique de TAWASSOL reste vide pour les agents migrés.
- **Sans unité ou structure** (2 356 agents sans unité, ce qui est normal hors siège) : rattachés à une structure « à compléter » par délégation (`a_completer = 1`). **Sans date de recrutement** : la date reste vide. Les deux cas sont listés pour la RH.
- Supprimés logiquement (`deleted_at`) : migrés inactifs, jamais ignorés. `programme_id` devient une ligne de `personnel_programme`.

## 5. Rôles et comptes
- Rôles : super-administrateur vers `admin_technique` ; cellule des RH vers `rh_generaliste` ; cellule des déplacements vers `cellule_deplacements` ; cellule de paie vers `cellule_paie` ; superviseur vers `supervision` ; cellule des congés vers `rh_generaliste` (permissions congés) ; délégation, coordination, unité administrative vers `responsable_standard` avec périmètre. **À valider.**
- **Séparation des tâches** (testée dans `MigrationIT`) : saisie, validation de premier niveau, validation de la cellule des déplacements et validation du paiement sont des permissions distinctes qu'aucun rôle ne cumule. Constat de l'existant : les comptes délégation, coordination et unité administrative peuvent créer, modifier et valider les mêmes missions ; la validation du paiement n'est ouverte qu'à la cellule de paie et au super-administrateur.
- **Comptes** : les 117 sont des comptes d'entité, aucun n'est lié à un agent. Ils sont migrés **désactivés** (`est_compte_legacy = 1`, conservés pour l'audit, jamais supprimés). La RH désigne les personnes qui reçoivent un compte individuel avec périmètre. Hachages bcrypt acceptés seulement si le moteur d'authentification les accepte (c'est le cas) avec changement de mot de passe obligatoire ; jamais de mot de passe en clair ni de hachage dans un rapport.

## 6. Déplacements
- **Montants** : `double` converti en `DECIMAL(10,2)` (arrondi commercial). Les taux de chaque mission (`indemnite_taux_1`, `indemnite_taux_2`, `taux_km`) sont conservés, jamais recalculés.
- **Repas** : conservés tels que stockés. Les 44 missions créées du 03 au 12/06/2024 dont les repas diffèrent du moteur actuel sont marquées `ecart_moteur_historique`.
- **Sans date de retour** (129) : migrées avec `retour_non_renseigne` ; 79 remboursables sans repas, listées pour la cellule des déplacements. **Départ égal à l'arrivée** (13) : `anomalie_depart_arrivee`.
- **Statut** : voir la table de correspondance ; une ligne d'historique unique « migré » (date de dernière modification, auteur inconnu) ; `date_validation_approximative = 1` pour les validées et payées.
- **Numéros** : `numero_ordm_legacy` et `numero_etat_legacy` (V13) conservent les numéros d'origine tels quels ; `numero_ordm` et `numero_etat` sont réservés aux nouveaux numéros (uniques). Les missions sans numéro (36) et les numéros d'état en double (9) sont migrées avec `anomalie_numero` (`numero_absent`, `etat_en_double`), sans rien inventer. Compteurs : maximum entre le compteur JSON de la délégation (déjà repris en M2) et le plus grand numéro numérique des missions, par délégation, par année et par type, pour éviter toute collision.
- **Missions du 03 au 12/06/2024** (44) : marquées « montant historique non reproductible » (`ecart_moteur_historique`), montants figés.
- Supprimées logiquement (11) : migrées avec `supprime_le`.

## 7. Contrôles de réconciliation (automatisés, rapport signé avant bascule)
1. Nombre d'enregistrements par table : source = cible + écartés justifiés.
2. Sommes par année, délégation, programme et statut : écart nul au centime. Valeurs de référence du 27/06/2024 (479 missions non supprimées) : repas 61 636,00 ; transport 40 268,02 ; kilométrique 15 532,20 MAD.
3. Recalcul de chaque mission avec le moteur de TAWASSOL et comparaison aux montants stockés, écarts expliqués (attendus : les 44 de la première semaine).
4. Unicité des matricules et des numéros, clés étrangères, zéro orphelin non expliqué.
5. Régénération d'états PDF par délégation et par programme, comparaison visuelle ; sondage RH (50 agents, 100 missions).

## 8. Bascule et retour arrière
Répétitions complètes sur une copie (deux au minimum), gel de la saisie, migration finale puis delta, recette par profil, Suivi RH conservé en lecture seule, restauration documentée de la base cible, archivage selon la loi 69-99. **Aucune migration sur une base réelle sans validation écrite.**

## 9. Modèles PDF de Suivi RH (reçus après la phase M1)
Archive de référence : `docs/legacy/modeles-pdf/` (quatre modèles Blade, configuration mPDF, logo). La police `arabic-typesetting.ttf` n'est volontairement **pas** copiée dans le dépôt : sa licence (Microsoft) n'est pas établie.

Documents à reproduire en M3 : ordre de mission (arabe, RTL, avec attestation de présence), état récapitulatif des sommes dues (français), état des indemnités kilométriques (voiture privée, numéro préfixé `K`), ordres groupés par programme, récapitulatifs (liste par agent ; état global avec totaux par programme).

À corriger, pas à reprendre :
- fonctions du directeur codées en dur par identifiant de personne (à remplacer par `signataire_autorisation`, daté) ;
- code QR sans vérification possible (remplacé par un jeton public) ;
- montant en lettres en français seulement, sans unité ni centimes (bilingue, avec dirhams et centimes) ;
- pas de numéro unique de document ; pied de page réservé au siège ;
- l'état d'indemnités n'apparaît qu'une fois la mission finalisée (règle conservée : statut `approuvee` ou `payee`).

### Décisions sur les polices et le logo (réponse de l'utilisateur)
- Police arabe des PDF : **Amiri** (licence SIL OFL, embarquable). Police latine : **Noto Sans** (ou Source Serif), également libres. Arabic Typesetting n'est ni copiée ni utilisée côté serveur sans licence établie.
- À tester en M3 : chiffres, diacritiques, mélange arabe/français dans une même ligne (tableaux bilingues, montants). Le rendu différera légèrement de l'ancien modèle ; l'établissement le valide avant la mise en production.
- Logo : l'en-tête officiel bilingue (tifinagh, arabe, français, anglais) a été transmis par l'utilisateur ; `logo.png` de l'archive est conservé comme référence. À confirmer par l'établissement avec la validation du rendu.

## 10. Phase M2 : référentiels, personnel, comptes (livrée, à valider)
Commande `tawassol-migrate` : profil Spring `migrate` de l'API (classes `ma.entraide.tawassol.migration.*`). Rejouable, source en lecture seule.

```bash
java -jar tawassol-api.jar --spring.profiles.active=migrate \
  --tawassol.migrate.source.url=jdbc:mysql://HOTE/suivirh --tawassol.migrate.source.user=... --tawassol.migrate.source.password=... \
  --tawassol.migrate.mode=a_blanc --tawassol.migrate.output-dir=./migration-out
```

- **`a_blanc`** : tout est exécuté puis annulé (transaction), seule la ligne `migration_run` et le rapport restent. **`reel`** : exige `--tawassol.migrate.validation-ecrite=<référence du document de validation>` et `--tawassol.migrate.cnie-key=<32 octets en base64>` ; sans eux la commande refuse.
- **Sorties** : `rapport-<run>.json` (compteurs par table, anomalies par code), `anomalies-<run>.csv`, `controle-noms-<run>.csv` (noms à contrôler à la main), `comptes-perimetres-<run>.csv` (périmètre d'origine de chaque compte d'entité). Aucune CNIE n'y figure.
- **Annulation** : supprimer la ligne `migration_run` supprime ses correspondances ; les lignes reprises se retrouvent par `id_legacy`.

Résultat de l'essai à blanc sur la sauvegarde du 27/06/2024 (aucune donnée conservée) : 13 coordinations (12 régions rattachées et le siège créé), 83 délégations, 49 unités administratives, 39 grades, 10 responsabilités, 8 programmes et 112 plafonds, 18 jours fériés, **2 628 personnes** (aucune ignorée, aucun doublon de matricule ni de CNIE, 2 628 CNIE chiffrées), 117 comptes d'entité (désactivés), 96 signataires. Barèmes et taux kilométriques identiques à la cible.

Décisions de mise en oeuvre :
- Les 12 régions existantes sont rattachées par leur numéro (1 à 12 vers R01 à R12) ; la 13e (siège) est créée avec `est_siege`.
- `personnel.structure_id` : l'unité administrative réelle si la personne en a une (272), sinon une structure « à compléter » par délégation (83 créées, 2 356 personnes, `a_completer = 1`). Les 52 personnes « sortie de service » sont inactives.
- Signataires : Suivi RH ne conserve que la **qualité** (« le délégué provincial »), jamais le nom : `nom_fr` et `nom_ar` restent vides, à renseigner par la RH.
- Programmes : le code atteint 33 caractères, colonne élargie à 60 (migration V12). Plafonds et date d'effet (date de création de la ligne source) : `non_valide`.
- Fériés : l'arabe n'existe pas dans la source (repris du français en attendant) ; les fêtes religieuses sont `a_confirmer`.
- Noms : le dernier mot français est le prénom, le dernier mot arabe le nom (avec particule) ; **167 cas ambigus** listés pour contrôle, le texte d'origine est conservé.
- Comptes : bcrypt `$2y$` conservé (le moteur d'authentification l'accepte), désactivés, mot de passe à changer, sans périmètre tant que la RH n'a pas désigné les personnes.

## 11. Phase M4 : déplacements, congés, messages, réconciliation (livrée, essai à blanc et essai réel sur base jetable)
Les étapes `deplacements`, `conges`, `messages`, `initialiserCompteurs` et `reconciliation` s'ajoutent à la commande M2 (mêmes options, mêmes garde-fous). Les migrations V13 à V15 portent les numéros hérités, le statut `finalisee`, la table `legacy_archive` et les types de congé.

Résultat sur la sauvegarde du 27/06/2024 (essais sur une base cible jetable, jamais sur une base réelle) :

| Contrôle | Source | Cible | Écart |
|---|---|---|---|
| Missions non supprimées | 479 | 479 | 0 |
| Somme des repas (MAD) | 61 636,00 | 61 636,00 | 0,00 |
| Somme des frais de transport en commun (MAD) | 40 268,02 | 40 268,02 | 0,00 |
| Somme des indemnités kilométriques (MAD) | 15 532,20 | 15 532,20 | 0,00 |
| Congés | 2 024 | 2 024 | 0 |
| Personnes | 2 628 | 2 628 | 0 |

- 490 missions reprises (11 supprimées conservées avec leur date de suppression) : 186 approuvées, 262 finalisées, 27 soumises, 15 annulées. Aucune mission de programme dans la sauvegarde (planification à confirmer avant toute reprise de cet historique).
- Anomalies signalées sans rien inventer : 36 sans numéro d'origine, 9 numéros d'état en double (17 missions), 129 sans date de retour, 13 départ égal à arrivée, 1 congé à date invalide. Aucun écart de moteur inattendu : les 51 missions dont les repas diffèrent du moteur actuel (44 non supprimées et 7 supprimées) ont toutes été créées du 03 au 12/06/2024 et portent « montant historique non reproductible ».
- Compteurs : 121 compteurs par délégation, année et type, au maximum de l'ancien système (lignes supprimées comprises).
- Rejouer ne crée aucune ligne (deuxième passage : 0 création).
- Le droit annuel n'existe pas dans Suivi RH : il suit le paramètre (22 jours, à valider, H-CONGE-1). Les jours pris sont repris tels que Suivi RH les compte : jours imputés à l'année courante et à l'année précédente de chaque congé (créé, modifié et validé comptent, annulé non), écrits dans `conge_imputation`, puis soldes reconstitués de façon rejouable et marqués « à vérifier » (anomalie `SOLDES_A_VERIFIER`, compteur `CONGES_IMPUTES`). Les demandes « soumises » à période passée sont à régulariser par la RH (H-CONGE-5).

**Reste à faire avant la bascule (M5), hors de ce dépôt** : répétition complète sur une copie de la base de production avec le rapport de réconciliation signé, recette par profil, gel de la saisie, migration finale puis delta, conservation de Suivi RH en lecture seule, plan de retour arrière. **Aucune migration sur une base réelle sans validation écrite** : le mode réel refuse de démarrer sans référence de validation.

## 12. Chargement des données réelles fournies (03/11/2026)

Trois exports ont été fournis : la base complète de Suivi RH, `salaire_fonctionnaire` (bulletins de janvier à septembre 2026) et `Note_Annuelle` (2018 à 2025). Ils se chargent dans **un seul schéma source** (les noms de tables ne se recoupent pas), puis `tawassol-migrate` reprend tout en une passe : référentiels, personnel, comptes d'entité, missions propres et de programme, congés, messages, salaires, notes, compteurs, réconciliation, et (option `--tawassol.migrate.comptes-test=true`) comptes de test.

| Donnée | Source | Reprise |
|---|---|---|
| Personnel | 3 023 | 3 023 (CNIE chiffrées, 2 740 actifs) |
| Structures, délégations, coordinations, grades, programmes | 102, 83, 13, 75, 64 | tous |
| Missions propres | 10 796 (10 683 non supprimées) | 10 796 ; repas, transport et kilométrique : écart nul |
| Missions de programme | 16 141 (12 745 supprimées) | 16 141, origine « generation », historique tel quel |
| Congés | 14 489 | 14 489 (dont 78 de type paternité, repris comme maternité) |
| Salaires | 23 622 lignes, 9 lots mensuels | 23 593 ; 29 lignes de matricules inconnus ignorées ; échelon de la fiche mis à jour (2 781 agents) |
| Notes annuelles | 21 363 | 16 820 publiées ; 4 267 de matricules inconnus (agents sortis) et 64 vides ignorées |

Décisions et constats :
- **Rattachement organisationnel** : dans Suivi RH, 2 718 agents ont la structure « AUTRE » (dont 2 391 en délégation). Ce n'est pas une unité : ces agents sont rattachés à leur délégation, et seules les unités centrales rattachent les agents affectés au siège (296 agents). Sinon la chaîne de validation passerait par le siège pour des agents de délégation.
- **Génération automatique en production** : 60 programmes ont un nombre de cycles annuels non nul et 16 141 missions de programme existent. La génération (dates et destinations tirées au hasard) est donc réellement utilisée dans Suivi RH. TAWASSOL ne la reproduit pas ; seul l'historique est repris. Décision de la direction attendue sur la planification réelle.
- **Salaires** : les colonnes `echl` et `date_echl` ne sont pas reprises (signification à confirmer par la paie). `ech` est reporté comme échelon et `date_ech` comme date d'échelon de la fiche.
- **Notes** : « 17.5 » et « 17.50 » ne font qu'une ; deux valeurs différentes seraient signalées et la plus récente gardée.
- **Missions sans numéro d'origine** : 2 378 (propres et programme) ; numéros d'état en double : 68 lignes.
- **Comptes de test** : 8 comptes `test.*` rattachés à de vrais agents (deux agents, un responsable de structure, un délégué, un coordinateur, la cellule des déplacements, la cellule de paie, un profil RH). Suivi RH ne dit pas quelle personne exerce quelle responsabilité : ces désignations sont arbitraires et **à supprimer avant la production**. Les mots de passe sont aléatoires, écrits une seule fois dans un fichier local `IDENTIFIANTS-COMPTES-TEST-*.csv` (droits 600, jamais dans le rapport ni dans git), et doivent être changés à la première connexion.
- **Données personnelles** : les exports, la base reprise et les identifiants ne sont jamais publiés dans le dépôt.

Charger l'export de la base reprise dans un MySQL Docker :
```bash
gunzip -c tawassol_donnees_reelles.sql.gz | docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" tawassol'
```

