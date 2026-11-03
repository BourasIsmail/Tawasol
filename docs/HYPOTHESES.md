# Hypothèses TAWASSOL

Hypothèses prises en Phase 0 faute de source. Chacune est à confirmer.

1. Le circuit est : soumise → validée par le responsable → approuvée par la RH. La RH peut traiter directement une demande sans responsable.
2. Une demande d'attestation n'exige pas la validation du responsable par défaut (paramétrable par type).
3. Dans ordre_mission, indemnite_taux_1 et indemnite_taux_2 sont des tarifs unitaires par repas, et le montant retenu est plafonné par programme_mission.montant_max.
4. Le schéma actuel n'a pas : date de naissance, CIN, sexe, situation familiale, téléphone, position administrative. Ils sont proposés comme champs à ajouter.
5. personnel.echelon est un varchar(10) alors que echelle est un entier : un échelon est traité comme une valeur textuelle à normaliser.
6. annonce et document n'ont pas de ciblage : une migration ajoutera la cible (tous, délégation, structure).
7. Le portail restitue les données de paie. Il ne calcule pas la paie officielle.
8. Toutes les valeurs chiffrées des maquettes (droit à congé 22 j, plafond d'avance 5 000 MAD, taux de repas, durées d'échelon, délai de réponse 5 jours) sont des exemples à valider.
9. Le compte admin n'est lié à aucun agent et doit changer son mot de passe à la première connexion.
10. Le calendrier d'équipe et les rapports ne montrent pas le motif des congés maladie.
11. Les salaires et crédits sont alimentés par fichiers Excel ou CSV via des modèles d'import enregistrés en base (encodage, séparateur, colonnes, formats) : un changement de format ne demande aucun développement. Un connecteur automatique (SFTP ou API) pourra venir plus tard.
12. L'effectif est d'environ 2 600 à 3 000 agents, soit environ 3 000 lignes de salaire par mois.
13. Aucun bulletin officiel PDF n'existe : le portail génère un relevé informatif à partir des données importées. Une table de rubriques (salaire_ligne) permet un bulletin détaillé quand les colonnes seront disponibles.
14. Les lignes importées entrent en brouillon dans un lot. La publication du mois est une action distincte et irréversible.
15. Un congé à cheval sur deux années est imputé sur l'année de la date de début.
16. Sans ligne dans `solde_conge`, le droit annuel est celui du paramètre `conge.annuel.droit_jours` (valeur d'exemple, non validée).
17. La capacité d'endettement d'une avance se calcule comme (somme des mensualités de crédits publiés) / (dernier salaire net publié), comparée à un pourcentage paramétrable (33 %, exemple non validé). Sans bulletin publié, le contrôle est ignoré et signalé par un avertissement.
18. Le numéro de demande est attribué à la soumission, pas au brouillon, pour garantir une suite sans trou.
19. L'agent peut annuler une demande tant qu'elle n'est ni approuvée ni rejetée (brouillon, soumise ou validée par le responsable).
20. Aucun antivirus n'est branché : seule la vérification du type réel du fichier est faite.
21. Le responsable compétent est le plus proche dans l'arbre des structures, puis la délégation, puis la coordination. À égalité, le plus ancien périmètre l'emporte. Il est figé à la soumission.
22. Un responsable sans supérieur voit sa demande traitée directement par la RH.
23. Le responsable ne voit pas le motif ni les pièces d'un congé maladie (données de santé) : seule la RH y accède.
24. Le suppléant n'acquiert que la file de validation et la notation du périmètre ; il ne peut pas traiter sa propre demande, qui reste en attente du responsable ou de la RH.
25. Un suppléant doit appartenir au périmètre du responsable qui le désigne.
26. Un responsable est noté par son supérieur hiérarchique, ou par la RH s'il n'en a pas.
27. Les appréciations proposées (Très bien, Bien, Assez bien, Passable, Insuffisant) sont des suggestions libres, à remplacer par la grille réglementaire.
28. Le lien de réinitialisation est valable 30 minutes et envoyé à l'e-mail de la fiche : un agent sans e-mail doit passer par la RH ou l'admin.
29. Le délai de réponse aux demandes est de 5 jours (paramètre d'exemple, non validé) ; la file RH signale les demandes hors délai.
30. L'approbation d'un congé annuel décompte le solde de l'année de la date de début ; les congés maladie et exceptionnels ne touchent pas le solde annuel.
31. Les ordres de mission ne s'approuvent pas depuis la file générale : le calcul de l'indemnité (4B) est un préalable.
32. Les imports de salaires portent sur un mois choisi à l'import (pas une colonne de période par ligne).
33. Une ligne de crédit remplacée par un import ultérieur du même dossier passe en « remplace » ; l'ancienne ligne est conservée.
34. Le fichier d'import est envoyé à chaque étape du parcours (pas de stockage intermédiaire). Taille maximale 10 Mo (paramètre).
35. L'import des notes et des examens n'est pas encore pris en charge ; la RH saisit les notes dans l'écran de notation.
36. La publication des notes se fait en bloc pour toutes les notes validées d'une année.
37. Le calcul des agents éligibles à l'avancement d'échelon et les examens de grade sont en phase 4B.

## Déplacements : PDF
- H-PDF-1 : polices Amiri (arabe) et Noto Sans (latin), libres de droit ; rendu validé par l'établissement avant production.
- H-PDF-2 : en-tête officiel = logo bilingue fourni ; pied de page paramétrable par entité.

## Déplacements : moteur de calcul (M3a)
- H-MISSION-1 : le décompte des repas reproduit à l'identique l'algorithme actuel de Suivi RH (514 cas de référence). Seuils lus dans `bareme_repas` et `mission.repas.*` à la date de départ, **non validés** : un barème non validé est signalé (`BAREME_NON_VALIDE`) à chaque calcul.
- H-MISSION-2 : indemnité = repas au taux 1 puis au taux 2 après le seuil de bascule (15 jours), plus frais de transport (transports en commun seulement), plus indemnité kilométrique (voiture privée : distance x trajets x taux). Mission non remboursable : aucun repas. Arrondi au centime (demi supérieur).
- H-MISSION-3 (validé) : aucun seuil n'existe avant le 13/06/2024 : le calcul est refusé pour ces dates, pour ne produire aucun faux montant. Les montants migrés restent figés tels que payés, jamais recalculés. Les 44 missions du 03 au 12/06/2024 dont les repas diffèrent du moteur sont marquées « montant historique non reproductible » (`ecart_moteur_historique`) ; cela ne bloque pas la migration. Question à poser à la cellule des déplacements ou à la paie : règle appliquée cette première semaine. Si elle ne peut pas être établie, le marquage suffit.
- H-MISSION-3b (ouvert, service juridique) : la règle officielle de décompte des repas (seuils d'heures et intervalles) doit reposer sur un texte réglementaire sur les frais de déplacement, à citer par le service juridique. Les seuils actuels sont du code de Suivi RH, pas une base légale prouvée ; ils restent « non validés » dans `bareme_repas`.
- H-MISSION-4 (principe validé) : gabarits de numéros par défaut `{symbole}-{annee}-{n:5}` (ordre de mission) et `E{symbole}-{annee}-{n:5}` (état), modifiables par les paramètres `mission.numero.gabarit_ordm` et `gabarit_etat` ; le format officiel est à confirmer par la RH (Suivi RH ne conserve qu'un numéro d'ordre par délégation et par année).
- H-MISSION-5 (validé) : les numéros hérités sont conservés tels quels dans `numero_ordm_legacy` et `numero_etat_legacy` (ex. « PR / SB - 1 / 2024 »), sans reformatage : ce sont des références déjà imprimées et signées. Le nouveau format ne s'applique qu'aux nouveaux documents (`numero_ordm`, `numero_etat`, uniques).
- H-MISSION-6 (validé) : les missions sans numéro (36 constatées) et les numéros d'état en double (9) sont migrés avec l'indicateur `anomalie_numero` (`numero_absent`, `etat_en_double`), sans rien inventer ni corriger.
- H-MISSION-7 (validé) : les compteurs sont initialisés au maximum de l'ancien système, par délégation et par année : maximum entre le compteur JSON de la délégation et le plus grand numéro numérique des missions migrées (M4). L'attribution est atomique et sans trou (le compteur est annulé avec la transaction).
- H-MISSION-8 (validé, à confirmer par la RH en M3b) : **deux moments distincts**. Le numéro d'**ordre** est attribué à l'**approbation**, avant le déplacement : l'ordre est alors signé et remis à l'agent. Le numéro d'**état** est attribué à la **finalisation**, après le retour, comme dans Suivi RH : l'état d'indemnités n'existe qu'avec les dates, heures et repas réels. Ainsi une mission approuvée puis annulée ou jamais finalisée ne consomme aucun numéro d'état, et la séquence des états reste sans trou (contrôle d'audit simple). Un numéro d'état dès l'approbation ne serait possible que par décision explicite de la RH, avec une règle d'annulation (numéro annulé conservé, visible « annulé », jamais réutilisé).
- Circuit : brouillon, soumise, validee_responsable, **approuvee** (numéro d'ordre), **finalisee** (numéro d'état), payee ; rejetee et annulee possibles avant le paiement (V14).

## Déplacements : circuit, documents, reprise (M3b, M3c, M4)
- H-MISSION-9 : une mission héritée avec numéro d'ordre est reprise « approuvée » (l'ordre est déjà émis et signé) ; sans numéro, « soumise ». « finalized » devient « finalisée », « paid » « payée ». Aucune date ni aucun auteur de validation n'est inventé (`date_validation_approximative`, historique « migré »).
- H-MISSION-10 : un congé « finalized » de Suivi RH est repris « approuvé », « created » et « modified » « soumis » : à confirmer par la RH.
- H-MISSION-11 : le montant en arabe des états suit la grammaire usuelle (dirhams au pluriel de 3 à 10, accord simple) ; à faire relire par l'établissement avant production, comme le rendu des polices Amiri et Noto Sans.
- H-MISSION-12 : séparation des tâches appliquée par le service : l'agent ne traite jamais sa propre mission ; l'approbation exige une autre personne que celle du niveau 1 ; le paiement une autre personne que celles qui ont approuvé et finalisé.
- H-MISSION-13 : un document officiel n'est téléchargeable que pour une mission approuvée (ordre) ou finalisée (états) ; chaque génération journalise l'empreinte SHA-256 du PDF (archivage). Le jeton de vérification du QR est un HMAC du type, de l'identifiant et du numéro ; la réponse publique ne contient aucune donnée personnelle.

## Chaîne de validation hiérarchique (demandes, congés, missions)
- H-CHAINE-1 (décidé) : toute demande ou mission qui exige l'accord des responsables est validée par **tous** les responsables de la hiérarchie, **dans l'ordre**, du plus proche au plus haut : un responsable par structure en remontant l'arbre, puis le délégué provincial, puis le coordinateur régional (la coordination du siège joue le rôle de direction centrale). Les niveaux sans responsable sont absents ; le demandeur est écarté de sa propre chaîne (sa demande part du niveau au-dessus) ; une même personne n'apparaît qu'une fois.
- H-CHAINE-2 : la chaîne est figée à la soumission (table `etape_validation`) ; un changement d'organigramme ultérieur ne la modifie pas. Seul le niveau actif peut décider ; les autres responsables de la chaîne peuvent consulter la demande en attendant leur tour. Un rejet à un niveau arrête la chaîne.
- H-CHAINE-3 : en cas d'absence, le suppléant déjà désigné décide à la place du responsable et la validation est tracée « par suppléance ». Aucun contournement par la RH n'est prévu.
- H-CHAINE-4 : la RH (demandes) ou la cellule des déplacements (approbation des missions) n'intervient qu'après le dernier niveau. Sans aucun responsable dans la hiérarchie, la demande va directement à la RH et la mission directement à l'approbation.
- H-CHAINE-5 : les demandes et missions reprises de Suivi RH n'ont pas de chaîne (traitées avant la reprise). Les demandes soumises avant cette évolution gardent leur responsable unique.

