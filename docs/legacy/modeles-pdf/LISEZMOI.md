# Modèles PDF des missions — Suivi RH (extraction)

| Fichier | Rôle | Appelé par |
|---|---|---|
| ordm_model.blade.php | Ordre de mission individuel (AR, RTL) + état d'indemnités (FR) + état kilométrique (voiture privée). Contient les fonctions numberToFrenchText / convertIntegerPart | DeplacementController::exportSinglePDF, DeplacementProgrammeController (2 exports) |
| ordm_grouped_model.blade.php | Ordres groupés (programme) | DeplacementProgrammeController (2 exports) |
| ordm_recap_model.blade.php | Récapitulatif simple | DeplacementController (liste), DeplacementProgrammeController |
| ordm_recap2_model.blade.php | Récapitulatif 2 | DeplacementProgrammeController |
| exportSinglePDF.mpdf-config.php.txt | Config mPDF : A4, RTL, police arabtype, en-tête logo 65 %, pied de page siège (adresse bilingue) | — |
| assets/logo.png, assets/arabic-typesetting.ttf | En-tête et police (licence de la police à vérifier avant réutilisation) | — |

Variables : show_order, show_summary, title, deplacements. show_order=true pour generated/created/modified/finalized ; show_summary=true seulement pour finalized.
Statut -> contenu : l'état d'indemnités n'apparaît qu'une fois la mission finalisée.

Limites constatées : montant en lettres en français uniquement (virgule + centimes lus comme un entier, pas de « dirhams/centimes ») ; pas de numéro de document, QR code ni signataire daté ; pied de page seulement pour le siège ; corps en largeur fixe 2480px.
À prévoir dans TAWASSOL : version bilingue AR/FR, montant en lettres AR et FR avec unité, numéro unique + QR de vérification, signataire valide à la date de la mission (delegation / signataire_autorisation), archivage horodaté.

Points à ne pas reprendre : fonctions du directeur codées en dur par personne_id (1461136, 1461016 depuis le 2025-10-20) -> à remplacer par la table des fonctions/signataires datée ; le QR code encode seulement id utilisateur + id mission + date/heure d'impression (aucune vérification possible) -> remplacer par un jeton de vérification public.
